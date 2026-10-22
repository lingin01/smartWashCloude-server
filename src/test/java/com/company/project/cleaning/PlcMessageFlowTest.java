package com.company.project.cleaning;

import com.company.project.config.PlcAccessProperties;
import com.company.project.config.PlcAccessProperties.*;
import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.*;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.service.command.CleaningCommandServiceImpl;
import com.company.project.service.cleaning.CleaningServiceImpl;
import com.company.project.service.lobby.CleaningLobbyServiceImpl;
import com.company.project.service.safety.CleaningSafetyServiceImpl;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PlcMessageFlowTest {
    static PlcAccessProperties configured() {
        var props=new PlcAccessProperties();props.setEnabled(true);
        var device=new Device();device.setId("plc-test");
        device.setHost("127.0.0.1");device.setEnabled(true);device.setWritesEnabled(true);
        device.setConfigurationVersion(0L);
        var stop=new Point();stop.setEnabled(true);stop.setAddress(7);stop.setFunctionCode(5);
        stop.setDataType(DataType.BOOLEAN);stop.setValue(BigDecimal.ONE);device.setEmergencyStop(stop);
        List<Point> parameters=new ArrayList<>();
        int address=100;
        for(String field:List.of("startTime","intervalSeconds","groupSpeedMetersPerMinute")){
            var p=new Point();p.setEnabled(true);p.setAddress(address++);p.setFunctionCode(6);
            p.setDataType(DataType.UINT16);p.setField(field);
            p.setEncoding(field.equals("startTime")?"HHMM":field.equals("intervalSeconds")?"SECONDS":"IDENTITY");
            parameters.add(p);
        }
        device.setParameters(parameters);props.setDevices(List.of(device));props.validate();
        return props;
    }
    static CleaningCommand command(String request,String type){
        Instant now=Instant.now();
        Map<String,Object> parameters=type.equals("emergency_stop")?Map.of("reason","test"):
            Map.of("settings",new CleaningSettingsBo(LocalTime.of(8,30),3600L,new BigDecimal("6")),"expectedVersion",0L);
        return new CleaningCommand(UUID.randomUUID().toString(),"plc-test",type,parameters,now,request,"tester",now.plusSeconds(30));
    }
    @Test void emergencyPriorityAndResultCapacityReservation(){
        var props=configured();var buffer=new PlcMessageBuffer();
        var settings=TestServices.prepare(props,command("cfg","update_cleaning_parameters"));
        var emergency=TestServices.prepare(props,command("stop","emergency_stop"));
        assertTrue(buffer.offer(settings,2));assertTrue(buffer.offer(emergency,2));
        assertEquals("stop",buffer.pollOperation().payload().requestId());
        assertFalse(buffer.offer(TestServices.prepare(props,command("stop2","emergency_stop")),2));
        var result=new PlcOperationResultBo(emergency.payload(),true,false,1,"OK");
        buffer.offerResult(result);buffer.offerResult(result);
        var next=buffer.peekResult();buffer.completeResult(next.messageId());
        assertNull(buffer.peekResult());
        assertTrue(buffer.offer(TestServices.prepare(props,command("stop2","emergency_stop")),2));
        assertEquals("stop2",buffer.pollOperation().payload().requestId());
        assertEquals("cfg",buffer.pollOperation().payload().requestId());
    }
    @Test void concurrentSettingsAreSerializedAndEmergencyStillAllowed() throws Exception{
        var buffer=new PlcMessageBuffer();
        var service=TestServices.commands(configured(),buffer);
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try{
            List<Future<CleaningCommandStatusBo>> results=new ArrayList<>();
            for(int i=0;i<16;i++){final int n=i;results.add(pool.submit(()->service.submit(command("cfg"+n,"update_cleaning_parameters"))));}
            int queued=0;
            for(var result:results)if(result.get(3,TimeUnit.SECONDS).state()==CleaningCommandStatusBo.State.QUEUED)queued++;
            assertEquals(1,queued);
            assertEquals(CleaningCommandStatusBo.State.QUEUED,service.submit(command("stop","emergency_stop")).state());
            var emergency=buffer.pollOperation().payload();service.acceptDeviceResult(new PlcOperationResultBo(emergency,true,false,1,"OK"));
            assertEquals(CleaningCommandStatusBo.State.SUCCEEDED,service.find("plc-test","stop").orElseThrow().state());
            var settings=buffer.pollOperation().payload();service.acceptDeviceResult(new PlcOperationResultBo(settings,true,false,3,"OK"));
            assertEquals(CleaningCommandStatusBo.State.UNCONFIRMED,service.find("plc-test",settings.requestId()).orElseThrow().state());
            assertEquals("SETTINGS_IN_PROGRESS",service.submit(command("cfg-next","update_cleaning_parameters")).code());
        }finally{pool.shutdownNow();}
    }
    @Test void parametersEncodeAllThreeValuesAndRejectUnknownAddress(){
        var props=configured();var message=TestServices.prepare(props,command("cfg","update_cleaning_parameters"));
        assertEquals(List.of(830),message.payload().writes().get(0).registers());
        assertEquals(List.of(3600),message.payload().writes().get(1).registers());
        assertEquals(List.of(6),message.payload().writes().get(2).registers());
        props.getDevices().get(0).getEmergencyStop().setAddress(null);
        assertThrows(IllegalArgumentException.class,props::validate);
    }
    @Test void partialWriteRemainsUnconfirmedButExplicitRejectionAllowsNextRequest(){
        var buffer=new PlcMessageBuffer();
        var service=TestServices.commands(configured(),buffer);
        service.submit(command("partial","update_cleaning_parameters"));
        var partial=buffer.pollOperation().payload();
        service.acceptDeviceResult(new PlcOperationResultBo(partial,false,true,1,"PLC_WRITE_UNCONFIRMED"));
        assertEquals(CleaningCommandStatusBo.State.UNCONFIRMED,service.find("plc-test","partial").orElseThrow().state());
        assertEquals("SETTINGS_IN_PROGRESS",service.submit(command("after-partial","update_cleaning_parameters")).code());
        var secondBuffer=new PlcMessageBuffer();
        var second=TestServices.commands(configured(),secondBuffer);
        second.submit(command("rejected","update_cleaning_parameters"));
        var rejected=secondBuffer.pollOperation().payload();
        second.acceptDeviceResult(new PlcOperationResultBo(rejected,false,false,0,"PLC_WRITE_REJECTED_2"));
        assertEquals(CleaningCommandStatusBo.State.FAILED,second.find("plc-test","rejected").orElseThrow().state());
        assertEquals(CleaningCommandStatusBo.State.QUEUED,second.submit(command("after-rejected","update_cleaning_parameters")).state());
    }
    @Test void disabledConfigurationCreatesNoWorkers(){
        var workers=org.mockito.Mockito.mock(com.company.project.info.thread.core.BackgroundWorkerRegistrar.class);
        var tasks=new com.company.project.presentation.plc.PlcDataIngress(new PlcAccessProperties(),workers,
                org.mockito.Mockito.mock(com.company.project.infrastructure.plc.PlcDeviceService.class),
                new PlcMessageBuffer(),org.mockito.Mockito.mock(com.company.project.presentation.plc.PlcDevicePresentation.class));
        tasks.start();
        org.mockito.Mockito.verifyNoInteractions(workers);
    }
    @Test void blockedIoDoesNotOccupyDispatchAndShutdownDoesNotHoldCenterLock() throws Exception {
        var center=new com.company.project.info.thread.core.ThreadCenter();
        var ioStarted=new CountDownLatch(1);var dispatched=new CountDownLatch(1);var release=new CountDownLatch(1);
        try {
            assertTrue(center.registerDedicatedPeriodic("test-io",()->{
                ioStarted.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
            },Duration.ofMillis(50)));
            assertTrue(ioStarted.await(2,TimeUnit.SECONDS));
            assertTrue(center.registerDedicatedPeriodic("test-dispatch",dispatched::countDown,Duration.ofMillis(50)));
            assertTrue(dispatched.await(2,TimeUnit.SECONDS));
            center.unregister("test-io");assertFalse(center.isRegistered("test-io"));
        }finally{release.countDown();center.shutdown();}
    }
    static PlcDeviceSnapshotBo.Point point(String field,int index,String winch,String rope,String sensor,String value){
        return new PlcDeviceSnapshotBo.Point(field+index,field,index,rope,sensor,winch,"kg",BigDecimal.ONE,new BigDecimal(value),true);
    }
    @Test void actualBusinessIdsMissingMetricsAndManualMode(){
        var points=List.of(point("manualMode",1,"","","","1"),point("running",1,"","","","0"),
            point("ropeTension",1,"W1","R1","S1","400"),point("ropeTension",2,"W1","R2","S2","600"),
            point("ropeTension",3,"W2","R3","S3","700"),point("winchSpeed",1,"W1","","","6"),
            point("winchTorque",1,"W1","","","123"),point("groupSpeed",1,"","","","5"));
        var cleaning=TestServices.cleaning(TestServices.commands(new PlcAccessProperties(),new PlcMessageBuffer()));
        cleaning.acceptDeviceSnapshot(new PlcDeviceSnapshotBo("plc-test",true,1,Instant.now(),points));
        var sample=cleaning.latest("plc-test").orElseThrow();
        assertEquals(SystemRealtimeStatusBo.Status.MAINTENANCE,sample.status().systemStatus());
        assertEquals(0,new BigDecimal("500").compareTo(sample.winches().get(0).tensionAverage()));
        assertNull(sample.winches().get(1).tensionAverage());
        assertEquals("R1",sample.ropes().get(0).ropeId());
        assertNull(sample.ringHeightMeters());assertFalse(sample.heightValid());
        assertNull(sample.winches().get(1).speed());
        assertEquals(new BigDecimal("123"),sample.winches().get(0).torqueKg());
        assertEquals(new BigDecimal("5"),sample.groupSpeedMetersPerMinute());
        assertEquals(3,sample.validTensionCount());
    }

    /** 用户入口到真实回环报文，再到命令结果和热缓冲，不需要 Spring／数据库。 */
    @Test void lobbyToLoopbackPlcAndBackToRealtimeBuffer() throws Exception {
        try (var server=new java.net.ServerSocket(0,2,java.net.InetAddress.getByName("127.0.0.1"))) {
            var props=configured();var definition=props.getDevices().get(0);
            definition.setPort(server.getLocalPort());
            List<Point> reads=new ArrayList<>();
            for (int i=0;i<2;i++) {
                var p=new Point();p.setEnabled(true);p.setId("mode"+i);p.setAddress(10+i);
                p.setFunctionCode(1);p.setDataType(DataType.BOOLEAN);
                p.setField(i==0?"manualMode":"running");reads.add(p);
            }
            for (int i=0;i<2;i++) {
                var p=new Point();p.setEnabled(true);p.setId("tension"+i);p.setAddress(40+i);
                p.setDataType(DataType.UINT16);p.setField("ropeTension");p.setIndex(i+1);
                p.setWinchId("W1");p.setRopeId("R"+(i+1));p.setSensorId("S"+(i+1));p.setUnit("kg");reads.add(p);
            }
            definition.setPoints(reads);props.validate();
            var buffer=new PlcMessageBuffer();
            var commands=TestServices.commands(props,buffer);
            var cleaning=TestServices.cleaning(commands);
            var lobby=TestServices.lobby(cleaning);
            var presentation=new com.company.project.presentation.plc.PlcDevicePresentation(lobby);
            var device=new com.company.project.infrastructure.plc.PlcDeviceService(props,buffer);
            device.initialize();
            ExecutorService peer=Executors.newSingleThreadExecutor();
            Future<?> reply=peer.submit(()->{
                try(var socket=server.accept()){
                    socket.setSoTimeout(3000);
                    for(int n=0;n<3;n++){
                        byte[] request=socket.getInputStream().readNBytes(12);
                        byte[] response=switch(request[7]){
                            case 5 -> request;
                            case 1 -> ModbusTcpProtocolTest.response(request,(byte)1,(byte)1,(byte)2);
                            case 3 -> ModbusTcpProtocolTest.response(request,(byte)3,(byte)4,(byte)1,(byte)144,(byte)2,(byte)88);
                            default -> throw new java.io.IOException("unexpected function");
                        };
                        socket.getOutputStream().write(response);socket.getOutputStream().flush();
                    }
                }catch(Exception e){throw new CompletionException(e);}
            });
            try {
                var context=new com.company.project.domain.param.cleaning.CleaningOperationContext(
                        "local-stop","plc-test","tester",Set.of("device:write"));
                var accepted=lobby.emergencyStop(new com.company.project.domain.param.cleaning.EmergencyStopParam(context,"模拟"));
                assertEquals(CleaningCommandStatusBo.State.QUEUED,accepted.state());
                device.runCycle();
                var result=buffer.peekResult();presentation.onWriteResult(result.payload());buffer.completeResult(result.messageId());
                for(var frame:buffer.takeSnapshots())presentation.onSnapshot(frame.payload());
                assertEquals(CleaningCommandStatusBo.State.SUCCEEDED,commands.find("plc-test","local-stop").orElseThrow().state());
                var actual=cleaning.latest("plc-test").orElseThrow();
                assertEquals(SystemRealtimeStatusBo.Status.RUNNING,actual.status().systemStatus());
                assertEquals(0,new BigDecimal("500").compareTo(actual.winches().get(0).tensionAverage()));
                reply.get(3,TimeUnit.SECONDS);
            } finally {device.shutdown();reply.cancel(true);peer.shutdownNow();}
        }
    }
}

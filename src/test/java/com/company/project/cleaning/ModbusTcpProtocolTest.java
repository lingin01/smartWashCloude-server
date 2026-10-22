package com.company.project.cleaning;

import com.company.project.config.PlcAccessProperties.*;
import com.company.project.domain.bo.plc.PlcOperationBo.Write;
import com.company.project.infrastructure.plc.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 仅使用回环模拟 PLC，不启动 Spring，不访问现场或数据库。 */
class ModbusTcpProtocolTest {
    static byte[] response(byte[] request, byte... pdu) {
        return ByteBuffer.allocate(7 + pdu.length).put(request, 0, 4)
                .putShort((short)(pdu.length + 1)).put(request[6]).put(pdu).array();
    }
    @Test void validatesTransactionUnitFunctionAndByteCount() throws Exception {
        byte[] request = ModbusTcpCodec.read(123, 1, 3, 40, 1);
        byte[] reply = response(request, (byte)3, (byte)2, (byte)0x12, (byte)0x34);
        assertArrayEquals(new byte[]{0x12,0x34}, ModbusTcpCodec.readData(request, reply));
        for (int offset : new int[]{0,2,4,6,7,8}) {
            byte[] bad = reply.clone(); bad[offset] ^= 1;
            assertThrows(IOException.class, () -> ModbusTcpCodec.readData(request,bad));
        }
        var error = assertThrows(ModbusTcpCodec.DeviceException.class,
                () -> ModbusTcpCodec.readData(request,response(request,(byte)0x83,(byte)2)));
        assertEquals(2,error.code());
    }
    @Test void writeSingleEchoAndMultiRangeHaveDifferentChecks() throws Exception {
        for (var write : List.of(new Write(5,7,List.of(),true), new Write(6,10,List.of(321),null))) {
            byte[] request = ModbusTcpCodec.write(9,1,write);
            ModbusTcpCodec.checkWrite(request,request.clone());
            byte[] bad=request.clone();bad[11]^=1;
            assertThrows(IOException.class,()->ModbusTcpCodec.checkWrite(request,bad));
        }
        byte[] request=ModbusTcpCodec.write(10,1,new Write(16,100,List.of(1,2),null));
        byte[] reply=response(request,(byte)16,(byte)0,(byte)100,(byte)0,(byte)2);
        assertNotEquals(request.length,reply.length);
        ModbusTcpCodec.checkWrite(request,reply);
        reply[11]=3;
        assertThrows(IOException.class,()->ModbusTcpCodec.checkWrite(request,reply));
    }
    @Test void signedUnsignedFloatOrderingScaleAndInvalidValue() {
        for (var type : List.of(DataType.INT16,DataType.UINT16,DataType.INT32,DataType.UINT32,DataType.FLOAT32,DataType.FLOAT64)) {
            for (var order : ByteOrder.values()) {
                Point p=new Point();p.setDataType(type);p.setByteOrder(order);
                p.setScale(new BigDecimal("0.5"));p.setOffset(BigDecimal.ONE);
                BigDecimal value=new BigDecimal(type==DataType.INT16||type==DataType.INT32?"-11":"101");
                List<Integer> words=ModbusTcpCodec.encode(p,value);
                ByteBuffer raw=ByteBuffer.allocate(words.size()*2);
                words.forEach(w->raw.putShort((short)(int)w));
                assertEquals(0,value.compareTo(ModbusTcpCodec.decode(p,raw.array(),0,false)));
            }
        }
        Point p=new Point();p.setDataType(DataType.UINT16);p.setInvalidValue(new BigDecimal("65535"));
        assertNull(ModbusTcpCodec.decode(p,new byte[]{-1,-1},0,false));
        assertEquals(BigDecimal.ONE,ModbusTcpCodec.decode(p,new byte[]{4},2,true));
        assertThrows(IllegalArgumentException.class,()->ModbusTcpCodec.read(1,1,3,65535,2));
    }
    @Test void socketAcceptsSplitFramesAndReusesConnection() throws Exception {
        try (ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));
             ModbusTcpClient client=new ModbusTcpClient("127.0.0.1",server.getLocalPort())) {
            ExecutorService pool=Executors.newSingleThreadExecutor();
            Future<?> peer=pool.submit(()->{
                try (Socket socket=server.accept()) {
                    socket.setSoTimeout(2000);
                    for(int i=0;i<2;i++){
                        byte[] request=socket.getInputStream().readNBytes(12);
                        if(request.length!=12)throw new IOException("request");
                        byte[] reply=response(request,(byte)3,(byte)2,(byte)0,(byte)(40+i));
                        socket.getOutputStream().write(reply,0,3);socket.getOutputStream().flush();
                        Thread.sleep(10);
                        socket.getOutputStream().write(reply,3,reply.length-3);socket.getOutputStream().flush();
                    }
                }catch(Exception e){throw new CompletionException(e);}
            });
            try {
                for(int i=0;i<2;i++){
                    byte[] request=ModbusTcpCodec.read(i,1,3,40,1);
                    byte[] reply=client.exchange(request,Duration.ofSeconds(2));
                    assertEquals(40+i,ModbusTcpCodec.u16(ModbusTcpCodec.readData(request,reply),0));
                }
                peer.get(3,TimeUnit.SECONDS);
            }finally{pool.shutdownNow();}
        }
    }
    @Test void socketTimeoutIsBounded() throws Exception {
        try(ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));
            ModbusTcpClient client=new ModbusTcpClient("127.0.0.1",server.getLocalPort())){
            ExecutorService pool=Executors.newSingleThreadExecutor();
            Future<?> peer=pool.submit(()->{try(Socket socket=server.accept()){
                socket.getInputStream().readNBytes(12);Thread.sleep(1000);
            }catch(Exception ignored){}});
            try{
                long start=System.nanoTime();
                assertThrows(SocketTimeoutException.class,()->client.exchange(
                        ModbusTcpCodec.read(1,1,3,40,1),Duration.ofMillis(100)));
                assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<900);
            }finally{peer.cancel(true);pool.shutdownNow();}
        }
    }
}

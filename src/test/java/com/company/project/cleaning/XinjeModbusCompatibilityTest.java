package com.company.project.cleaning;

import com.company.project.config.PlcAccessProperties;
import com.company.project.config.PlcAccessProperties.*;
import com.company.project.domain.bo.plc.PlcOperationBo;
import com.company.project.domain.bo.plc.PlcOperationBo.Write;
import com.company.project.infrastructure.plc.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 对照用户提供的信捷PDF报文及120寄存器限制；网络只访问本机模拟器。 */
class XinjeModbusCompatibilityTest {
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value.replace(" ", "")); }

    @Test void matchesDocumentReadRequestsAndReplies() throws Exception {
        for (int function : new int[]{1,2}) {
            String fc=String.format("%02X",function);
            byte[] request=ModbusTcpCodec.read(0x0102,1,function,125,13);
            assertArrayEquals(hex("0102 0000 0006 01 "+fc+" 007D 000D"),request);
            byte[] bits=ModbusTcpCodec.readData(request,hex("0102 0000 0005 01 "+fc+" 02 2503"));
            Point point=new Point();point.setDataType(DataType.BOOLEAN);
            int[] expected={1,0,1,0,0,1,0,0,1,1,0,0,0};
            for(int index=0;index<expected.length;index++)
                assertEquals(BigDecimal.valueOf(expected[index]),ModbusTcpCodec.decode(point,bits,index,true));
        }
        for(int function:new int[]{3,4}) {
            String fc=String.format("%02X",function);
            byte[] request=ModbusTcpCodec.read(0x0102,1,function,100,11);
            assertArrayEquals(hex("0102 0000 0006 01 "+fc+" 0064 000B"),request);
            byte[] values=hex("1122 3344 5566 7788 9900 1122 3344 5566 7788 9900 1234");
            byte[] reply=hex("0102 0000 0019 01 "+fc+" 16 "+HexFormat.of().formatHex(values));
            assertArrayEquals(values,ModbusTcpCodec.readData(request,reply));
        }
    }

    @Test void matchesDocumentWritesAndUsesActualMbapLength() throws Exception {
        byte[] coil=ModbusTcpCodec.write(0x0102,1,new Write(5,125,List.of(),false));
        assertArrayEquals(hex("0102 0000 0006 01 05 007D 0000"),coil);
        ModbusTcpCodec.checkWrite(coil,coil.clone());
        byte[] register=ModbusTcpCodec.write(0x0102,1,new Write(6,500,List.of(10),null));
        assertArrayEquals(hex("0102 0000 0006 01 06 01F4 000A"),register);
        ModbusTcpCodec.checkWrite(register,register.clone());
        byte[] multi=ModbusTcpCodec.write(0x0102,1,new Write(16,1,List.of(10,258),null));
        assertArrayEquals(hex("0102 0000 000B 01 10 0001 0002 04 000A 0102"),multi);
        // PDF第8页完整响应长度为0006；表格的000B是笔误。
        ModbusTcpCodec.checkWrite(multi,hex("0102 0000 0006 01 10 0001 0002"));
        assertThrows(IOException.class,()->ModbusTcpCodec.checkWrite(multi,hex("0102 0000 000B 01 10 0001 0002")));
    }

    @Test void decodesProvidedDintCalibrationValueWithConfiguredWordOrder() {
        Point point=new Point();point.setDataType(DataType.INT32);
        point.setByteOrder(ByteOrder.ABCD);
        assertEquals(0,new BigDecimal("1234567890").compareTo(ModbusTcpCodec.decode(point,hex("4996 02D2"),0,false)));
        point.setByteOrder(ByteOrder.CDAB);
        assertEquals(0,new BigDecimal("1234567890").compareTo(ModbusTcpCodec.decode(point,hex("02D2 4996"),0,false)));
        point.setDataType(DataType.INT16);point.setByteOrder(ByteOrder.ABCD);
        assertEquals(0,BigDecimal.valueOf(-1).compareTo(ModbusTcpCodec.decode(point,hex("FFFF"),0,false)));
    }

    @Test void splitsReadsAtDeviceLimitWithoutSplittingDint() throws Exception {
        assertReadBlocks(false,List.of(120,1));
        assertReadBlocks(true,List.of(119,2));
    }

    private static void assertReadBlocks(boolean lastIsDint,List<Integer> expectedCounts) throws Exception {
        try(ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"))) {
            Device definition=new Device();definition.setId("xinje-test");definition.setEnabled(true);
            definition.setHost("127.0.0.1");definition.setPort(server.getLocalPort());definition.setTimeoutMs(5000);
            List<Point> points=new ArrayList<>();
            int singles=lastIsDint?119:121;
            for(int index=0;index<singles;index++)points.add(point(index,DataType.INT16));
            if(lastIsDint)points.add(point(119,DataType.INT32));
            definition.setPoints(points);
            var properties=new PlcAccessProperties();properties.setDevices(List.of(definition));properties.validate();
            ExecutorService pool=Executors.newSingleThreadExecutor();
            Future<?> peer=pool.submit(()->{
                try(Socket socket=server.accept()) {
                    socket.setSoTimeout(5000);
                    int address=0;
                    for(int expected:expectedCounts) {
                        byte[] request=socket.getInputStream().readNBytes(12);
                        assertEquals(12,request.length);
                        assertEquals(address,ModbusTcpCodec.u16(request,8));
                        assertEquals(expected,ModbusTcpCodec.u16(request,10));
                        byte[] reply=ByteBuffer.allocate(9+expected*2).put(request,0,4)
                                .putShort((short)(3+expected*2)).put(request[6]).put((byte)3)
                                .put((byte)(expected*2)).array();
                        socket.getOutputStream().write(reply);socket.getOutputStream().flush();
                        address+=expected;
                    }
                } catch(IOException failure) { throw new CompletionException(failure); }
            });
            try(ModbusTcpDevice device=new ModbusTcpDevice(definition)) {
                var snapshot=device.poll(()->false);
                assertTrue(snapshot.online());
                assertEquals(points.size(),snapshot.points().size());
                assertTrue(snapshot.points().stream().allMatch(value->value.valid()));
                peer.get(6,TimeUnit.SECONDS);
            } finally { pool.shutdownNow(); }
        }
    }

    private static Point point(int address,DataType type) {
        var point=new Point();point.setEnabled(true);point.setId("d-"+address);
        point.setField("raw-"+address);point.setAddress(address);point.setDataType(type);
        return point;
    }

    @Test void rejectsOversizedWriteBeforeSendingAnySubrequest() {
        var definition=new Device();definition.setId("xinje-test");definition.setHost("127.0.0.1");
        definition.setPort(1);
        var operation=new PlcOperationBo("req","cmd","xinje-test","update_cleaning_parameters",
                Instant.now().plusSeconds(30),0,0L,null,List.of(
                        new Write(6,100,List.of(1),null),
                        new Write(16,200,Collections.nCopies(121,0),null)));
        try(var device=new ModbusTcpDevice(definition)) {
            var result=device.write(operation,()->false);
            assertEquals("PLC_WRITE_REGISTER_LIMIT_EXCEEDED",result.code());
            assertEquals(0,result.confirmedWrites());
            assertFalse(result.success());assertFalse(result.uncertain());
        }
    }
}

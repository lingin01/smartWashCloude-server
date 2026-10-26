package com.company.project.infrastructure.plc;

import com.company.project.config.PlcAccessProperties.*;
import com.company.project.domain.bo.plc.PlcOperationBo.Write;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/** Modbus TCP 编码及响应校验；不把协议异常原文直接返回用户。 */
public final class ModbusTcpCodec {
    private ModbusTcpCodec() { }
    public static final class DeviceException extends IOException {
        private final int code;
        public DeviceException(int code) { super("PLC_EXCEPTION_" + code); this.code = code; }
        public int code() { return code; }
    }

    public static byte[] read(int transaction, int unit, int function, int address, int quantity) {
        int max = function <= 2 ? 2000 : 125;
        if (function < 1 || function > 4 || quantity < 1 || quantity > max
                || address < 0 || address + quantity > 65536) throw new IllegalArgumentException("读取范围无效");
        return frame(transaction, unit, ByteBuffer.allocate(5).put((byte)function)
                .putShort((short)address).putShort((short)quantity).array());
    }

    public static byte[] write(int transaction, int unit, Write request) {
        int fc = request.functionCode(), address = request.address();
        if (address < 0 || address > 65535) throw new IllegalArgumentException("写地址无效");
        if (fc == 5) {
            if (request.coil() == null) throw new IllegalArgumentException("线圈值缺失");
            return frame(transaction, unit, ByteBuffer.allocate(5).put((byte)5)
                    .putShort((short)address).putShort((short)(request.coil() ? 0xff00 : 0)).array());
        }
        List<Integer> words = request.registers();
        if (words.isEmpty() || words.size() > 123 || address + words.size() > 65536
                || words.stream().anyMatch(v -> v == null || v < 0 || v > 65535)
                || (fc == 6 && words.size() != 1) || (fc != 6 && fc != 16))
            throw new IllegalArgumentException("写寄存器范围无效");
        ByteBuffer pdu = ByteBuffer.allocate(fc == 6 ? 5 : 6 + words.size() * 2);
        pdu.put((byte)fc).putShort((short)address);
        if (fc == 6) pdu.putShort((short)(int)words.get(0));
        else {
            pdu.putShort((short)words.size()).put((byte)(words.size() * 2));
            words.forEach(v -> pdu.putShort((short)(int)v));
        }
        return frame(transaction, unit, pdu.array());
    }

    private static byte[] frame(int transaction, int unit, byte[] pdu) {
        if (transaction < 0 || transaction > 65535 || unit < 0 || unit > 255)
            throw new IllegalArgumentException("事务号或设备地址无效");
        return ByteBuffer.allocate(7 + pdu.length).putShort((short)transaction).putShort((short)0)
                .putShort((short)(pdu.length + 1)).put((byte)unit).put(pdu).array();
    }

    public static int u16(byte[] value, int offset) {
        return ((value[offset] & 255) << 8) | (value[offset + 1] & 255);
    }

    public static void validateHeaders(byte[] request, byte[] response) throws IOException {
        if (request.length < 8 || response.length < 9 || response.length > 260
                || u16(response, 0) != u16(request, 0) || u16(response, 2) != 0
                || u16(response, 4) != response.length - 6 || response[6] != request[6])
            throw new IOException("MODBUS_HEADER_INVALID");
        int fc = request[7] & 255, actual = response[7] & 255;
        if (actual != fc && actual != (fc | 128)) throw new IOException("MODBUS_FUNCTION_INVALID");
    }

    private static void check(byte[] request, byte[] response) throws IOException {
        validateHeaders(request, response);
        if ((response[7] & 128) != 0) {
            if (response.length != 9) throw new IOException("MODBUS_EXCEPTION_LENGTH_INVALID");
            throw new DeviceException(response[8] & 255);
        }
    }

    public static byte[] readData(byte[] request, byte[] response) throws IOException {
        check(request, response);
        int expected = (request[7] & 255) <= 2 ? (u16(request, 10) + 7) / 8 : u16(request, 10) * 2;
        if ((response[8] & 255) != expected || response.length != expected + 9)
            throw new IOException("MODBUS_READ_LENGTH_INVALID");
        return Arrays.copyOfRange(response, 9, response.length);
    }

    public static void checkWrite(byte[] request, byte[] response) throws IOException {
        check(request, response);
        int fc = request[7] & 255;
        if (fc == 5 || fc == 6) {
            if (!Arrays.equals(request, response)) throw new IOException("MODBUS_WRITE_ECHO_INVALID");
        } else if (fc == 16) {
            // 多寄存器只回起始地址和数量，不能用完整报文相等判断。
            if (response.length != 12 || u16(response, 8) != u16(request, 8)
                    || u16(response, 10) != u16(request, 10)) throw new IOException("MODBUS_WRITE_RANGE_INVALID");
        } else throw new IOException("MODBUS_WRITE_FUNCTION_INVALID");
    }

    public static BigDecimal decode(Point point, byte[] bytes, int offset, boolean bits) {
        BigDecimal number = decodeRaw(point, bytes, offset, bits);
        return number == null ? null : bits ? number : number.multiply(point.getScale()).add(point.getOffset());
    }
    public static BigDecimal decodeRaw(Point point, byte[] bytes, int offset, boolean bits) {
        if (bits) return (bytes[offset / 8] & (1 << (offset % 8))) != 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        byte[] raw = Arrays.copyOfRange(bytes, offset * 2, offset * 2 + point.getDataType().words() * 2);
        ordered(raw, point.getByteOrder());
        ByteBuffer data = ByteBuffer.wrap(raw);
        double value = switch (point.getDataType()) {
            case BOOLEAN -> data.getShort() != 0 ? 1 : 0;
            case INT16 -> data.getShort();
            case UINT16 -> Short.toUnsignedInt(data.getShort());
            case INT32 -> data.getInt();
            case UINT32 -> Integer.toUnsignedLong(data.getInt());
            case FLOAT32 -> data.getFloat();
            case FLOAT64 -> data.getDouble();
        };
        if (!Double.isFinite(value)) return null;
        BigDecimal number = BigDecimal.valueOf(value);
        if (point.getInvalidValue() != null && number.compareTo(point.getInvalidValue()) == 0) return null;
        return number;
    }

    public static List<Integer> encode(Point point, BigDecimal businessValue) {
        BigDecimal raw = businessValue.subtract(point.getOffset()).divide(point.getScale(), MathContext.DECIMAL128);
        ByteBuffer bytes = ByteBuffer.allocate(point.getDataType().words() * 2);
        switch (point.getDataType()) {
            case BOOLEAN -> throw new IllegalArgumentException("线圈使用布尔编码");
            case INT16 -> bytes.putShort(raw.shortValueExact());
            case UINT16 -> bytes.putShort((short)unsigned(raw, 16).intValue());
            case INT32 -> bytes.putInt(raw.intValueExact());
            case UINT32 -> bytes.putInt(unsigned(raw, 32).intValue());
            case FLOAT32 -> {
                float value = raw.floatValue();
                if (!Float.isFinite(value)) throw new IllegalArgumentException("浮点写值超出范围");
                bytes.putFloat(value);
            }
            case FLOAT64 -> {
                double value = raw.doubleValue();
                if (!Double.isFinite(value)) throw new IllegalArgumentException("浮点写值超出范围");
                bytes.putDouble(value);
            }
        }
        byte[] value = bytes.array();
        ordered(value, point.getByteOrder());
        Integer[] result = new Integer[value.length / 2];
        for (int index = 0; index < result.length; index++) result[index] = u16(value, index * 2);
        return List.of(result);
    }

    private static BigInteger unsigned(BigDecimal value, int bits) {
        BigInteger number = value.toBigIntegerExact();
        if (number.signum() < 0 || number.bitLength() > bits) throw new IllegalArgumentException("无符号写值超出范围");
        return number;
    }

    private static void ordered(byte[] bytes, ByteOrder order) {
        if (order == ByteOrder.BADC) {
            for (int index = 0; index < bytes.length; index += 2) {
                byte v = bytes[index]; bytes[index] = bytes[index + 1]; bytes[index + 1] = v;
            }
        } else if (order == ByteOrder.DCBA) {
            for (int index = 0; index < bytes.length / 2; index++) {
                byte v = bytes[index]; bytes[index] = bytes[bytes.length - 1 - index]; bytes[bytes.length - 1 - index] = v;
            }
        } else if (order == ByteOrder.CDAB) {
            for (int index = 0; index < bytes.length / 2; index += 2) {
                byte a = bytes[index], b = bytes[index + 1];
                bytes[index] = bytes[bytes.length - index - 2]; bytes[index + 1] = bytes[bytes.length - index - 1];
                bytes[bytes.length - index - 2] = a; bytes[bytes.length - index - 1] = b;
            }
        }
    }
}

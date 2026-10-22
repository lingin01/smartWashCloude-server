package com.company.project.infrastructure.plc;

import com.company.project.config.PlcAccessProperties.*;
import com.company.project.dto.plc.PlcReadSnapshotDto;
import com.company.project.domain.bo.plc.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** 单 PLC 网络对象：读写共用一条会话，全部由专用 I/O 工作者调用。 */
public final class ModbusTcpDevice implements AutoCloseable {
    private record Block(int function, int address, int count, List<Point> points) { }
    private final Device definition;
    private final ModbusTcpClient client;
    private final List<Block> blocks;
    private int transaction;
    private long sequence = System.currentTimeMillis() * 1000L;
    private int failures;
    private long nextPollAt;
    private volatile boolean closed;

    public ModbusTcpDevice(Device definition) {
        this.definition = definition;
        client = new ModbusTcpClient(definition.getHost(), definition.getPort());
        blocks = buildReadBlocks(definition.getPoints());
    }
    public String id() { return definition.getId(); }
    public boolean due(long now) { return now >= nextPollAt; }

    public PlcReadSnapshotDto poll(BooleanSupplier emergencyWaiting) {
        if (closed) return null;
        long deadline = System.nanoTime() + Duration.ofMillis(definition.getTimeoutMs()).toNanos();
        List<PlcReadSnapshotDto.PointValue> values = new ArrayList<>();
        boolean online = true;
        for (Block block : blocks) {
            if (closed || Thread.currentThread().isInterrupted()) return null;
            if (emergencyWaiting.getAsBoolean()) {
                // 给急停让路不等于通信失败，不发布一帧伪离线数据。
                nextPollAt = System.nanoTime() + Duration.ofMillis(50).toNanos();
                return null;
            }
            if (System.nanoTime() >= deadline) {
                online = false;
                appendInvalid(values, block.points());
                continue;
            }
            try {
                byte[] request = ModbusTcpCodec.read(nextTransaction(), definition.getUnitId(),
                        block.function(), block.address(), block.count());
                byte[] data = ModbusTcpCodec.readData(request,
                        client.exchange(request, Duration.ofNanos(Math.max(1, deadline - System.nanoTime()))));
                for (Point point : block.points()) {
                    java.math.BigDecimal number = ModbusTcpCodec.decode(point, data,
                            point.getAddress() - block.address(), block.function() <= 2);
                    values.add(value(point, number, number != null));
                }
            } catch (ModbusTcpCodec.DeviceException rejected) {
                // 设备有效返回异常，当前测点无效；不伪造数值，也不把点位错误当作硬件故障码。
                appendInvalid(values, block.points());
            } catch (IOException | RuntimeException unavailable) {
                online = false;
                client.close();
                appendInvalid(values, block.points());
            }
        }
        // 没有读点时不制造“设备在线”的全零快照。
        if (blocks.isEmpty()) online = false;
        failures = online ? 0 : Math.min(failures + 1, 4);
        nextPollAt = System.nanoTime() + Duration.ofMillis(online ? definition.getPollIntervalMs()
                : 1000L << Math.max(0, failures - 1)).toNanos();
        return new PlcReadSnapshotDto(id(), online, ++sequence, Instant.now(), values);
    }

    public PlcOperationResultBo write(PlcOperationBo operation, BooleanSupplier emergencyWaiting) {
        int confirmed = 0;
        if (!Instant.now().isBefore(operation.expiresAt())) return result(operation, false, false, 0, "COMMAND_EXPIRED");
        for (PlcOperationBo.Write write : operation.writes()) {
            if (closed || Thread.currentThread().isInterrupted())
                return result(operation, false, confirmed > 0, confirmed, "SERVICE_STOPPED");
            if (!Instant.now().isBefore(operation.expiresAt()))
                return result(operation, false, confirmed > 0, confirmed, "COMMAND_EXPIRED");
            if (!operation.emergency() && emergencyWaiting.getAsBoolean())
                return result(operation, false, confirmed > 0, confirmed, "INTERRUPTED_BY_EMERGENCY");
            try {
                byte[] request = ModbusTcpCodec.write(nextTransaction(), definition.getUnitId(), write);
                long remaining = Math.max(1, Duration.between(Instant.now(), operation.expiresAt()).toMillis());
                byte[] response = client.exchange(request, Duration.ofMillis(Math.min(definition.getTimeoutMs(), remaining)));
                ModbusTcpCodec.checkWrite(request, response);
                confirmed++;
            } catch (ModbusTcpCodec.DeviceException rejected) {
                return result(operation, false, confirmed > 0, confirmed, "PLC_WRITE_REJECTED_" + rejected.code());
            } catch (IOException | RuntimeException unknown) {
                client.close();
                // 超时／断线可能发生在 PLC 收到请求之后，不擅自重发或声称未执行。
                return result(operation, false, true, confirmed, "PLC_WRITE_UNCONFIRMED");
            }
        }
        return result(operation, true, false, confirmed, "PLC_WRITE_CONFIRMED");
    }

    private static PlcOperationResultBo result(PlcOperationBo op, boolean success, boolean uncertain, int count, String code) {
        return new PlcOperationResultBo(op, success, uncertain, count, code);
    }
    private int nextTransaction() { transaction = (transaction + 1) & 65535; return transaction; }
    private static PlcReadSnapshotDto.PointValue value(Point p, java.math.BigDecimal value, boolean valid) {
        return new PlcReadSnapshotDto.PointValue(p.getId(), p.getField(), p.getIndex(), p.getRopeId(),
                p.getSensorId(), p.getWinchId(), p.getUnit(), p.getActiveValue(), value, valid);
    }
    private static void appendInvalid(List<PlcReadSnapshotDto.PointValue> values, List<Point> points) {
        points.forEach(p -> values.add(value(p, null, false)));
    }
    private static List<Block> buildReadBlocks(List<Point> points) {
        List<Point> sorted = points.stream().filter(Point::isEnabled)
                .sorted(Comparator.comparingInt(Point::getFunctionCode).thenComparingInt(Point::getAddress)).toList();
        List<Block> result = new ArrayList<>();
        for (Point p : sorted) {
            int width = p.getFunctionCode() <= 2 ? 1 : p.getDataType().words();
            if (!result.isEmpty()) {
                Block last = result.get(result.size() - 1);
                int end = Math.max(last.address() + last.count(), p.getAddress() + width);
                int limit = p.getFunctionCode() <= 2 ? 2000 : 125;
                if (last.function() == p.getFunctionCode() && p.getAddress() <= last.address() + last.count()
                        && end - last.address() <= limit) {
                    List<Point> merged = new ArrayList<>(last.points()); merged.add(p);
                    result.set(result.size() - 1, new Block(last.function(), last.address(), end - last.address(), List.copyOf(merged)));
                    continue;
                }
            }
            result.add(new Block(p.getFunctionCode(), p.getAddress(), width, List.of(p)));
        }
        return List.copyOf(result);
    }
    @Override public void close() { closed = true; client.close(); }
}

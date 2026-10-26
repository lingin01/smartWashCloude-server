package com.company.project.info.buffer.plc;

import com.company.project.domain.bo.plc.*;
import com.company.project.domain.message.Message;
import com.company.project.dto.plc.PlcReadSnapshotDto;
import org.springframework.stereotype.Component;
import java.util.*;

/** 有界消息中心。结果占用预留容量直到分发成功，网络线程不等待消费者腾位。 */
@Component
public class PlcMessageBuffer {
    private record Queued(Message<PlcOperationBo> message, long order) { }
    private final PriorityQueue<Queued> outgoing = new PriorityQueue<>(
            Comparator.<Queued>comparingInt(q -> q.message().priority()).reversed().thenComparingLong(Queued::order));
    private final Set<String> reserved = new HashSet<>();
    private final Set<String> resultReady = new HashSet<>();
    private final Set<String> auditRequired = new HashSet<>();
    private final Set<String> auditCommitted = new HashSet<>();
    private final ArrayDeque<Message<PlcOperationResultBo>> results = new ArrayDeque<>();
    private final Map<String, Message<PlcReadSnapshotDto>> snapshots = new LinkedHashMap<>();
    private final ArrayDeque<Message<PlcReadSnapshotDto>> orderedSnapshots = new ArrayDeque<>();
    private long order;
    private boolean accepting = true;

    public synchronized boolean offer(Message<PlcOperationBo> message, int capacity) {
        if (!accepting || reserved.size() >= capacity || !reserved.add(message.payload().requestId())) return false;
        outgoing.add(new Queued(message, ++order));
        return true;
    }
    public synchronized Message<PlcOperationBo> pollOperation() {
        Queued next = outgoing.poll();
        return next == null ? null : next.message();
    }
    public synchronized boolean emergencyWaiting(String deviceId) {
        return outgoing.stream().anyMatch(q -> q.message().payload().emergency()
                && q.message().deviceId().equals(deviceId));
    }
    public synchronized void offerResult(PlcOperationResultBo result) {
        if (!reserved.contains(result.operation().requestId())) throw new IllegalStateException("无预留位置的写结果");
        if (!resultReady.add(result.operation().requestId())) return;
        results.addLast(Message.create(result.operation().requestId(), result.operation().deviceId(),
                Message.Type.PLC_WRITE_RESULT, result.operation().emergency() ? 100 : 10, null, result));
    }
    public synchronized Message<PlcOperationResultBo> peekResult() { return results.peekFirst(); }
    public synchronized boolean completeResult(String messageId) {
        Message<PlcOperationResultBo> current = results.peekFirst();
        if (current == null || !current.messageId().equals(messageId)) throw new IllegalStateException("写结果消费关联错误");
        String requestId = current.payload().operation().requestId();
        if(auditRequired.contains(requestId)&&!auditCommitted.contains(requestId))return false;
        results.removeFirst();
        reserved.remove(requestId);
        resultReady.remove(requestId);
        auditRequired.remove(requestId);auditCommitted.remove(requestId);
        return true;
    }
    public synchronized void requireAudit(String requestId) { auditRequired.add(requestId); }
    public synchronized void auditCommitted(String requestId) { if(reserved.contains(requestId))auditCommitted.add(requestId); }
    public synchronized boolean isAuditCommitted(String requestId) { return auditCommitted.contains(requestId); }
    /** 实时最新帧允许合并；不能用此通道代替后续有序轮次／历史事件队列。 */
    public synchronized void offerSnapshot(PlcReadSnapshotDto snapshot) {
        if (orderedSnapshots.size() >= 4096)
            throw new IllegalStateException("PLC有序采集队列已满，未覆盖历史信号");
        if (!snapshots.containsKey(snapshot.deviceId()) && snapshots.size() >= 64)
            throw new IllegalStateException("设备快照来源超出配置容量");
        snapshots.put(snapshot.deviceId(), Message.create(null, snapshot.deviceId(),
                Message.Type.DEVICE_SNAPSHOT, 0, null, snapshot));
        orderedSnapshots.addLast(Message.create(null, snapshot.deviceId(),
                Message.Type.DEVICE_SNAPSHOT, 0, null, snapshot));
    }
    public synchronized boolean hasSnapshotCapacity() { return orderedSnapshots.size() < 4096; }
    public synchronized Message<PlcReadSnapshotDto> peekOrderedSnapshot() { return orderedSnapshots.peekFirst(); }
    public synchronized void completeSnapshot(String messageId) {
        var next = orderedSnapshots.peekFirst();
        if (next == null || !next.messageId().equals(messageId)) throw new IllegalStateException("采集确认顺序不一致");
        orderedSnapshots.removeFirst();
        var latest = snapshots.get(next.deviceId());
        if (latest != null && latest.payload().sequence() <= next.payload().sequence()) snapshots.remove(next.deviceId());
    }
    public synchronized List<Message<PlcReadSnapshotDto>> takeSnapshots() {
        List<Message<PlcReadSnapshotDto>> values = List.copyOf(snapshots.values());
        snapshots.clear();
        return values;
    }
    public synchronized void stopAccepting() { accepting = false; }
}

package com.company.project.info.buffer.cleaning;

import com.company.project.domain.bo.telemetry.RunHistoryBo.Write;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;

/** 顺序持久待写队列。只有数据库提交成功才能确认删除；损坏文件不跳过。 */
@Component
@ConditionalOnProperty(prefix="cleaning.history", name="runtime-enabled", havingValue="true")
public class DurableHistoryInbox implements AutoCloseable {
    public record Pending(Path path, Write value) { }
    private final Path directory;
    private final ObjectMapper mapper;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private long next;
    public DurableHistoryInbox(@Value("${cleaning.history.spool-directory:var/history-inbox}") String directory,
                               ObjectMapper mapper) throws IOException {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.mapper = mapper;
        Files.createDirectories(this.directory);
        lockChannel = FileChannel.open(this.directory.resolve(".lock"), CREATE, WRITE);
        lock = lockChannel.tryLock();
        if (lock == null) { lockChannel.close(); throw new IOException("历史待写目录已被其他后端占用"); }
        try (var files = Files.list(this.directory)) {
            next = files.map(p -> p.getFileName().toString()).filter(n -> n.matches("[0-9]{20}\\.json"))
                    .mapToLong(n -> Long.parseLong(n.substring(0,20))).max().orElse(0);
        }
    }
    public synchronized void append(Write value) {
        try {
            Path target = directory.resolve(String.format(Locale.ROOT, "%020d.json", ++next));
            Path temporary = directory.resolve(target.getFileName() + ".tmp");
            byte[] data = mapper.writeValueAsBytes(value);
            try (var channel = FileChannel.open(temporary, CREATE_NEW, WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, target); }
        } catch (IOException failure) { throw new IllegalStateException("历史数据暂存失败，采集消息未确认", failure); }
    }
    public synchronized Optional<Pending> peek() {
        try (var files = Files.list(directory)) {
            var first = files.filter(p -> p.getFileName().toString().matches("[0-9]{20}\\.json"))
                    .min(Comparator.comparing(p -> p.getFileName().toString()));
            if (first.isEmpty()) return Optional.empty();
            return Optional.of(new Pending(first.get(), mapper.readValue(first.get().toFile(), Write.class)));
        } catch (IOException failure) { throw new IllegalStateException("历史待写文件无法读取，保留原文件", failure); }
    }
    public synchronized void acknowledge(Pending pending) {
        if (!pending.path().getParent().equals(directory)) throw new IllegalArgumentException("待写文件不属于当前队列");
        try { Files.delete(pending.path()); }
        catch (IOException failure) { throw new IllegalStateException("历史已提交，待写文件稍后幂等重试", failure); }
    }
    public synchronized long pendingCount() {
        try (var files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json")).count();
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    @Override @PreDestroy public void close() throws IOException { if(lock.isValid())lock.release(); lockChannel.close(); }
}

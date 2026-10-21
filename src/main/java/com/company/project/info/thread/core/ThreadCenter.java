package com.company.project.info.thread.core;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** 原线程中心增加专用通道；业务服务仍不自行 new Thread，停止等待不持中心锁。 */
@Component
public class ThreadCenter implements BackgroundWorkerRegistrar {
    private static final Logger log = LoggerFactory.getLogger(ThreadCenter.class);
    private final ScheduledExecutorService shared = Executors.newScheduledThreadPool(2, task -> thread(task, "cleaning-shared"));
    private final Map<String, ScheduledFuture<?>> workers = new HashMap<>();
    private final Map<String, ScheduledExecutorService> dedicated = new HashMap<>();
    private boolean stopping;
    private static Thread thread(Runnable task, String name) {
        Thread thread = new Thread(task, name); thread.setDaemon(true); return thread;
    }
    @Override public synchronized boolean registerPeriodic(String name, Runnable cycle, Duration interval) {
        return register(name, cycle, interval, shared);
    }
    @Override public synchronized boolean registerDedicatedPeriodic(String name, Runnable cycle, Duration interval) {
        if (stopping || workers.containsKey(name)) return false;
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> thread(task, name));
        try {
            if (!register(name, cycle, interval, executor)) { executor.shutdown(); return false; }
            dedicated.put(name, executor); return true;
        } catch (RuntimeException failure) { executor.shutdownNow(); throw failure; }
    }
    private boolean register(String name, Runnable cycle, Duration interval, ScheduledExecutorService executor) {
        if (stopping || workers.containsKey(name)) return false;
        if (interval.toMillis() <= 0) throw new IllegalArgumentException("任务周期必须大于零");
        var future = executor.scheduleWithFixedDelay(() -> {
            try { cycle.run(); } catch (Exception failure) { log.error("后台任务执行失败: {}", name, failure); }
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        workers.put(name, future); return true;
    }
    @Override public void unregister(String name) {
        ScheduledFuture<?> future;
        ScheduledExecutorService executor;
        synchronized (this) {
            future = workers.remove(name);
            executor = dedicated.remove(name);
        }
        if (future != null) future.cancel(executor != null);
        if (executor != null) {
            executor.shutdownNow();
            if (!Thread.currentThread().getName().equals(name)) try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) log.warn("任务尚未退出: {}", name);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }
    public synchronized boolean isRegistered(String name) { return workers.containsKey(name); }
    @PreDestroy public void shutdown() {
        List<ScheduledExecutorService> owned;
        synchronized (this) {
            stopping = true;
            owned = new ArrayList<>(dedicated.values()); owned.add(shared);
            workers.values().forEach(future -> future.cancel(true)); workers.clear(); dedicated.clear();
        }
        owned.forEach(ScheduledExecutorService::shutdownNow);
        // await 放在锁外，避免工作者退出时需要登记中心形成交叉等待。
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        for (var executor : owned) try {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
    }
}

package com.company.project.info.thread.core;

import com.company.project.service.CloudStatisticsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 物理线程仍由 ThreadCenter 管理；本类只声明历史批次落库周期。 */
@Component
@ConditionalOnProperty(prefix = "cleaning.history", name = "persistence-enabled", havingValue = "true")
public class TelemetryHistoryPersistenceTask {
    private static final String WORKER = "cleaning-history-persistence";
    private final BackgroundWorkerRegistrar workers;
    private final CloudStatisticsService statistics;
    private final long intervalMs;

    public TelemetryHistoryPersistenceTask(BackgroundWorkerRegistrar workers,
                                           CloudStatisticsService statistics,
                                           @Value("${cleaning.history.persistence-interval-ms:2000}") long intervalMs) {
        this.workers = workers;
        this.statistics = statistics;
        this.intervalMs = intervalMs;
    }

    @PostConstruct
    public void start() {
        if (!workers.registerPeriodic(WORKER, statistics::runRealtimePersistenceCycle,
                Duration.ofMillis(intervalMs))) {
            throw new IllegalStateException("历史批次落库任务重复注册");
        }
    }

    @PreDestroy
    public void stop() {
        workers.unregister(WORKER);
    }
}

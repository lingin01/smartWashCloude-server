package com.company.project.service.tasks;

import com.company.project.service.CleaningStatisticsService;
import com.company.project.info.thread.core.BackgroundWorkerRegistrar;
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
    private final CleaningStatisticsService history;
    private final long intervalMs;

    public TelemetryHistoryPersistenceTask(BackgroundWorkerRegistrar workers,
                                           CleaningStatisticsService history,
                                           @Value("${cleaning.history.persistence-interval-ms:2000}") long intervalMs) {
        this.workers = workers;
        this.history = history;
        this.intervalMs = intervalMs;
    }

    @PostConstruct
    public void start() {
        if (!workers.registerPeriodic(WORKER, history::runPersistenceCycle,
                Duration.ofMillis(intervalMs))) {
            throw new IllegalStateException("历史批次落库任务重复注册");
        }
    }

    @PreDestroy
    public void stop() {
        workers.unregister(WORKER);
    }
}

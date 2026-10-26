package com.company.project.service.tasks;
import com.company.project.info.thread.core.BackgroundWorkerRegistrar;
import com.company.project.service.CleaningStatisticsService;
import jakarta.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix="cleaning.history",name="runtime-enabled",havingValue="true")
public class RuntimeHistoryTasks {
    private final BackgroundWorkerRegistrar workers;
    private final CleaningStatisticsService statistics;
    public RuntimeHistoryTasks(BackgroundWorkerRegistrar workers,CleaningStatisticsService statistics) {
        this.workers=workers;this.statistics=statistics;
    }
    @PostConstruct public void start() {
        if(!workers.registerDedicatedPeriodic("cleaning-runtime-persistence",statistics::runPersistenceCycle,Duration.ofSeconds(1)))
            throw new IllegalStateException("正式历史保存任务重复");
        try {
            if(!workers.registerDedicatedPeriodic("cleaning-period-statistics",statistics::runPeriodCycle,Duration.ofSeconds(2)))
                throw new IllegalStateException("周期统计任务重复");
            if(!workers.registerDedicatedPeriodic("cleaning-export-jobs",statistics::runExportCycle,Duration.ofSeconds(2)))
                throw new IllegalStateException("导出任务重复");
        } catch(RuntimeException failure) {
            workers.unregister("cleaning-export-jobs");workers.unregister("cleaning-period-statistics");
            workers.unregister("cleaning-runtime-persistence");throw failure;
        }
    }
    @PreDestroy public void stop() {
        workers.unregister("cleaning-export-jobs");
        workers.unregister("cleaning-period-statistics");workers.unregister("cleaning-runtime-persistence");
        statistics.runPersistenceCycle();
    }
}

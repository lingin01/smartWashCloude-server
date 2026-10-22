package com.company.project.service.tasks;
import com.company.project.service.CleaningCommandService;
import com.company.project.info.thread.core.BackgroundWorkerRegistrar;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** 回执超时周期统一登记在线程中心，命令服务不创建线程。 */
@Component
public class CleaningCommandTimeoutTask {
    private final BackgroundWorkerRegistrar workers;
    private final CleaningCommandService commands;
    public CleaningCommandTimeoutTask(BackgroundWorkerRegistrar workers, CleaningCommandService commands) {
        this.workers = workers; this.commands = commands;
    }
    @PostConstruct public void start() {
        if (!workers.registerPeriodic("cleaning-command-timeout", commands::runTimeoutCycle, Duration.ofSeconds(1)))
            throw new IllegalStateException("命令超时任务重复注册");
    }
    @PreDestroy public void stop() { workers.unregister("cleaning-command-timeout"); }
}

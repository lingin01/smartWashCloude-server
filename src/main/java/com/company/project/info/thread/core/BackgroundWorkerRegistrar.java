package com.company.project.info.thread.core;

import java.time.Duration;

/** 业务只登记周期；阻塞技术任务必须由统一中心提供专用线程，不占共享任务池。 */
public interface BackgroundWorkerRegistrar {
    boolean registerPeriodic(String workerName, Runnable businessCycle, Duration interval);
    boolean registerDedicatedPeriodic(String workerName, Runnable businessCycle, Duration interval);
    void unregister(String workerName);
}

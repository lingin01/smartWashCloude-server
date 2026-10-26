package com.company.project.service;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.statistics.CleaningStatisticsBo;
import com.company.project.domain.bo.telemetry.TelemetryBatchBo;
/** 统计服务聚合实际样本分析和既有历史保存，不声明未实现的轮次/周期能力。 */
public interface CleaningStatisticsService {
    void acceptSample(TelemetrySample sample);
    CleaningStatisticsBo statistics(String deviceId);
    void acceptHistoryBatch(TelemetryBatchBo batch);
    void runPersistenceCycle();
    default boolean runtimeEnabled() { return false; }
    default void acceptRuntimeWrite(com.company.project.domain.bo.telemetry.RunHistoryBo.Write write) { }
    default java.util.Optional<TelemetrySample> restoredState(String deviceId) { return java.util.Optional.empty(); }
    default java.util.Map<String,Object> runtimeConfiguration(String deviceId) { return java.util.Map.of(); }
    default void runPeriodCycle() { }
    default void runExportCycle() { }
    default com.company.project.domain.bo.ExportAccess exports(com.company.project.domain.param.cleaning.CleaningOperationContext context) {
        throw new com.company.project.domain.exception.PageApiException(503,"EXPORT_NOT_READY","正式导出尚未启用");
    }
    default java.util.List<com.company.project.domain.bo.telemetry.RunHistoryBo.Event> activeEvents(String deviceId) { return java.util.List.of(); }
    default com.company.project.domain.bo.telemetry.HistoryReader history(String deviceId) {
        throw new com.company.project.domain.exception.PageApiException(503,"HISTORY_NOT_READY","正式历史保存尚未启用");
    }
    default com.company.project.domain.bo.StoredSettingsBo storedSettings(String deviceId) { return null; }
    default com.company.project.domain.bo.StoredThresholdsBo thresholds(String deviceId,String winchId) { return null; }
    default com.company.project.domain.bo.StoredThresholdsBo saveThresholds(com.company.project.domain.bo.CleaningCommand command,
            String winchId,long version,java.util.List<com.company.project.domain.bo.StoredThresholdsBo.Value> values) {
        throw new com.company.project.domain.exception.PageApiException(503,"CONFIGURATION_NOT_READY","阈值存储尚未启用");
    }
    default com.company.project.domain.bo.StoredSettingsBo saveSettings(com.company.project.domain.bo.CleaningCommand command,long version,
            com.company.project.domain.bo.CleaningSettingsBo value) {
        throw new com.company.project.domain.exception.PageApiException(503,"CONFIGURATION_NOT_READY","配置存储尚未启用");
    }
}

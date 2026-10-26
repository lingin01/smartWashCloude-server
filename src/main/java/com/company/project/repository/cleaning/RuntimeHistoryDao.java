package com.company.project.repository.cleaning;

import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.CleaningCommand;
import com.company.project.domain.bo.CleaningCommandStatusBo;
import com.company.project.domain.bo.CleaningSettingsBo;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import java.time.Instant;
import java.util.*;

/** 正式运行、统计和操作持久化端口；不向数据层传HTTP对象。 */
public interface RuntimeHistoryDao {
    record PeriodWork(String systemId, String periodType, Instant start, Instant end, long version) { }
    void save(Write write);
    void prepare();
    void recoverInterruptedRuns(Instant recoveredAt);
    Optional<TelemetrySample> lastState(String deviceId);
    List<Event> activeEvents(String deviceId);
    Page<Run> runs(String deviceId, Instant from, Instant to, String result, int offset, int limit);
    Optional<Run> run(String deviceId, String recordCode);
    List<SavedFrame> frames(String deviceId, String recordCode, int offset, int limit);
    List<SavedFrame> framesByTime(String deviceId,Instant from,Instant to,String result,int offset,int limit);
    Optional<SavedFrame> frame(String deviceId, String recordCode, String sampleKey);
    List<java.time.LocalDate> dates(String deviceId, int offset, int limit);
    Page<Event> events(String deviceId, Instant from, Instant to, Boolean alarm, Integer winchSlot,
                       String recordCode, int offset, int limit);
    Page<Event> events(String deviceId, Instant from, Instant to, Boolean alarm, Integer winchSlot,
                       String recordCode, String direction, int offset, int limit);
    Optional<Event> event(String deviceId, String eventCode);
    List<PeriodWork> pendingPeriods(int limit);
    List<Run> periodRuns(String systemId, Instant from, Instant to);
    List<Run> measuredRuns(String systemId, Instant from, Instant to);
    Map<String,Integer> winches(String systemId);
    java.util.stream.Stream<SavedFrame> periodFrames(String systemId, Instant from, Instant to);
    List<Period> dailyPeriods(String systemId, Instant from, Instant to);
    List<Height> dailyHeights(String systemId, Instant from, Instant to);
    void storePeriod(PeriodWork work, List<Period> periods, List<Height> heights);
    List<Period> periods(String deviceId, String type, Instant from, Instant to, String scopeId);
    List<Height> heights(String deviceId, String type, Instant start, String scopeId);
    com.company.project.domain.bo.StoredSettingsBo settings(String deviceId);
    com.company.project.domain.bo.StoredSettingsBo saveSettings(CleaningCommand command, long expectedVersion, CleaningSettingsBo settings);
    List<String> deviceIds();
    String systemId(String deviceId);
    com.company.project.domain.bo.StoredThresholdsBo thresholds(String deviceId,String winchId);
    com.company.project.domain.bo.StoredThresholdsBo saveThresholds(CleaningCommand command,String winchId,long version,
            List<com.company.project.domain.bo.StoredThresholdsBo.Value> values);
    Map<String,Object> configuration(String deviceId);
    void saveConfiguration(String deviceId, Map<String,Object> configuration);
    void saveOperation(CleaningCommand command, CleaningCommandStatusBo state);
    Optional<CleaningCommandStatusBo> operation(String deviceId, String requestId, String commandCode);
    com.company.project.domain.bo.ExportTaskBo createExport(com.company.project.domain.bo.ExportTaskBo task);
    Page<com.company.project.domain.bo.ExportTaskBo> exports(String deviceId,String operator,String type,String state,int offset,int limit);
    Optional<com.company.project.domain.bo.ExportTaskBo> export(String deviceId,String operator,String code);
    Optional<com.company.project.domain.bo.ExportTaskBo> claimExport(String storeId);
    void updateExport(com.company.project.domain.bo.ExportTaskBo task);
    void recoverExports(String storeId,Instant now);
    void exportSnapshot(Runnable action);
    Optional<List<Height>> runHeightCache(String deviceId,String recordCode);
    void cacheRunHeights(String deviceId,String recordCode,long version,List<Height> values);
    boolean statisticsReady(String deviceId,String type,Instant from,Instant to);
}

package com.company.project.domain.bo.telemetry;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import com.company.project.domain.bo.telemetry.RunHistoryBo.Period;
import java.time.*;
import java.util.*;

/** 已经绑定系统权限的只读历史视图，不暴露DAO或任意SQL。 */
public interface HistoryReader {
    Page<Run> runs(Instant from,Instant to,String result,int offset,int limit);
    Optional<Run> run(String recordCode);
    List<SavedFrame> frames(String recordCode,int offset,int limit);
    Optional<SavedFrame> frame(String recordCode,String sampleKey);
    List<LocalDate> dates(int offset,int limit);
    Page<Event> events(Instant from,Instant to,Boolean alarm,Integer winchSlot,String recordCode,int offset,int limit);
    default Page<Event> events(Instant from,Instant to,Boolean alarm,Integer winchSlot,String recordCode,String direction,int offset,int limit) {
        return events(from,to,alarm,winchSlot,recordCode,offset,limit);
    }
    Optional<Event> event(String eventCode);
    List<Period> periods(String type,Instant from,Instant to,String scopeId);
    List<Height> heights(String type,Instant start,String scopeId);
    List<Height> runHeights(String recordCode,String scopeId);
    default List<Height> runHeights(String recordCode,String scopeId,Instant from,Instant to) {return runHeights(recordCode,scopeId);}
    List<Event> activeEvents();
    ArchiveMetricBo archive(String scopeId,String source,Instant from,Instant to,String grouping);
}

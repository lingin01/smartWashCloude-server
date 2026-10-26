package com.company.project.domain.bo;
import lombok.Builder;
import java.time.*;
import java.util.*;

@Builder(toBuilder=true)
public record ExportTaskBo(String taskCode,String requestId,String systemCode,String systemId,String deviceId,
                           Long creatorUserId,String operatorId,String exportType,String unit,Selection filters,
                           String state,String phase,Instant createdAt,Instant startedAt,Instant snapshotAt,
                           Instant finishedAt,long processedRows,long outputRows,List<File> files,
                           List<TableCount> tableCounts,String storeId,String errorCode,String errorMessage) {
    public ExportTaskBo {
        files=files==null?List.of():List.copyOf(files);tableCounts=tableCounts==null?List.of():List.copyOf(tableCounts);
    }
    public record Selection(Instant startTime,Instant endTime,String recordCode,List<String> recordCodes,List<String> eventCodes,
                            String winchCode,String deviceCode,String scopeId,Integer winchSlot,String motionRole,String direction,
                            String runResult,String levels,String period,LocalDate startDate,LocalDate endDate,String metric,String tensionMetric) {
        public Selection {recordCodes=recordCodes==null?List.of():List.copyOf(recordCodes);eventCodes=eventCodes==null?List.of():List.copyOf(eventCodes);}
    }
    public record File(String fileCode,String fileName,String relativePath,String mediaType,long sizeBytes,long dataRows,String rowKind) { }
    public record TableCount(String tableName,String rowKind,long rows) { }
}

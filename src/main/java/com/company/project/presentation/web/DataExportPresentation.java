package com.company.project.presentation.web;

import com.company.project.domain.exception.PageApiException;
import com.company.project.dto.page.PageRequests.*;
import com.company.project.vo.page.PageResponses.*;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** 统计导出器接入前不创建虚假任务，也不发布空文件或半成品。 */
@Component
public class DataExportPresentation {
    private final PageAccess access;
    public DataExportPresentation(PageAccess access) { this.access = access; }

    public ExportTaskResponse create(ExportCreateRequest request, Authentication auth) {
        String permission = "EXCEPTIONS".equals(request.exportType()) ? "alarm:read" : "device:read";
        var resolved = access.resolve(request.systemCode(), request.requestId(), permission, auth);
        if (request.filters().winchCode() != null) access.winch(resolved.system(), request.filters().winchCode());
        if (request.filters().deviceCode() != null) access.winch(resolved.system(), request.filters().deviceCode());
        var f=request.filters();var winch=f.winchCode()==null?null:access.winch(resolved.system(),f.winchCode());
        var device=f.deviceCode()==null?null:access.winch(resolved.system(),f.deviceCode());
        if(winch!=null&&device!=null&&!winch.winchCode().equals(device.winchCode()))throw new IllegalArgumentException("设备筛选互相冲突");
        var selection=new com.company.project.domain.bo.ExportTaskBo.Selection(
                f.startTime()==null?null:f.startTime().toInstant(),f.endTime()==null?null:f.endTime().toInstant(),f.recordCode(),f.recordCodes(),f.eventCodes(),
                f.winchCode(),f.deviceCode(),winch!=null?winch.winchId():device!=null?device.winchId():null,device!=null?device.groupNo():winch!=null?winch.groupNo():null,f.motionRole(),f.direction(),f.runResult(),f.levels(),
                f.period(),f.startDate(),f.endDate(),f.metric(),f.tensionMetric());
        return response(exporter(resolved).create(request.requestId(),request.exportType(),request.unit(),selection));
    }

    public ExportListResponse list(ExportListQuery query, Authentication auth) {
        String permission = "EXCEPTIONS".equals(query.getExportType()) ? "alarm:read" : "device:read";
        var resolved=access.resolve(query.getSystemCode(), null, permission, auth);
        var page=exporter(resolved).list(query.getExportType(),query.getState(),(query.getPage()-1)*query.getPageSize(),query.getPageSize());
        return new ExportListResponse(page.entries().stream().map(this::response).toList(),page.total(),query.getPage(),query.getPageSize());
    }

    public ExportTaskResponse task(String systemCode, String taskCode, Authentication auth) {
        return response(exporter(access.resolve(systemCode, null, "device:read", auth)).task(taskCode));
    }

    public ResponseEntity<Resource> download(String systemCode, String taskCode, String fileCode, Authentication auth) {
        var file=exporter(access.resolve(systemCode,null,"device:read",auth)).download(taskCode,fileCode);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(file.mediaType())).contentLength(file.sizeBytes())
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,org.springframework.http.ContentDisposition.attachment()
                        .filename(file.fileName(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(new org.springframework.core.io.FileSystemResource(file.path()));
    }
    private com.company.project.domain.bo.ExportAccess exporter(PageAccess.Resolved resolved) {
        var exporter=access.exports(resolved);if(exporter==null)throw notReady();return exporter;
    }
    private ExportTaskResponse response(com.company.project.domain.bo.ExportTaskBo task) {
        var f=task.filters();
        var filters=new ExportSelection(f.startTime(),f.endTime(),f.recordCode(),f.recordCodes(),f.eventCodes(),f.winchCode(),f.deviceCode(),
                f.motionRole(),f.direction(),f.runResult(),f.levels(),f.period(),f.startDate(),f.endDate(),f.metric(),f.tensionMetric());
        return new ExportTaskResponse(task.taskCode(),task.systemCode(),task.exportType(),task.state(),task.phase(),filters,task.unit(),"M106_EXPORT_V1",
                task.createdAt(),task.startedAt(),task.snapshotAt(),task.finishedAt(),task.processedRows(),task.outputRows(),
                task.files().stream().map(v->new ExportFile(v.fileCode(),v.fileName(),v.mediaType(),v.sizeBytes(),v.dataRows(),v.rowKind(),"SUCCESS".equals(task.state()))).toList(),
                task.tableCounts().stream().map(v->new ExportTableCount(v.tableName(),v.rowKind(),v.rows())).toList(),task.errorCode(),task.errorMessage());
    }

    private PageApiException notReady() {
        return new PageApiException(503, "EXPORT_NOT_READY", "后台导出任务及完整文件存储尚未接通");
    }
}

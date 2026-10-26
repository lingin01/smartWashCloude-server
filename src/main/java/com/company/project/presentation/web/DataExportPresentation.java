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
        throw notReady();
    }

    public ExportListResponse list(ExportListQuery query, Authentication auth) {
        String permission = "EXCEPTIONS".equals(query.getExportType()) ? "alarm:read" : "device:read";
        access.resolve(query.getSystemCode(), null, permission, auth);
        throw notReady();
    }

    public ExportTaskResponse task(String systemCode, String taskCode, Authentication auth) {
        access.resolve(systemCode, null, "device:read", auth);
        throw notReady();
    }

    public ResponseEntity<Resource> download(String systemCode, String taskCode, String fileCode, Authentication auth) {
        access.resolve(systemCode, null, "device:read", auth);
        throw notReady();
    }

    private PageApiException notReady() {
        return new PageApiException(503, "EXPORT_NOT_READY", "后台导出任务及完整文件存储尚未接通");
    }
}

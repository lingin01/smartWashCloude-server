package com.company.project.controller;

import com.company.project.domain.Result;
import com.company.project.dto.page.PageRequests.*;
import com.company.project.presentation.web.DataExportPresentation;
import com.company.project.vo.page.PageResponses.*;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 导出任务入口。下载只接受稳定文件编号，不接收客户端路径或 SQL。 */
@RestController
@RequestMapping("/api/command-center/exports")
public class DataExportController {
    private final DataExportPresentation presentation;
    public DataExportController(DataExportPresentation presentation) { this.presentation = presentation; }

    @PostMapping
    public ResponseEntity<Result<ExportTaskResponse>> create(@Valid @RequestBody ExportCreateRequest request, Authentication auth) {
        return ResponseEntity.accepted().body(Result.success(presentation.create(request, auth)));
    }

    @GetMapping
    public Result<ExportListResponse> list(@Valid @ModelAttribute ExportListQuery query, Authentication auth) {
        return Result.success(presentation.list(query, auth));
    }

    @GetMapping("/{taskCode}")
    public Result<ExportTaskResponse> task(@PathVariable String taskCode,
            @Valid @ModelAttribute SystemQuery query, Authentication auth) {
        return Result.success(presentation.task(query.getSystemCode(), taskCode, auth));
    }

    @GetMapping("/{taskCode}/files/{fileCode}")
    public ResponseEntity<Resource> download(@PathVariable String taskCode, @PathVariable String fileCode,
            @Valid @ModelAttribute SystemQuery query, Authentication auth) {
        return presentation.download(query.getSystemCode(), taskCode, fileCode, auth);
    }
}

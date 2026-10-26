package com.company.project.domain.bo;
import com.company.project.domain.bo.telemetry.RunHistoryBo.Page;
import java.nio.file.Path;
/** 绑定账号和系统的导出操作，不接受客户端文件路径。 */
public interface ExportAccess {
    ExportTaskBo create(String requestId,String type,String unit,ExportTaskBo.Selection selection);
    Page<ExportTaskBo> list(String type,String state,int offset,int limit);
    ExportTaskBo task(String taskCode);
    Download download(String taskCode,String fileCode);
    record Download(Path path,String fileName,String mediaType,long sizeBytes) { }
}

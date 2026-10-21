package com.company.project.domain;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 命令执行结果 BO
 */
public class CommandResult {
    /** 对应命令ID */
    private String commandId;
    /** 是否成功 */
    private Boolean success;
    /** 返回数据 */
    private Map<String, Object> data;
    /** 错误码 */
    private String errorCode;
    /** 错误信息 */
    private String errorMessage;
    /** 完成时间 */
    private LocalDateTime completeTime;

    // TODO: Lombok @Data
}

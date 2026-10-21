package com.company.project.domain;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 待发送/已发送命令 BO
 */
public class Command {
    /** 命令唯一ID */
    private String commandId;
    /** 目标设备编码 */
    private String deviceCode;
    /** 命令类型: START/STOP/RESET/QUERY */
    private String commandType;
    /** 命令参数 */
    private Map<String, Object> params;
    /** 命令状态: PENDING/SENT/SUCCESS/FAILED/TIMEOUT */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;

    // TODO: Lombok @Data
}

package com.company.project.domain.enums;

/**
        * 记录运行状态枚举
 */
public enum RecordRunStatusEnum {
    /** 启动中 \*/
    STARTING(1, "启动中"),
    /** 运行中 \*/
    RUNNING(2, "运行中"),
    /** 执行完成 */
    COMPLETED(3, "执行完成"),
    /** 手动停止 */
    STOPPED(4, "手动停止"),
    /** 执行失败（业务逻辑失败） \*/
    FAILED(5, "执行失败"),
    /** 执行超时 */
    TIMEOUT(6, "执行超时"),
    /** 系统异常/程序报错 */
    ERROR(7, "系统异常");

    private final Integer code;
    private final String desc;

    RecordRunStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}


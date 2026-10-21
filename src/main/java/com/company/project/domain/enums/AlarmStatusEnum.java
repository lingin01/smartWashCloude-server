package com.company.project.domain.enums;



/**
 * 告警状态枚举
 * 告警中  已恢复
 */
public enum AlarmStatusEnum {

    ACTIVE("告警中"),
    RECOVERED("已恢复");

    private final String desc;

    // 带参构造方法
    AlarmStatusEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

}

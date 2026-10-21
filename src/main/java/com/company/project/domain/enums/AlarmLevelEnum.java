package com.company.project.domain.enums;



/**
 *
 *告警级别：一般、重要、紧急
 *
*/

public enum AlarmLevelEnum {

    NORMAL("一般"),
    IMPORTANT("重要"),
    URGENT("紧急");

    private final String desc;

    // 带参构造方法
    AlarmLevelEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}

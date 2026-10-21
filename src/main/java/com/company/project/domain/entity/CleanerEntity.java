package com.company.project.domain.entity;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 清洗器实体

 */
@Data
public class CleanerEntity {
    /** 清洗器序号 主键*/
    private Long id;

    /** 清洗器编号 */
    private String code;

    /** 清洗器名称 */
    private String name;

    /** 安装位置 */
    private String location;

    /** 分解槽数量 */
    private Integer tankCount;

    /** 设备类型 */
    private String deviceType;

    /** 当前告警数 */
    private Integer alarmCount; //动态数据

    /** 在线状态 */
    private boolean onlineStatus;//默认是0是离线，1是在线

    /** 运行状态编码 */
    private String runStatus;//在变成业务对象的时候会变成吗枚举类型的//动态数据 而且默认为是休闲

    /** 最后心跳时间 */
    private String lastHeartbeat;//动态数据

    /** 创建人 */
    private String Creator;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    private Integer deleted;// 删除
}

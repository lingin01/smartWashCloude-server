package com.company.project.vo;

import lombok.Data;

/**
 * 设备状态 VO（Controller → 前端）
 *  * 对应前端显示数据：CleaningMachine
 */
@Data
public class CleanerVO {

    //显示统计数据
    /** 清洗器编号 */
    private String machineCode;

    /** 清洗器名称 */
    private String name;

    /** 安装位置 */
    private String location;

    /** 分解槽数量 */
    private Integer tankCount;

    /** 当前告警数 */
    private Integer alarmCount; //动态数据

    /** 在线状态编码 */
    private String onlineStatus;//这里到到时候需要在线离线

    /** 运行状态编码 */
    private String runStatus;//在变成业务对象的时候会变成吗枚举类型的//动态数据 而且默认为是休闲 ，运行，紧停 ，故障

    /** 最后心跳时间 */
    private String lastHeartbeat;//动态数据

    //详情数据

    /** 创建时间 */
    private String CreationDate;

}

package com.company.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分解槽状态 VO（Controller → 前端）
 */

@Data
public class TankVO {

    /** 分解槽编码 */
    private String tankCode;

    /** 分解槽名称 */
    private String tankName;

    /** 安装/所在位置 */
    private String location;

    /** 所属清洗设备编号 */
    private String machineCode;

    /** 槽体高度 (单位：m) */
    private Double height;

    /** 槽体内径 (单位：m) */
    private Double innerDiameter;

    /** 运行状态 空闲，清洗中，停用，故障*/
    private String status;

    /** 上次清洗时间 */
    private LocalDateTime lastCleaningTime;

    //详情数据
    /** 模型地址 */
    private String modelUrl;
}

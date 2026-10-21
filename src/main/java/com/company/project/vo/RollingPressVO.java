package com.company.project.vo;

import lombok.Data;

/**
 * 卷压机视图对象
 */
@Data
public class RollingPressVO {
    /** 卷压机名称 */
    private String rollerName;

    /** 卷压机设备编码 */
    private String rollerDeviceCode;

    /** 设备重量(单位:吨) */
    private Double rollerWeight;

    /** 运行状态 停机 运行 故障 */
    private String rollerStatus;
}

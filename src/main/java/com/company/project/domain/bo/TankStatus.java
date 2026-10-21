package com.company.project.domain.bo;

import java.time.LocalDateTime;

/**
 * 分解槽状态 BO
 */
public class TankStatus {
    /** 分解槽编码 */
    private String tankCode;
    /** 温度 */
    private Double temperature;
    /** 压力 */
    private Double pressure;
    /** 液位 */
    private Double liquidLevel;
    /** 清洁度 0-100 */
    private Integer cleanliness;
    /** 卷压机转速 */
    private Double rollerSpeed;
    /** 数据更新时间 */
    private LocalDateTime updateTime;


    public boolean isReady(){

        return false;
    }


    public String getDetail(){
        return "";
    }

}

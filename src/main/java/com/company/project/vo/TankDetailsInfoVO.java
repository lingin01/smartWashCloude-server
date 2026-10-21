package com.company.project.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分解槽详情信息VO
 * 整合槽体、卷压机、压力传感器、告警信息
 */
@Data
public class TankDetailsInfoVO {

    /** 分解槽基础信息 */
    private TankVO tank;

    /** 卷压机信息 */
    private RollingPressVO rollingPress;

    /** 压力传感器信息 */
    private PressureSensorVO pressureSensor;


    /** 当前分解槽告警信息 */
    private AlarmVO alarm;


    /**
     * 组装模拟假数据
     */
    public void buildMockData() {
        // 1. 分解槽基础信息
        TankVO tank = new TankVO();
        tank.setTankCode("TK-001");
        tank.setTankName("一号分解槽");
        tank.setLocation("东厂区A区1号位");
        tank.setMachineCode("CL-001");
        tank.setHeight(12.5);
        tank.setInnerDiameter(5.8);
        tank.setStatus("清洗中");
        tank.setLastCleaningTime(LocalDateTime.now().minusHours(1));
        tank.setModelUrl("/model/tank001.glb");
        this.setTank(tank);

        // 2. 卷压机信息
        RollingPressVO press = new RollingPressVO();
        press.setRollerName("主卷压机-A01");
        press.setRollerDeviceCode("RP-A001");
        press.setRollerWeight(8.6);
        press.setRollerStatus("运行");
        this.setRollingPress(press);

        // 3. 压力传感器信息
        PressureSensorVO sensor = new PressureSensorVO();
        sensor.setSensorName("槽体压力传感器");
        sensor.setSensorDeviceCode("PS-001");
        sensor.setPressureData(0.85);
        sensor.setSensorStatus("正常");
        sensor.setUpdateData("2026-06-14 14:25:30");
        this.setPressureSensor(sensor);

        // 4. 告警信息
        AlarmVO alarm = new AlarmVO();
        alarm.setAlarmCode("AL20260614002");
        alarm.setCleanerCode("TK-001");
        alarm.setAlarmLevel("一般");
        alarm.setAlarmStatus("恢复");
        alarm.setAlarmTime(LocalDateTime.now().minusMinutes(20));
        this.setAlarm(alarm);
    }
}

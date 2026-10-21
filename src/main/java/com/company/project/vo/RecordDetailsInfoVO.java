package com.company.project.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 清洗记录详情汇总VO
 * 包含槽体数据、压力曲线数据、告警统计、清洗时长统计
 */
@Data
public class RecordDetailsInfoVO {

    RecordVO recordVO;

    /** 所有分解槽基础数据 */
    private List<TankVO> tankList;

    /** 压力数据变化明细（时序数据） */
    private List<PressureSensorVO> pressureDataList;

    /** 本次告警详情数据 */
    private AlarmVOS currentAlarmList;

    /** 单次平均清洗时长(单位：秒/分钟) */
    private Double avgCleanTime;

    /** 清洗总次数 */
    private Integer cleanTotalCount;

    /** 累计总清洗时长(单位：秒/分钟) */
    private Long totalCleanTime;

    /**
     * 组装模拟假数据
     */
    public void buildMockData() {
        // 1. 清洗记录主信息
        RecordVO record = new RecordVO();
        record.setRecordCode("REC-20260614001");
        record.setTaskCode("TASK-001");
        record.setPlanCode("PLAN-20260614001");
        record.setCleanerCode("CL-001");
        record.setOperator("管理员");
        record.setStartTime(LocalDateTime.now().minusHours(3));
        record.setEndTime(LocalDateTime.now().minusHours(1));
        record.setTotalDuration(7200L);
        record.setStatus(1);
        record.setCreateTime(LocalDateTime.now().minusHours(3));
        this.setRecordVO(record);

        // 2. 分解槽列表
        List<TankVO> tankList = new ArrayList<>();
        TankVO tank1 = new TankVO();
        tank1.setTankCode("TK-001");
        tank1.setTankName("一号分解槽");
        tank1.setLocation("东厂区A区");
        tank1.setMachineCode("CL-001");
        tank1.setHeight(12.5);
        tank1.setInnerDiameter(5.8);
        tank1.setStatus("空闲");
        tank1.setLastCleaningTime(LocalDateTime.now().minusHours(1));
        tank1.setModelUrl("/model/tank001.glb");
        tankList.add(tank1);

        TankVO tank2 = new TankVO();
        tank2.setTankCode("TK-002");
        tank2.setTankName("二号分解槽");
        tank2.setLocation("东厂区A区");
        tank2.setMachineCode("CL-001");
        tank2.setHeight(12.2);
        tank2.setInnerDiameter(5.6);
        tank2.setStatus("空闲");
        tank2.setLastCleaningTime(LocalDateTime.now().minusHours(1));
        tank2.setModelUrl("/model/tank002.glb");
        tankList.add(tank2);
        this.setTankList(tankList);

        // 3. 压力时序数据
        List<PressureSensorVO> pressureList = new ArrayList<>();
        PressureSensorVO p1 = new PressureSensorVO();
        p1.setSensorName("槽体压力传感器");
        p1.setSensorDeviceCode("PS-001");
        p1.setPressureData(0.72);
        p1.setSensorStatus("正常");
        p1.setUpdateData("2026-06-14 08:10:00");

        PressureSensorVO p2 = new PressureSensorVO();
        p2.setSensorName("槽体压力传感器");
        p2.setSensorDeviceCode("PS-001");
        p2.setPressureData(0.85);
        p2.setSensorStatus("正常");
        p2.setUpdateData("2026-06-14 09:20:00");

        PressureSensorVO p3 = new PressureSensorVO();
        p3.setSensorName("槽体压力传感器");
        p3.setSensorDeviceCode("PS-001");
        p3.setPressureData(0.68);
        p3.setSensorStatus("正常");
        p3.setUpdateData("2026-06-14 10:30:00");
        pressureList.add(p1);
        pressureList.add(p2);
        pressureList.add(p3);
        this.setPressureDataList(pressureList);

        // 4. 告警数据
        AlarmVOS alarmVos = new AlarmVOS();
        List<AlarmVO> alarmList = new ArrayList<>();
        AlarmVO alarm = new AlarmVO();
        alarm.setAlarmCode("AL20260614002");
        alarm.setCleanerCode("TK-001");
        alarm.setAlarmLevel("一般");
        alarm.setAlarmStatus("恢复");
        alarm.setAlarmTime(LocalDateTime.now().minusHours(2));
        alarmList.add(alarm);
        alarmVos.setAlarmVOS(alarmList);
        alarmVos.setAlarmTotalCount(1);
        this.setCurrentAlarmList(alarmVos);

        // 5. 统计字段
        this.setAvgCleanTime(240.0);
        this.setCleanTotalCount(12);
        this.setTotalCleanTime(28800L);
    }
}

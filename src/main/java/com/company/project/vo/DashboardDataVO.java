package com.company.project.vo;


import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DashboardDataVO {
    //显示总条数stats
    private DeviceStatusRecordVO stats;

    //显示最新几次alarms
    private LatestAlarmVOS alarms;

    //显示最新的machines;
    private List<CleanerVO> machines;

    /**
     * 封装：构建模拟假数据
     */
    public void buildMockData() {
        // 1. 设备统计数据
        DeviceStatusRecordVO stats = new DeviceStatusRecordVO();
        stats.setTotalCleaners(2);
        stats.setOnlineCount(2);
        stats.setRunningCount(1);
        stats.setAlarmCount(1);
        this.setStats(stats);

        // 2. 最新告警数据（适配 List 集合）
        LatestAlarmVOS latestAlarmVos = new LatestAlarmVOS();
        List<AlarmVO> alarmList = new ArrayList<>();

        // 告警1
        AlarmVO alarm1 = new AlarmVO();
        alarm1.setAlarmCode("AL20260614001");
        alarm1.setCleanerCode("CL-002");
        alarm1.setAlarmLevel("重要");
        alarm1.setAlarmStatus("未恢复");
        alarm1.setAlarmTime(LocalDateTime.now());
        alarmList.add(alarm1);

        latestAlarmVos.setAlarmList(alarmList);
        this.setAlarms(latestAlarmVos);

        // 3. 清洗器列表数据
        List<CleanerVO> machineList = new ArrayList<>();

        CleanerVO machine1 = new CleanerVO();
        machine1.setMachineCode("CL-001");
        machine1.setName("一号清洗设备");
        machine1.setLocation("东厂区A区");
        machine1.setTankCount(4);
        machine1.setAlarmCount(0);
        machine1.setOnlineStatus("在线");
        machine1.setRunStatus("运行");
        machine1.setLastHeartbeat("2026-06-14 10:22:36");
        machineList.add(machine1);

        CleanerVO machine2 = new CleanerVO();
        machine2.setMachineCode("CL-002");
        machine2.setName("二号清洗设备");
        machine2.setLocation("东厂区B区");
        machine2.setTankCount(4);
        machine2.setAlarmCount(1);
        machine2.setOnlineStatus("在线");
        machine2.setRunStatus("故障");
        machine2.setLastHeartbeat("2026-06-14 10:21:12");
        machineList.add(machine2);

        this.setMachines(machineList);
    }
}

package com.company.project.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CleanerDetailsInfoVO {

    CleanerVO detailsInfo;

    TankBrieflyInfoVOS tanks;

    /**
     * 组装模拟假数据
     */
    public void buildMockData() {
        // 组装清洗器详情
        CleanerVO cleaner = new CleanerVO();
        cleaner.setMachineCode("CL-001");
        cleaner.setName("一号清洗设备");
        cleaner.setLocation("东厂区A区");
        cleaner.setTankCount(4);
        cleaner.setAlarmCount(0);
        cleaner.setOnlineStatus("在线");
        cleaner.setRunStatus("运行");
        cleaner.setLastHeartbeat("2026-06-14 11:30:22");
        cleaner.setCreationDate("2026-01-10 08:30:00");
        this.setDetailsInfo(cleaner);

        // 初始化集合包装类
        TankBrieflyInfoVOS tanks = new TankBrieflyInfoVOS();
        List<TankVO> tankList = new ArrayList<>();

        TankVO tank1 = new TankVO();
        tank1.setTankCode("TK-001");
        tank1.setTankName("一号分解槽");
        tank1.setLocation("东厂区A区");
        tank1.setMachineCode("CL-001");
        tank1.setHeight(12.5);
        tank1.setInnerDiameter(5.8);
        tank1.setStatus("清洗中");
        tank1.setLastCleaningTime(LocalDateTime.now().minusHours(1));
        tank1.setModelUrl("/model/tank001.glb");
        tankList.add(tank1);

        tanks.setTankList(tankList);
        this.setTanks(tanks);
    }
}

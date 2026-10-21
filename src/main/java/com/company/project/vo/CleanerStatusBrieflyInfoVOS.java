package com.company.project.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;


//清洗器列表简要数据
@Data
public class CleanerStatusBrieflyInfoVOS {

//    private DeviceStatusRecordVO deviceStatusRecordVO;

    private List<CleanerVO> list;

    private Integer total;

    private  Integer page;

    private  Integer pageSize;

    public void buildMockData() {
        List<CleanerVO> list = new ArrayList<>();

        CleanerVO vo1 = new CleanerVO();
        vo1.setMachineCode("CL-001");
        vo1.setName("一号清洗设备");
        vo1.setLocation("东厂区A区");
        vo1.setTankCount(4);
        vo1.setAlarmCount(0);
        vo1.setOnlineStatus("在线");
        vo1.setRunStatus("运行");
        vo1.setLastHeartbeat("2026-06-14 10:22:36");
        list.add(vo1);

        CleanerVO vo2 = new CleanerVO();
        vo2.setMachineCode("CL-002");
        vo2.setName("二号清洗设备");
        vo2.setLocation("东厂区B区");
        vo2.setTankCount(4);
        vo2.setAlarmCount(1);
        vo2.setOnlineStatus("在线");
        vo2.setRunStatus("故障");
        vo2.setLastHeartbeat("2026-06-14 10:21:12");
        list.add(vo2);

        this.setList(list);
    }
}



package com.company.project.vo;


import lombok.Data;

@Data
public class DeviceStatusRecordVO {

    int totalCleaners;//清洗器总数

    int onlineCount;//清洗器在线数量

    int runningCount;//清洗器运行总数量

    int alarmCount;//清洗器告警总数量
}

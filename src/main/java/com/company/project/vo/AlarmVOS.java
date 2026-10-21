package com.company.project.vo;

import lombok.Data;

import java.util.List;


@Data
public class AlarmVOS {


    List<AlarmVO> alarmVOS;

    private Integer alarmTotalCount;
}

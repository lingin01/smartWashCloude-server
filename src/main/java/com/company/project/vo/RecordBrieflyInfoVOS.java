package com.company.project.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Data
public class RecordBrieflyInfoVOS {

    List<RecordVO> recordVOS;


    /**
     * 组装模拟假数据
     */
    public void buildMockData() {
        List<RecordVO> recordList = new ArrayList<>();

        RecordVO record1 = new RecordVO();
        record1.setRecordCode("REC-20260614001");
        record1.setTaskCode("TASK-001");
        record1.setPlanCode("PLAN-20260614001");
        record1.setCleanerCode("CL-001");
        record1.setOperator("管理员");
        record1.setStartTime(LocalDateTime.now().minusHours(3));
        record1.setEndTime(LocalDateTime.now().minusHours(1));
        record1.setTotalDuration(7200L);
        record1.setStatus(1);
        record1.setCreateTime(LocalDateTime.now().minusHours(3));
        recordList.add(record1);

        RecordVO record2 = new RecordVO();
        record2.setRecordCode("REC-20260614002");
        record2.setTaskCode("TASK-002");
        record2.setPlanCode("PLAN-20260614002");
        record2.setCleanerCode("CL-002");
        record2.setOperator("运维人员");
        record2.setStartTime(LocalDateTime.now().minusHours(2));
        record2.setEndTime(LocalDateTime.now().minusMinutes(40));
        record2.setTotalDuration(4800L);
        record2.setStatus(1);
        record2.setCreateTime(LocalDateTime.now().minusHours(2));
        recordList.add(record2);

        RecordVO record3 = new RecordVO();
        record3.setRecordCode("REC-20260614003");
        record3.setTaskCode("TASK-003");
        record3.setPlanCode("PLAN-20260614003");
        record3.setCleanerCode("CL-003");
        record3.setOperator("管理员");
        record3.setStartTime(LocalDateTime.now().minusDays(1));
        record3.setEndTime(LocalDateTime.now().minusDays(1).plusHours(2));
        record3.setTotalDuration(6000L);
        record3.setStatus(0);
        record3.setCreateTime(LocalDateTime.now().minusDays(1));
        recordList.add(record3);

        this.setRecordVOS(recordList);
    }
}

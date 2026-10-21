package com.company.project.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class PlanBrieflyInfoVOS {

    List<PlanDetailsInfoVO>  planDetailsInfoVOS;

    /**
     * 组装模拟假数据
     */
    public void buildMockData() {
        List<PlanDetailsInfoVO> planList = new ArrayList<>();

        // 计划1：单次任务
        PlanDetailsInfoVO plan1 = new PlanDetailsInfoVO();
        plan1.setPlanCode("PLAN-20260614001");
        plan1.setPlanName("A区一号槽单次清洗计划");
        plan1.setCleanerCode("CL-001");
        plan1.setPlanType(false);
        plan1.setWorkTime(null);
        plan1.setRestTime(null);
        plan1.setCleanSpeed(2.6);
        plan1.setBaseTension(520.0);
        plan1.setTensionRange("480 ~ 560 N");
        plan1.setExecuteTime(LocalDateTime.now().plusHours(2));
        plan1.setStatus(1);
        planList.add(plan1);

        // 计划2：周期任务
        PlanDetailsInfoVO plan2 = new PlanDetailsInfoVO();
        plan2.setPlanCode("PLAN-20260614002");
        plan2.setPlanName("B区设备循环清洗计划");
        plan2.setCleanerCode("CL-002");
        plan2.setPlanType(true);
        plan2.setWorkTime(120);
        plan2.setRestTime(30);
        plan2.setCleanSpeed(2.2);
        plan2.setBaseTension(480.0);
        plan2.setTensionRange("450 ~ 510 N");
        plan2.setExecuteTime(LocalDateTime.now().plusMinutes(30));
        plan2.setStatus(1);
        planList.add(plan2);



        this.setPlanDetailsInfoVOS(planList);
    }

}

package com.company.project.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TankBrieflyInfoVOS {

    List<TankVO> tankList;


    /**
     * 构建模拟假数据
     */
    public void buildMockData() {
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

        TankVO tank2 = new TankVO();
        tank2.setTankCode("TK-002");
        tank2.setTankName("二号分解槽");
        tank2.setLocation("东厂区A区");
        tank2.setMachineCode("CL-001");
        tank2.setHeight(12.2);
        tank2.setInnerDiameter(5.6);
        tank2.setStatus("空闲");
        tank2.setLastCleaningTime(LocalDateTime.now().minusDays(1));
        tank2.setModelUrl("/model/tank002.glb");
        tankList.add(tank2);

        TankVO tank3 = new TankVO();
        tank3.setTankCode("TK-003");
        tank3.setTankName("三号分解槽");
        tank3.setLocation("西厂区C区");
        tank3.setMachineCode("CL-003");
        tank3.setHeight(11.8);
        tank3.setInnerDiameter(5.5);
        tank3.setStatus("故障");
        tank3.setLastCleaningTime(LocalDateTime.now().minusDays(3));
        tank3.setModelUrl("/model/tank003.glb");
        tankList.add(tank3);

        this.setTankList(tankList);
    }
}

package com.company.project.domain.bo;

import java.math.BigDecimal;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** 罐体→清洗系统→PLC/圆环/卷扬机/牵引索/传感器的稳定业务身份。 */
public record CleaningSystemTopologyBo(String tankId, String cleaningSystemId,
                                       String deviceId, String ringId,
                                       BigDecimal ringOwnWeightKilograms,
                                       List<Winch> winches) {
    public CleaningSystemTopologyBo {
        winches = winches == null ? List.of() : List.copyOf(winches);
    }

    /** 当前现场一套系统对应一台PLC；每台绞机恰好对应两根索和两个独立传感器。 */
    public void requireCurrentSiteShape() {
        if (blank(cleaningSystemId) || blank(deviceId) || blank(ringId) || winches.size() != 4) {
            throw new IllegalArgumentException("清洗系统必须包含PLC、圆环和 4 台绞机");
        }
        Set<String> winchIds = new HashSet<>();
        Set<String> ropeIds = new HashSet<>();
        Set<String> sensorIds = new HashSet<>();
        for (Winch winch : winches) {
            if (blank(winch.winchId()) || !winchIds.add(winch.winchId()) || winch.ropes().size() != 2) {
                throw new IllegalArgumentException("每台绞机必须有唯一编号并对应 2 根牵引索");
            }
            for (Rope rope : winch.ropes()) {
                if (blank(rope.ropeId()) || blank(rope.tensionSensorId())
                        || !ropeIds.add(rope.ropeId()) || !sensorIds.add(rope.tensionSensorId())) {
                    throw new IllegalArgumentException("8 根索及其拉力传感器编号必须分别唯一");
                }
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Winch(String winchId, List<Rope> ropes) {
        public Winch {
            ropes = ropes == null ? List.of() : List.copyOf(ropes);
        }
    }

    public record Rope(String ropeId, String tensionSensorId) {
    }
}

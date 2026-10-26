package com.company.project.domain.bo.page;

import java.util.List;
import java.util.Set;

/** 页面公开编号与现场内部编号的明确映射；只供业务层使用，不直接序列化到 HTTP。 */
public record PageSystemBo(String systemCode, String systemName, String deviceId,
                           List<Winch> winches, Set<String> allowedUsernames) {
    public PageSystemBo {
        winches = winches == null ? List.of() : List.copyOf(winches);
        allowedUsernames = allowedUsernames == null ? Set.of() : Set.copyOf(allowedUsernames);
    }

    public record Winch(String winchCode, String winchName, String winchId,
                        Integer groupNo, List<Sensor> sensors) {
        public Winch { sensors = sensors == null ? List.of() : List.copyOf(sensors); }
    }

    public record Sensor(String sensorCode, String sensorName, String sensorId,
                         String ropeId, Integer ropeNo) { }
}

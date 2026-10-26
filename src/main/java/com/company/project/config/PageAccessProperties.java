package com.company.project.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 当前档案表尚无公开编号/名称列，使用人工确认的外部映射；默认不配置任何系统或账号。 */
@Data
@Component
@ConfigurationProperties(prefix = "cleaning.page")
public class PageAccessProperties {
    private List<SystemMapping> systems = new ArrayList<>();

    @Data
    public static class SystemMapping {
        private String systemCode;
        private String systemName;
        private String deviceId;
        private Set<String> allowedUsernames = new HashSet<>();
        private List<WinchMapping> winches = new ArrayList<>();
    }

    @Data
    public static class WinchMapping {
        private String winchCode;
        private String winchName;
        private String winchId;
        private Integer groupNo;
        private List<SensorMapping> sensors = new ArrayList<>();
    }

    @Data
    public static class SensorMapping {
        private String sensorCode;
        private String sensorName;
        private String sensorId;
        private String ropeId;
        private Integer ropeNo;
    }
}

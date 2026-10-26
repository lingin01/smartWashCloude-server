package com.company.project.repository.device;

import com.company.project.config.PageAccessProperties;
import com.company.project.domain.bo.page.PageSystemBo;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 数据层只保存已明确配置的身份映射；不决定登录权限或生成测量/状态。 */
@Component
public class PageSystemRegistry {
    private final Map<String, PageSystemBo> systems;

    public PageSystemRegistry(PageAccessProperties properties) {
        Map<String, PageSystemBo> configured = new LinkedHashMap<>();
        Set<String> devices = new HashSet<>();
        for (var source : properties.getSystems()) {
            requireId(source.getSystemCode());
            requireId(source.getDeviceId());
            if (!devices.add(source.getDeviceId()))
                throw new IllegalArgumentException("一个 PLC 不能配置为多个页面系统");
            var winches = source.getWinches().stream().map(w -> new PageSystemBo.Winch(
                    w.getWinchCode(), w.getWinchName(), w.getWinchId(), w.getGroupNo(),
                    w.getSensors().stream().map(s -> new PageSystemBo.Sensor(s.getSensorCode(),
                            s.getSensorName(), s.getSensorId(),
                            s.getRopeId()==null || s.getRopeId().isBlank() ? s.getSensorId() : s.getRopeId(), s.getRopeNo())).toList())).toList();
            var system = new PageSystemBo(source.getSystemCode(), source.getSystemName(),
                    source.getDeviceId(), winches, source.getAllowedUsernames());
            validateShape(system);
            if (configured.putIfAbsent(system.systemCode(), system) != null)
                throw new IllegalArgumentException("页面系统公开编号重复");
        }
        systems = java.util.Collections.unmodifiableMap(configured);
    }

    public Optional<PageSystemBo> find(String systemCode) { return Optional.ofNullable(systems.get(systemCode)); }
    public List<PageSystemBo> all() { return List.copyOf(systems.values()); }

    private static void validateShape(PageSystemBo system) {
        if (system.winches().size() != 4)
            throw new IllegalArgumentException("页面系统映射必须明确四台设备及各自两路传感器");
        Set<String> winchCodes = new HashSet<>(), winchIds = new HashSet<>();
        Set<String> sensorCodes = new HashSet<>(), sensorIds = new HashSet<>(), ropeIds = new HashSet<>();
        Set<Integer> groups = new HashSet<>();
        for (var winch : system.winches()) {
            requireUnique(winch.winchCode(), winchCodes);
            requireUnique(winch.winchId(), winchIds);
            if (winch.groupNo() == null || winch.groupNo() < 1 || winch.groupNo() > 4
                    || !groups.add(winch.groupNo()) || winch.sensors().size() != 2)
                throw new IllegalArgumentException("页面设备分组和两路传感器归属无效");
            Set<Integer> ropes = new HashSet<>();
            for (var sensor : winch.sensors()) {
                requireUnique(sensor.sensorCode(), sensorCodes);
                requireUnique(sensor.sensorId(), sensorIds);
                requireUnique(sensor.ropeId(), ropeIds);
                if (sensor.ropeNo() == null || sensor.ropeNo() < 1 || sensor.ropeNo() > 2
                        || !ropes.add(sensor.ropeNo()))
                    throw new IllegalArgumentException("页面钢绳序号必须明确为 1 或 2");
            }
        }
    }

    private static void requireUnique(String value, Set<String> values) {
        requireId(value);
        if (!values.add(value)) throw new IllegalArgumentException("页面对象编号重复");
    }

    private static void requireId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}"))
            throw new IllegalArgumentException("页面和现场编号必须明确且格式有效");
    }
}

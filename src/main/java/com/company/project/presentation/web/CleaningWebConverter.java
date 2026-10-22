package com.company.project.presentation.web;

import com.company.project.dto.cleaning.*;
import com.company.project.domain.bo.*;
import com.company.project.domain.param.cleaning.*;
import com.company.project.vo.cleaning.*;
import com.company.project.service.UserService;
import com.company.project.security.RolePermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** 用户侧 DTO/Param/VO 转换集中在表示层，Controller 和大厅不逐字段搬运。 */
@Component
public class CleaningWebConverter {
    private final UserService users;
    private final RolePermissionService permissions;
    public CleaningWebConverter(UserService users, RolePermissionService permissions) {
        this.users = users; this.permissions = permissions;
    }
    public CleaningOperationContext context(String deviceId, String requestId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName()))
            throw new AuthenticationCredentialsNotFoundException("请先登录");
        // 每次读取实际账号状态，禁用用户或变更权限后不能沿用 JWT 中的旧角色。
        var user = users.getByUsername(authentication.getName());
        if (user == null || user.getStatus() == null || user.getStatus() != 1)
            throw new AccessDeniedException("账号不可用，请联系管理员");
        return new CleaningOperationContext(requestId, deviceId, user.getUsername(), permissions.getPermissions(user.getRoleCode()));
    }
    public EmergencyStopParam emergency(String deviceId, EmergencyStopDto dto, Authentication auth) {
        if (dto.reason() != null && dto.reason().length() > 512) throw new IllegalArgumentException("急停说明过长");
        return new EmergencyStopParam(context(deviceId, dto.requestId(), auth), dto.reason());
    }
    public SettingsUpdateParam settings(String deviceId, SettingsUpdateDto dto, Authentication auth) {
        if (dto.settings() == null || dto.expectedVersion() == null)
            throw new IllegalArgumentException("缺少配置或当前版本");
        var value = dto.settings();
        LocalTime time;
        try { time = value.startTime() == null ? null : LocalTime.parse(value.startTime()); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("开始时间格式应为 HH:mm 或 HH:mm:ss"); }
        return new SettingsUpdateParam(context(deviceId, dto.requestId(), auth), dto.expectedVersion(),
                new CleaningSettingsBo(time, value.intervalSeconds(), value.groupSpeedMetersPerMinute()));
    }
    public CleaningCommandVo commandVo(CleaningCommandStatusBo bo) {
        return new CleaningCommandVo(bo.requestId(), bo.commandId(), bo.deviceId(),
                bo.type(), bo.state().name(), bo.code(), bo.message(), bo.createdAt(), bo.expiresAt(),
                bo.updatedAt());
    }
    public CleaningSettingsVo settingsVo(CleaningSettingsViewBo bo) {
        var s = bo.settings();
        return new CleaningSettingsVo(bo.deviceId(), bo.state(), bo.version(), s == null ? null :
                new CleaningSettingsVo.SettingsVo(s.startTime().toString(), s.intervalSeconds(),
                        s.groupSpeedMetersPerMinute()));
    }
    public CleaningRealtimeVo realtimeVo(String deviceId, Optional<TelemetrySample> sample) {
        if (sample.isEmpty()) return new CleaningRealtimeVo(deviceId, false, "尚未取得设备实时数据", null);
        var s = sample.get();
        var status = s.status();
        var statusVo = status == null ? null : new CleaningRealtimeVo.StatusVo(status.systemStatus().name(),
                status.systemStatus().textZh(), status.online(), status.statusValid(), status.emergencyActive(),
                status.faultCode(), status.direction());
        return new CleaningRealtimeVo(deviceId, true, "已取得设备实时数据", new CleaningRealtimeVo.DataVo(
                s.sequence(), s.sampledAt(), s.groupAverageTension(), s.groupAverageSpeed(),
                s.groupAverageHeight(), s.heightSpread(),
                s.ropes().stream().map(r -> new CleaningRealtimeVo.RopeVo(r.ropeId(), r.sensorId(), r.winchId(),
                        r.tension(), r.valid())).toList(),
                s.winches().stream().map(w -> new CleaningRealtimeVo.WinchVo(w.winchId(), w.heightMeters(), w.speed(),
                        w.tensionAverage(), w.online(), w.running(), w.faultCode(), w.torqueKg(),
                        w.torqueValid(), w.heightValid(), w.speedValid(), w.status(), statusText(w.status()),
                        w.direction(), w.validTensionCount())).toList(),
                statusVo, s.ringHeightMeters(), s.heightValid(), s.validTensionCount(),
                s.validSpeedCount(), s.groupSpeedMetersPerMinute(), "kg", "m/min", "m"));
    }
    private static String statusText(String status) {
        if (status == null) return "状态未确认";
        return switch (status) {
            case "STANDBY" -> "待机"; case "RUNNING" -> "运行"; case "MAINTENANCE" -> "检修";
            case "FAULT" -> "故障"; case "OFFLINE" -> "离线"; default -> "状态未确认";
        };
    }
    public CleaningStatisticsVo statisticsVo(com.company.project.domain.bo.statistics.CleaningStatisticsBo source) {
        return new CleaningStatisticsVo(source.deviceId(),source.scope(),source.acceptedSamples(),source.firstSampleAt(),
                source.lastSampleAt(),source.metrics().stream().map(m->new CleaningStatisticsVo.MetricVo(m.metric(),m.targetId(),
                m.unit(),m.validSamples(),m.minimum(),m.maximum(),m.sampleAverage())).toList());
    }
}

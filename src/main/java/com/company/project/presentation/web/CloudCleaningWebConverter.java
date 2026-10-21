package com.company.project.presentation.web;

import com.company.project.dto.cloud.*;
import com.company.project.domain.bo.*;
import com.company.project.domain.param.cloud.*;
import com.company.project.vo.cloud.*;
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
public class CloudCleaningWebConverter {
    private final UserService users;
    private final RolePermissionService permissions;
    public CloudCleaningWebConverter(UserService users, RolePermissionService permissions) {
        this.users = users; this.permissions = permissions;
    }
    public CloudOperationContext context(String boxId, String requestId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName()))
            throw new AuthenticationCredentialsNotFoundException("请先登录");
        // 每次读取实际账号状态，禁用用户或变更权限后不能沿用 JWT 中的旧角色。
        var user = users.getByUsername(authentication.getName());
        if (user == null || user.getStatus() == null || user.getStatus() != 1)
            throw new AccessDeniedException("账号不可用，请联系管理员");
        return new CloudOperationContext(requestId, boxId, user.getUsername(), permissions.getPermissions(user.getRoleCode()));
    }
    public CloudEmergencyStopParam emergency(String boxId, CloudEmergencyStopDto dto, Authentication auth) {
        if (dto == null) throw new IllegalArgumentException("缺少急停请求");
        if (dto.reason() != null && dto.reason().length() > 512) throw new IllegalArgumentException("急停说明过长");
        return new CloudEmergencyStopParam(context(boxId, dto.requestId(), auth), dto.sessionId(), dto.reason());
    }
    public CloudSettingsUpdateParam settings(String boxId, CloudSettingsUpdateDto dto, Authentication auth) {
        if (dto == null || dto.settings() == null || dto.expectedVersion() == null)
            throw new IllegalArgumentException("缺少配置或当前版本");
        var value = dto.settings();
        LocalTime time;
        try { time = value.startTime() == null ? null : LocalTime.parse(value.startTime()); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("开始时间格式应为 HH:mm 或 HH:mm:ss"); }
        return new CloudSettingsUpdateParam(context(boxId, dto.requestId(), auth), dto.expectedVersion(),
                new CloudCleaningSettingsBo(time, value.intervalSeconds(), value.speedLevel(), value.groupSpeedMetersPerMinute()));
    }
    public CloudCommandVo commandVo(CloudCommandStatusBo bo) {
        return new CloudCommandVo(bo.requestId(), bo.cloudCommandId(), bo.boxCommandId(), bo.boxId(),
                bo.type(), bo.state().name(), bo.code(), bo.message(), bo.createdAt(), bo.expiresAt(),
                bo.updatedAt(), bo.resourceVersion());
    }
    public CloudSettingsVo settingsVo(CloudSettingsViewBo bo) {
        var s = bo.settings();
        return new CloudSettingsVo(bo.boxId(), bo.state(), bo.version(), s == null ? null :
                new CloudSettingsVo.SettingsVo(s.startTime().toString(), s.intervalSeconds(),
                        s.speedLevel(), s.groupSpeedMetersPerMinute()));
    }
    public CloudRealtimeVo realtimeVo(String boxId, Optional<TelemetrySample> sample) {
        if (sample.isEmpty()) return new CloudRealtimeVo(boxId, false, "尚未取得设备实时数据", null);
        var s = sample.get();
        var status = s.status();
        var statusVo = status == null ? null : new CloudRealtimeVo.StatusVo(status.systemStatus().name(),
                status.systemStatus().textZh(), status.online(), status.statusValid(), status.emergencyActive(),
                status.faultCode(), status.direction());
        return new CloudRealtimeVo(boxId, true, "已取得设备实时数据", new CloudRealtimeVo.DataVo(s.sessionId(),
                s.sequence(), s.sampledAt(), s.state(), s.speedLevel(), s.groupAverageTension(), s.groupAverageSpeed(),
                s.groupAverageHeight(), s.heightSpread(), s.hasWarning(), s.hasCriticalAlarm(),
                s.ropes().stream().map(r -> new CloudRealtimeVo.RopeVo(r.ropeId(), r.sensorId(), r.winchId(),
                        r.tension(), r.valid(), r.level())).toList(),
                s.winches().stream().map(w -> new CloudRealtimeVo.WinchVo(w.winchId(), w.heightMeters(), w.speed(),
                        w.tensionAverage(), w.online(), w.running(), w.faultCode(), w.torqueNewtonMeters(),
                        w.torqueValid(), w.torqueKg(), w.heightValid(), w.speedValid(), w.status(),
                        statusText(w.status()), w.direction(), w.validTensionCount())).toList(),
                statusVo, s.ringHeightMeters(), s.heightValid(), s.statusVersion(), s.validTensionCount(),
                s.validSpeedCount(), s.groupSpeedMetersPerMinute(), "kg", "m/min", "m"));
    }
    private static String statusText(String status) {
        if (status == null) return "状态未确认";
        return switch (status) {
            case "STANDBY" -> "待机"; case "RUNNING" -> "运行"; case "MAINTENANCE" -> "检修";
            case "FAULT" -> "故障"; case "OFFLINE" -> "离线"; default -> "状态未确认";
        };
    }
}

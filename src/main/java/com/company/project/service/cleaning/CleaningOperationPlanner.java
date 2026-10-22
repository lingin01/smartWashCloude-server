package com.company.project.service.cleaning;
import com.company.project.domain.bo.*;
import com.company.project.domain.param.cleaning.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/** 清洗服务自己的操作规则：形成业务请求，不读写命令状态或设备寄存器。 */
final class CleaningOperationPlanner {
    CleaningCommand emergencyStop(EmergencyStopParam request) {
        String reason=request.reason()==null || request.reason().isBlank()?"用户急停":request.reason();
        return command(request.context(),"emergency_stop",Map.of("reason",reason));
    }
    CleaningCommand updateSettings(SettingsUpdateParam request) {
        var s=request.settings();
        if (request.expectedVersion()<0 || s==null || s.startTime()==null
                || s.intervalSeconds()==null || s.intervalSeconds()<=0)
            throw new IllegalArgumentException("请提供当前配置版本、开始时间及有效运行间隔");
        if (s.groupSpeedMetersPerMinute()==null || s.groupSpeedMetersPerMinute().signum()<=0
                || s.groupSpeedMetersPerMinute().compareTo(BigDecimal.TEN)>0)
            throw new IllegalArgumentException("整组速度必须大于0且不超过10m/min");
        if (s.startTime().getSecond()!=0 || s.startTime().getNano()!=0)
            throw new IllegalArgumentException("启动时间仅支持小时和分钟");
        return command(request.context(),"update_cleaning_parameters",Map.of("expectedVersion",request.expectedVersion(),"settings",s));
    }
    private CleaningCommand command(CleaningOperationContext context,String type,Map<String,Object> payload) {
        Instant now=Instant.now();
        return new CleaningCommand(UUID.randomUUID().toString(),context.deviceId(),type,payload,now,
                context.requestId(),context.operatorId(),now.plusSeconds(30));
    }
}

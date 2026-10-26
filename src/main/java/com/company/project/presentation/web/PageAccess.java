package com.company.project.presentation.web;

import com.company.project.domain.bo.page.PageSystemBo;
import com.company.project.domain.exception.PageApiException;
import com.company.project.domain.param.cleaning.CleaningOperationContext;
import com.company.project.service.CleaningLobbyService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** 网络侧只转换可信登录上下文，系统归属和权限由大厅交给安全服务。 */
@Component
public class PageAccess {
    private final CleaningWebConverter converter;
    private final CleaningLobbyService lobby;

    public PageAccess(CleaningWebConverter converter, CleaningLobbyService lobby) {
        this.converter = converter;
        this.lobby = lobby;
    }

    public Resolved resolve(String systemCode, String requestId, String permission, Authentication auth) {
        var identity = converter.context(null, requestId, auth);
        var system = lobby.pageSystem(identity, systemCode, permission);
        var context = new CleaningOperationContext(requestId, system.deviceId(),
                identity.operatorId(), identity.permissions());
        return new Resolved(system, context);
    }

    public PageSystemBo.Winch winch(PageSystemBo system, String winchCode) {
        return system.winches().stream().filter(w -> w.winchCode().equals(winchCode)).findFirst()
                .orElseThrow(() -> new PageApiException(404, "WINCH_NOT_FOUND", "该系统中不存在此动力设备"));
    }

    public record Resolved(PageSystemBo system, CleaningOperationContext context) {}
}

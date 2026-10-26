package com.company.project.presentation.web;

import com.company.project.domain.AccountRoles;
import com.company.project.domain.Result;
import com.company.project.domain.User;
import com.company.project.domain.exception.PageApiException;
import com.company.project.domain.param.cleaning.CleaningOperationContext;
import com.company.project.dto.account.AccountRequests.*;
import com.company.project.security.JwtTokenProvider;
import com.company.project.security.RolePermissionService;
import com.company.project.service.CleaningLobbyService;
import com.company.project.service.UserService;
import com.company.project.vo.account.AccountResponses.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/** 账号 HTTP 适配，不将用户管理混入清洗命令业务。 */
@Component
public class AccountWebPresentation {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final UserService users;
    private final JwtTokenProvider tokens;
    private final RolePermissionService roles;
    private final CleaningLobbyService lobby;

    public AccountWebPresentation(UserService users, JwtTokenProvider tokens,
                                  RolePermissionService roles, CleaningLobbyService lobby) {
        this.users = users;
        this.tokens = tokens;
        this.roles = roles;
        this.lobby = lobby;
    }

    public Result<TokenResponse> login(LoginRequest request) {
        String token = users.login(request.username(), request.password());
        return Result.success(tokenResponse(token, tokens.generateRefreshToken(request.username())));
    }

    public Result<TokenResponse> refresh(RefreshRequest request) {
        if (!tokens.validateToken(request.refreshToken()) || !tokens.isRefreshToken(request.refreshToken())) {
            throw new PageApiException(401, "INVALID_REFRESH_TOKEN", "刷新凭据无效");
        }
        User user = enabledUser(tokens.getUsername(request.refreshToken()));
        String accessToken = tokens.generateToken(user.getUsername(), user.getRoleCode());
        // 当前没有刷新凭据轮换存储，null 表示继续保留原有效刷新凭据。
        return Result.success(tokenResponse(accessToken, null));
    }

    public Result<UserInfoResponse> userInfo(Authentication authentication) {
        User user = currentUser(authentication);
        Set<String> permissions = permissions(user);
        var context = new CleaningOperationContext(null, null, user.getUsername(), permissions);
        var systems = lobby.pageSystems(context).stream()
                .map(system -> new AuthorizedSystem(system.systemCode(), system.systemName())).toList();
        String currentSystem = systems.size() == 1 ? systems.get(0).systemCode() : null;
        return Result.success(new UserInfoResponse(id(user), user.getUsername(), user.getRealName(),
                user.getAvatar(), user.getEmail(), user.getPhone(), roleCodes(user), roleNames(user),
                permissions, systems, currentSystem));
    }

    public Result<Void> logout(Authentication authentication) {
        currentUser(authentication);
        SecurityContextHolder.clearContext();
        return new Result<>(200, "请清除本地令牌以退出；已签发令牌在到期前仍有效", null);
    }

    public Result<UserListResponse> users(UserListQuery query, Authentication authentication) {
        User current = currentUser(authentication);
        if (!permissions(current).contains("user:read")) {
            throw new PageApiException(403, "FORBIDDEN", "当前账号没有查看用户的权限");
        }
        var page = users.page(query.page(), query.pageSize(), query.keyword());
        var rows = page.getRecords().stream().map(this::listItem).toList();
        return Result.success(new UserListResponse(rows, page.getTotal(), query.page(), query.pageSize(),
                OffsetDateTime.now(BUSINESS_ZONE)));
    }

    private TokenResponse tokenResponse(String token, String refreshToken) {
        long seconds = tokens.getExpiration() / 1000;
        if (seconds <= 0) throw new PageApiException(503, "AUTH_CONFIGURATION_NOT_READY", "登录有效期配置未就绪");
        return new TokenResponse(token, refreshToken, seconds, "SECONDS");
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new PageApiException(401, "UNAUTHENTICATED", "请先登录");
        }
        return enabledUser(authentication.getName());
    }

    private User enabledUser(String username) {
        User user = users.getByUsername(username);
        if (user == null) throw new PageApiException(401, "ACCOUNT_NOT_FOUND", "账号不存在，请重新登录");
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new PageApiException(403, "ACCOUNT_DISABLED", "账号不可用，请联系管理员");
        }
        return user;
    }

    private Set<String> permissions(User user) {
        return user.getRoleCode() == null ? Set.of() : roles.getPermissions(user.getRoleCode());
    }

    private List<String> roleCodes(User user) {
        return user.getRoleCode() == null ? List.of() : List.of(user.getRoleCode());
    }

    private List<RoleName> roleNames(User user) {
        return roleCodes(user).stream().map(code -> new RoleName(code, AccountRoles.name(code))).toList();
    }

    private UserListItem listItem(User user) {
        // 当前账号表没有最近登录事实，不能把 null 解释为从未登录。
        return new UserListItem(id(user), user.getUsername(), user.getRealName(), roleCodes(user),
                roleNames(user), null, "UNKNOWN", user.getCreateTime() == null ? null
                : user.getCreateTime().atZone(BUSINESS_ZONE).toOffsetDateTime());
    }

    private String id(User user) { return user.getId() == null ? null : user.getId().toString(); }
}

package com.company.project.vo.account;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/** 显式保留未知资料的 null，不向页面暴露账号密码实体。 */
public final class AccountResponses {
    private AccountResponses() { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TokenResponse(String token, String refreshToken, long expiresIn,
                                String expiresInUnit) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RoleName(String code, String name) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AuthorizedSystem(String systemCode, String systemName) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record UserInfoResponse(String id, String username, String nickname, String avatar,
                                   String email, String phone, List<String> roles,
                                   List<RoleName> roleNames, Set<String> permissions,
                                   List<AuthorizedSystem> systems, String currentSystemCode) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record UserListItem(String id, String username, String nickname, List<String> roles,
                               List<RoleName> roleNames, OffsetDateTime lastLoginAt,
                               String lastLoginState, OffsetDateTime createdAt) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record UserListResponse(List<UserListItem> list, long total, int page, int pageSize,
                                   OffsetDateTime asOf) { }
}

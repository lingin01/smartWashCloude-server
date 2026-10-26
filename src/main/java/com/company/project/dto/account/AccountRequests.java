package com.company.project.dto.account;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** 账号页面的 HTTP 输入；账号身份始终从登录上下文取得。 */
public final class AccountRequests {
    private AccountRequests() { }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }

    public record RefreshRequest(@NotBlank String refreshToken) { }

    public record UserListQuery(String keyword, @Min(1) Integer page,
                                @Min(1) @Max(100) Integer pageSize) {
        public UserListQuery {
            keyword = keyword == null ? null : keyword.strip();
            page = page == null ? 1 : page;
            pageSize = pageSize == null ? 10 : pageSize;
        }
    }
}

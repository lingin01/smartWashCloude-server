package com.company.project.controller;

import com.company.project.domain.Result;
import com.company.project.dto.account.AccountRequests.UserListQuery;
import com.company.project.presentation.web.AccountWebPresentation;
import com.company.project.vo.account.AccountResponses.UserListResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/** 页面只读账号列表；不向该页面扩展旧管理员 CRUD。 */
@RestController
public class ReadOnlyUserController {
    private final AccountWebPresentation presentation;

    public ReadOnlyUserController(AccountWebPresentation presentation) { this.presentation = presentation; }

    @GetMapping("/api/users")
    public Result<UserListResponse> users(@Valid @ModelAttribute UserListQuery query,
                                         Authentication authentication) {
        return presentation.users(query, authentication);
    }
}

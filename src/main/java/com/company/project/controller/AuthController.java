package com.company.project.controller;

import com.company.project.domain.Result;
import com.company.project.dto.account.AccountRequests.LoginRequest;
import com.company.project.dto.account.AccountRequests.RefreshRequest;
import com.company.project.presentation.web.AccountWebPresentation;
import com.company.project.vo.account.AccountResponses.TokenResponse;
import com.company.project.vo.account.AccountResponses.UserInfoResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountWebPresentation presentation;

    public AuthController(AccountWebPresentation presentation) { this.presentation = presentation; }

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest body) {
        return presentation.login(body);
    }

    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshRequest body) {
        return presentation.refresh(body);
    }

    @GetMapping("/user-info")
    public Result<UserInfoResponse> userInfo(Authentication authentication) {
        return presentation.userInfo(authentication);
    }

    @PostMapping("/logout")
    public Result<Void> logout(Authentication authentication) {
        return presentation.logout(authentication);
    }
}

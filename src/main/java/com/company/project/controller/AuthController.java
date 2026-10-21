package com.company.project.controller;

import com.company.project.domain.Result;
import com.company.project.domain.User;
import com.company.project.security.JwtTokenProvider;
import com.company.project.security.RolePermissionService;
import com.company.project.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RolePermissionService rolePermissionService;

    public AuthController(UserService userService, JwtTokenProvider jwtTokenProvider,
                          RolePermissionService rolePermissionService) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rolePermissionService = rolePermissionService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String token = userService.login(username, password);
        User user = userService.getByUsername(username);
        String refreshToken = jwtTokenProvider.generateRefreshToken(username);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", jwtTokenProvider.getExpiration());
        return Result.success(result);
    }

    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)
                || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("refresh token无效");
        }
        String username = jwtTokenProvider.getUsername(refreshToken);
        User user = userService.getByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String newToken = jwtTokenProvider.generateToken(username, user.getRoleCode());
        return Result.success(Map.of("token", newToken));
    }

    @GetMapping("/user-info")
    public Result<Map<String, Object>> userInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.getByUsername(auth.getName());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        Set<String> permissions = rolePermissionService.getPermissions(user.getRoleCode());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getRealName());
        info.put("avatar", user.getAvatar());
        info.put("email", user.getEmail());
        info.put("phone", user.getPhone());
        info.put("roles", List.of(user.getRoleCode()));
        info.put("permissions", permissions);
        return Result.success(info);
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        SecurityContextHolder.clearContext();
        return Result.success();
    }
}

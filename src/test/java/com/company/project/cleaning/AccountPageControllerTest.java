package com.company.project.cleaning;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.project.controller.AuthController;
import com.company.project.controller.ReadOnlyUserController;
import com.company.project.domain.Result;
import com.company.project.domain.User;
import com.company.project.domain.bo.page.PageSystemBo;
import com.company.project.domain.exception.PageApiException;
import com.company.project.dto.account.AccountRequests.*;
import com.company.project.presentation.web.AccountWebPresentation;
import com.company.project.security.JwtTokenProvider;
import com.company.project.security.RolePermissionService;
import com.company.project.service.CleaningLobbyService;
import com.company.project.service.UserService;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证页面实用契约：账号状态、秒单位、真实权限、默认分页和未知登录事实。 */
class AccountPageControllerTest {
    private UserService users;
    private JwtTokenProvider tokens;
    private CleaningLobbyService lobby;
    private AccountWebPresentation presentation;
    private MockMvc mvc;
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());

    @BeforeEach void setUp() {
        users = mock(UserService.class);
        tokens = mock(JwtTokenProvider.class);
        lobby = mock(CleaningLobbyService.class);
        presentation = new AccountWebPresentation(users, tokens, new RolePermissionService(), lobby);
        var mapper = Jackson2ObjectMapperBuilder.json()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(presentation),
                        new ReadOnlyUserController(presentation))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        when(tokens.getExpiration()).thenReturn(3_600_000L);
    }

    @Test void loginUsesSecondsAndRejectsMissingPasswordBeforeService() throws Exception {
        when(users.login("alice", "password")).thenReturn("access");
        when(tokens.generateRefreshToken("alice")).thenReturn("refresh");
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"alice\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.expiresInUnit").value("SECONDS"));
        clearInvocations(users);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }

    @Test void refreshRechecksAccountAndDoesNotPretendToRotateRefreshCredential() throws Exception {
        when(tokens.validateToken("refresh")).thenReturn(true);
        when(tokens.isRefreshToken("refresh")).thenReturn(true);
        when(tokens.getUsername("refresh")).thenReturn("alice");
        User user = user("VIEWER");
        when(users.getByUsername("alice")).thenReturn(user);
        when(tokens.generateToken("alice", "VIEWER")).thenReturn("new-access");
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"refresh\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").value("new-access"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"refreshToken\":null")));
        user.setStatus(0);
        clearInvocations(tokens);
        assertThrows(PageApiException.class, () -> presentation.refresh(new RefreshRequest("refresh")));
        verify(tokens, never()).generateToken(anyString(), anyString());
    }

    @Test void accessTokenCannotBeUsedAsRefreshCredential() {
        when(tokens.validateToken("access")).thenReturn(true);
        when(tokens.isRefreshToken("access")).thenReturn(false);
        assertThrows(PageApiException.class, () -> presentation.refresh(new RefreshRequest("access")));
        verifyNoInteractions(users);
    }

    @Test void userInfoUsesAuthorizedRealSystemIdentityAndPreservesNullProfile() throws Exception {
        when(users.getByUsername("alice")).thenReturn(user("VIEWER"));
        PageSystemBo system = mock(PageSystemBo.class);
        when(system.systemCode()).thenReturn("site-actual");
        when(system.systemName()).thenReturn("现场系统");
        when(lobby.pageSystems(any())).thenReturn(List.of(system));
        mvc.perform(get("/api/auth/user-info").principal(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("42"))
                .andExpect(jsonPath("$.data.currentSystemCode").value("site-actual"))
                .andExpect(jsonPath("$.data.systems[0].systemName").value("现场系统"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"avatar\":null")));
        verify(lobby).pageSystems(argThat(context -> "alice".equals(context.operatorId())
                && context.permissions().contains("device:read")));
    }

    @Test void usersDefaultsAndExplicitUnknownLoginDoNotLeakCredential() throws Exception {
        User row = user("ADMIN");
        row.setPassword("private-password-hash");
        row.setCreateTime(LocalDateTime.of(2026, 10, 26, 8, 30));
        when(users.getByUsername("alice")).thenReturn(row);
        when(users.page(1, 10, "管理员")).thenReturn(new Page<User>(1, 10, 31).setRecords(List.of(row)));
        mvc.perform(get("/api/users").param("keyword", " 管理员 ").principal(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10)).andExpect(jsonPath("$.data.total").value(31))
                .andExpect(jsonPath("$.data.list[0].lastLoginState").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.list[0].createdAt").value("2026-10-26T08:30:00+08:00"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"lastLoginAt\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-password-hash"))));
    }

    @Test void userListRejectsOversizedPageAndRealRoleChangeImmediately() throws Exception {
        when(users.getByUsername("alice")).thenReturn(user("ADMIN"));
        mvc.perform(get("/api/users").param("pageSize", "101").principal(authentication))
                .andExpect(status().isBadRequest());
        verify(users, never()).page(anyInt(), anyInt(), any());
        when(users.getByUsername("alice")).thenReturn(user("VIEWER"));
        assertThrows(PageApiException.class, () -> presentation.users(new UserListQuery(null, null, null), authentication));
        verify(users, never()).page(anyInt(), anyInt(), any());
    }

    @Test void logoutRequiresActualEnabledAccountAndDescribesLimitedScope() {
        assertThrows(PageApiException.class, () -> presentation.logout(null));
        User user = user("ADMIN");
        when(users.getByUsername("alice")).thenReturn(user);
        Result<Void> result = presentation.logout(authentication);
        assertEquals(200, result.getCode());
        assertNull(result.getData());
        assertTrue(result.getMessage().contains("到期前仍有效"));
        user.setStatus(0);
        assertThrows(PageApiException.class, () -> presentation.logout(authentication));
    }

    private User user(String role) {
        User user = new User();
        user.setId(42L);
        user.setUsername("alice");
        user.setRealName("现场人员");
        user.setRoleCode(role);
        user.setStatus(1);
        return user;
    }
}

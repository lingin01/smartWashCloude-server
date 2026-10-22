package com.company.project.cleaning;

import com.company.project.config.SecurityConfig;
import com.company.project.domain.User;
import com.company.project.controller.*;
import com.company.project.presentation.web.*;
import com.company.project.security.*;
import com.company.project.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CleaningController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RolePermissionService.class,
        CleaningWebPresentation.class, CleaningWebConverter.class})
class CleaningWebSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean UserService users;
    @MockBean CleaningLobbyService lobby;
    @MockBean JwtTokenProvider tokens;
    @MockBean RealtimePushPresentation push;

    @Test void anonymousCannotUseNewControlApi() throws Exception {
        mvc.perform(post("/api/cleaning/devices/plc-001/emergency-stop")
                .contentType("application/json").content("{\"requestId\":\"req-1\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(lobby);
    }
    @Test void refreshTokenCannotAuthenticateDeviceApi() throws Exception {
        when(tokens.validateToken("refresh-only")).thenReturn(true);
        when(tokens.isRefreshToken("refresh-only")).thenReturn(true);
        mvc.perform(get("/api/cleaning/devices/plc-001/realtime")
                .header("Authorization", "Bearer refresh-only")).andExpect(status().isUnauthorized());
        verifyNoInteractions(lobby);
    }
    @Test @WithMockUser(username = "alice", roles = "VIEWER")
    void queryPassesThroughPresentationAndLobbyWithoutMockData() throws Exception {
        var user = new User(); user.setUsername("alice"); user.setStatus(1); user.setRoleCode("VIEWER");
        when(users.getByUsername("alice")).thenReturn(user);
        when(lobby.queryRealtime(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/cleaning/devices/plc-001/realtime"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.deviceId").value("plc-001"));
        verify(lobby).queryRealtime(argThat(context -> context.permissions().contains("device:read")));
    }

    @Test @WithMockUser(username = "alice")
    void removedRoutesHaveNoCompatibilityAliases() throws Exception {
        for (String path : java.util.List.of("/api/cleaning/start", "/api/cleaning/stop", "/api/cleaning/reset",
                "/api/cleaning/devices/plc-001/start", "/api/cleaning/devices/plc-001/reset",
                "/api/cleaning/devices/plc-001/pause", "/api/cleaning/devices/plc-001/resume",
                "/api/cleaning/devices/plc-001/release-emergency-stop")) {
            mvc.perform(post(path).contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/cloud/cleaning/boxes/plc-001/realtime")).andExpect(status().isNotFound());
        verifyNoInteractions(lobby);
    }

    @Test @WithMockUser(username = "alice", roles = "OPERATOR")
    void operatorComesFromLoginAndResponseUsesOnlyLocalCommandIdentity() throws Exception {
        var user = new User(); user.setUsername("alice"); user.setStatus(1); user.setRoleCode("OPERATOR");
        when(users.getByUsername("alice")).thenReturn(user);
        var now = java.time.Instant.now();
        when(lobby.emergencyStop(any())).thenReturn(new com.company.project.domain.bo.CleaningCommandStatusBo(
                "req-1", "cmd-1", "plc-001", "emergency_stop",
                com.company.project.domain.bo.CleaningCommandStatusBo.State.QUEUED,
                "QUEUED", "排队中", now, now.plusSeconds(30), now));
        mvc.perform(post("/api/cleaning/devices/plc-001/emergency-stop")
                .contentType("application/json")
                .content("{\"requestId\":\"req-1\",\"reason\":\"test\",\"operatorId\":\"mallory\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.deviceId").value("plc-001"))
                .andExpect(jsonPath("$.data.commandId").value("cmd-1"))
                .andExpect(jsonPath("$.data.boxId").doesNotExist())
                .andExpect(jsonPath("$.data.boxCommandId").doesNotExist())
                .andExpect(jsonPath("$.data.cloudCommandId").doesNotExist());
        verify(lobby).emergencyStop(argThat(request -> request.context().operatorId().equals("alice")
                && request.context().permissions().contains("device:write")));
    }
}

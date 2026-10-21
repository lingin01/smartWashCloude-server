package com.company.project.cleaning;

import com.company.project.config.SecurityConfig;
import com.company.project.domain.User;
import com.company.project.network_access_layer.web.*;
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

@WebMvcTest(CleaningWebService.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RolePermissionService.class,
        CloudCleaningWebPresentation.class, CloudCleaningWebConverter.class})
class CloudCleaningWebSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean UserService users;
    @MockBean CloudLobbyService lobby;
    @MockBean JwtTokenProvider tokens;

    @Test void anonymousCannotUseNewControlApi() throws Exception {
        mvc.perform(post("/api/cloud/cleaning/boxes/box-001/emergency-stop")
                .contentType("application/json").content("{\"requestId\":\"req-1\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(lobby);
    }
    @Test void refreshTokenCannotAuthenticateDeviceApi() throws Exception {
        when(tokens.validateToken("refresh-only")).thenReturn(true);
        when(tokens.isRefreshToken("refresh-only")).thenReturn(true);
        mvc.perform(get("/api/cloud/cleaning/boxes/box-001/realtime")
                .header("Authorization", "Bearer refresh-only")).andExpect(status().isUnauthorized());
        verifyNoInteractions(lobby);
    }
    @Test @WithMockUser(username = "alice", roles = "VIEWER")
    void queryPassesThroughPresentationAndLobbyWithoutMockData() throws Exception {
        var user = new User(); user.setUsername("alice"); user.setStatus(1); user.setRoleCode("VIEWER");
        when(users.getByUsername("alice")).thenReturn(user);
        when(lobby.queryRealtime(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/cloud/cleaning/boxes/box-001/realtime"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.boxId").value("box-001"));
        verify(lobby).queryRealtime(argThat(context -> context.permissions().contains("device:read")));
    }
}

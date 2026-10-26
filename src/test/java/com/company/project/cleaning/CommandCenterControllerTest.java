package com.company.project.cleaning;

import com.company.project.config.SecurityConfig;
import com.company.project.controller.*;
import com.company.project.domain.User;
import com.company.project.domain.bo.CleaningCommandStatusBo;
import com.company.project.domain.bo.CleaningSettingsViewBo;
import com.company.project.domain.bo.page.PageSystemBo;
import com.company.project.domain.exception.PageApiException;
import com.company.project.domain.param.cleaning.CleaningOperationContext;
import com.company.project.presentation.web.*;
import com.company.project.security.*;
import com.company.project.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CommandCenterController.class, AlarmEventController.class, DataExportController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RolePermissionService.class,
        CleaningWebConverter.class, PageAccess.class, PageSnapshotConverter.class,
        CommandCenterPresentation.class, AlarmEventPresentation.class, DataExportPresentation.class})
class CommandCenterControllerTest {
    @Autowired MockMvc mvc;
    @MockBean UserService users;
    @MockBean CleaningLobbyService lobby;
    @MockBean JwtTokenProvider tokens;
    @MockBean HomeStreamPresentation stream;

    private User current;
    private PageSystemBo system;

    @BeforeEach void configureActualIdentity() {
        current = new User();
        current.setUsername("alice"); current.setStatus(1); current.setRoleCode("OPERATOR");
        when(users.getByUsername("alice")).thenReturn(current);
        system = new PageSystemBo("SITE-01", "测试档案名称", "actual-plc", List.of(
                new PageSystemBo.Winch("WINCH-A", "测试动力名称", "axis-a", 1, List.of(
                        new PageSystemBo.Sensor("SENSOR-A", "左索", "sensor-a", "rope-a", 1),
                        new PageSystemBo.Sensor("SENSOR-B", "右索", "sensor-b", "rope-b", 2)))), Set.of("alice"));
        when(lobby.pageSystem(any(), eq("SITE-01"), anyString())).thenAnswer(invocation -> {
            CleaningOperationContext context = invocation.getArgument(0);
            String permission = invocation.getArgument(2);
            if (!context.permissions().contains(permission)) throw new AccessDeniedException("denied");
            return system;
        });
        when(lobby.queryRealtime(any())).thenReturn(Optional.empty());
        when(lobby.querySettings(any())).thenReturn(new CleaningSettingsViewBo("actual-plc", "NOT_SYNCHRONIZED", null, null));
    }

    @Test void allNewReadRoutesRequireLoginBeforeParametersOrServices() throws Exception {
        for (String path : List.of("home", "home/stream", "winch/detail", "winch/tension/trend", "archive",
                "history/list", "history/detail", "history/point", "parameter-settings", "commands/cmd-1",
                "alarms/latest", "alarms/history", "events/history", "events/event-1", "exports",
                "exports/task-1", "exports/task-1/files/file-1")) {
            mvc.perform(get("/api/command-center/" + path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.data.errorCode").value("UNAUTHENTICATED"));
        }
        mvc.perform(post("/api/command-center/emergency-stop")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/command-center/parameter-settings")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/command-center/winch/tension-thresholds")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/command-center/exports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/user-info")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        verifyNoInteractions(lobby);
    }

    @Test void refreshTokenCannotReadNewPages() throws Exception {
        when(tokens.validateToken("refresh")).thenReturn(true);
        when(tokens.isRefreshToken("refresh")).thenReturn(true);
        mvc.perform(get("/api/command-center/home").param("systemCode", "SITE-01")
                .header("Authorization", "Bearer refresh")).andExpect(status().isUnauthorized());
        verifyNoInteractions(lobby);
    }

    @Test @WithMockUser(username = "alice")
    void homeUsesMappedPlcAndExplicitNullsInsteadOfFakeReadings() throws Exception {
        mvc.perform(get("/api/command-center/home").param("systemCode", "SITE-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.systemCode").value("SITE-01"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.systemCurrentHeight").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.sensors[0].sensorCode").value("SENSOR-A"))
                .andExpect(jsonPath("$.data.sensors[0].pressure").value(org.hamcrest.Matchers.nullValue()));
        verify(lobby).queryRealtime(argThat(context -> context.deviceId().equals("actual-plc")));
    }

    @Test @WithMockUser(username = "alice", roles = "ADMIN")
    void checksActualEnabledAccountAndCurrentPermissions() throws Exception {
        current.setStatus(0);
        mvc.perform(get("/api/command-center/home").param("systemCode", "SITE-01")).andExpect(status().isForbidden());
        verify(lobby, never()).queryRealtime(any());
        current.setStatus(1); current.setRoleCode("VIEWER");
        mvc.perform(post("/api/command-center/emergency-stop").contentType("application/json")
                .content("{\"systemCode\":\"SITE-01\",\"requestId\":\"request-1\"}"))
                .andExpect(status().isForbidden());
        verify(lobby, never()).emergencyStop(any());
    }

    @Test @WithMockUser(username = "alice")
    void acceptedEmergencyUsesLoggedInOperatorAndBusinessCode200() throws Exception {
        var now = Instant.now();
        when(lobby.emergencyStop(any())).thenReturn(new CleaningCommandStatusBo("request-1", "command-1", "actual-plc",
                "emergency_stop", CleaningCommandStatusBo.State.QUEUED, "QUEUED", "等待下发", now, now.plusSeconds(30), now));
        mvc.perform(post("/api/command-center/emergency-stop").contentType("application/json")
                .content("{\"systemCode\":\"SITE-01\",\"requestId\":\"request-1\",\"operatorId\":\"mallory\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.commandCode").value("command-1"))
                .andExpect(jsonPath("$.data.deviceExecutionConfirmed").value(false));
        verify(lobby).emergencyStop(argThat(request -> request.context().operatorId().equals("alice")
                && request.context().deviceId().equals("actual-plc")));
    }

    @Test @WithMockUser(username = "alice")
    void commandLookupUsesCommandCodeAndDoesNotReportWriteAckAsStopped() throws Exception {
        var now = Instant.now();
        when(lobby.queryOperationByCommandCode(any(), eq("command-1"))).thenReturn(Optional.of(
                new CleaningCommandStatusBo("request-1", "command-1", "actual-plc", "emergency_stop",
                        CleaningCommandStatusBo.State.SUCCEEDED, "OK", "写应答", now, now.plusSeconds(30), now)));
        mvc.perform(get("/api/command-center/commands/command-1").param("systemCode", "SITE-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phase").value("WRITE_CONFIRMED"))
                .andExpect(jsonPath("$.data.deviceExecutionConfirmed").value(false));
        verify(lobby, never()).queryOperation(any(), anyString());
    }

    @Test @WithMockUser(username = "alice")
    void currentFrontendQueriesBindButMissingBusinessReturnsExplicit503() throws Exception {
        mvc.perform(get("/api/command-center/winch/tension/trend").param("systemCode", "SITE-01")
                .param("winchCode", "WINCH-A").param("viewType", "FORCE_COMPARISON").param("comparisonView", "COUNT"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data.errorCode").value("HISTORY_NOT_READY"));
        mvc.perform(get("/api/command-center/history/detail").param("systemCode", "SITE-01")
                .param("recordCode", "RUN-1").param("yearGrouping", "MONTH").param("resolution", "RAW"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/command-center/history/list").param("systemCode", "SITE-01").param("scope", "RUN")
                .param("startTime", "2026-10-01T00:00:00+08:00").param("endTime", "2026-10-27T00:00:00+08:00")
                .param("endExclusive", "true").param("includeAvailableDates", "true"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/command-center/alarms/latest").param("systemCode", "SITE-01"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data.errorCode").value("EVENTS_NOT_READY"));
        mvc.perform(get("/api/command-center/alarms/history").param("systemCode", "SITE-01").param("levels", ""))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/command-center/exports").param("systemCode", "SITE-01"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data.errorCode").value("EXPORT_NOT_READY"));
    }

    @Test @WithMockUser(username = "alice")
    void rejectsMissingWriteIdentityAndOutOfBudgetWithoutSubmittingCommands() throws Exception {
        mvc.perform(post("/api/command-center/emergency-stop").contentType("application/json")
                .content("{\"systemCode\":\"SITE-01\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/command-center/history/detail").param("systemCode", "SITE-01").param("recordCode", "RUN-1")
                .param("maxPointsPerSeries", "2001")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/command-center/history/detail").param("systemCode", "SITE-01").param("recordCode", "RUN-1")
                .param("yearGrouping", "YEAR")).andExpect(status().isBadRequest());
        verify(lobby, never()).emergencyStop(any());
    }

    @Test @WithMockUser(username = "alice")
    void independentArchiveRangesAndLegacyRejectionAreBoundAtHttpBoundary() throws Exception {
        var query = get("/api/command-center/archive").param("systemCode", "SITE-01").param("winchCode", "WINCH-A")
                .param("metric", "SPEED").param("period", "WEEK").param("comparisonType", "SELECTED_PERIODS")
                .param("changeStartDate", "2026-10-01").param("changeEndDate", "2026-10-08")
                .param("currentStartDate", "2026-10-08").param("currentEndDate", "2026-10-15")
                .param("referenceStartDate", "2026-09-01").param("referenceEndDate", "2026-09-08");
        mvc.perform(query).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.errorCode").value("STATISTICS_NOT_READY"));
        mvc.perform(query.param("compareStart", "2026-10-01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("RANGE_INCOMPLETE"));
    }

    @Test @WithMockUser(username = "alice")
    void configurationsRemainUnconfirmedAndWritesDoNotReachPlc() throws Exception {
        mvc.perform(get("/api/command-center/parameter-settings").param("systemCode", "SITE-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.applyStatus").value("NOT_READY"))
                .andExpect(jsonPath("$.data.savedVersion").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(put("/api/command-center/parameter-settings").contentType("application/json")
                .content("{\"systemCode\":\"SITE-01\",\"requestId\":\"p-1\",\"expectedVersion\":\"1\",\"startTime\":\"08:00\",\"intervalMinutes\":30,\"speedMPerMinute\":5}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.errorCode").value("CONFIGURATION_NOT_READY"));
        mvc.perform(put("/api/command-center/winch/tension-thresholds").contentType("application/json")
                .content("{\"systemCode\":\"SITE-01\",\"winchCode\":\"WINCH-A\",\"requestId\":\"t-1\",\"expectedVersion\":\"1\",\"thresholds\":[{\"sensorCode\":\"SENSOR-A\",\"alarmThreshold\":0.8,\"safetyValue\":1.2},{\"sensorCode\":\"SENSOR-B\",\"alarmThreshold\":0.8,\"safetyValue\":1.2}]}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data.errorCode").value("THRESHOLD_MAPPING_NOT_READY"));
        verify(lobby, never()).updateSettings(any());
    }

    @Test @WithMockUser(username = "alice")
    void absentMappingIsReportedBeforeAccessingAnyPlc() throws Exception {
        when(lobby.pageSystem(any(), eq("SITE-01"), anyString()))
                .thenThrow(new PageApiException(503, "SYSTEM_MAPPING_NOT_READY", "尚未配置公开身份"));
        mvc.perform(get("/api/command-center/home").param("systemCode", "SITE-01"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data.errorCode").value("SYSTEM_MAPPING_NOT_READY"));
        verify(lobby, never()).queryRealtime(any());
    }

    @Test @WithMockUser(username = "alice")
    void unexpectedFailureKeepsPublicErrorContractWithoutDiagnosticDetails() throws Exception {
        when(lobby.queryRealtime(any())).thenThrow(new IllegalStateException("internal-test-diagnostic"));
        mvc.perform(get("/api/command-center/home").param("systemCode", "SITE-01"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.data.errorCode").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-test-diagnostic"))));
    }
}

package com.company.project.cleaning;

import com.company.project.info.thread.core.BackgroundWorkerRegistrar;
import com.company.project.presentation.web.CommandCenterPresentation;
import com.company.project.presentation.web.HomeStreamPresentation;
import com.company.project.vo.page.PageResponses.HomeSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class HomeStreamPresentationTest {
    @Test void repeatsFullSnapshotAndPreservesSamplingTimeWhileRecheckingAccess() throws Exception {
        var pages = mock(CommandCenterPresentation.class);
        var workers = mock(BackgroundWorkerRegistrar.class);
        when(workers.registerDedicatedPeriodic(anyString(), any(), eq(Duration.ofSeconds(1)))).thenReturn(true);
        var auth = UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of());
        var sampled = Instant.parse("2026-10-01T00:00:00Z");
        HomeSnapshot snapshot = HomeSnapshot.builder().systemCode("SYS-01").available(true).sequence("77")
                .updatedAt(sampled).asOf(Instant.now()).winches(List.of()).sensors(List.of())
                .faultSources(List.of()).notices(List.of()).build();
        when(pages.homeSnapshot("SYS-01", auth)).thenReturn(snapshot);
        var stream = new HomeStreamPresentation(pages, workers);
        var json = new ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var mvc = MockMvcBuilders.standaloneSetup(new TestController(stream, auth))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        stream.start();
        try {
            var result = mvc.perform(get("/test/home-stream")).andExpect(request().asyncStarted()).andReturn();
            stream.publishCycle();
            stream.publishCycle();
            String content = result.getResponse().getContentAsString();
            List<JsonNode> events = new ArrayList<>();
            for (String line : content.split("\\n")) {
                if (line.startsWith("data:")) events.add(json.readTree(line.substring(5)));
            }
            assertEquals(3, events.size(), "unchanged sampling sequence still needs full snapshots");
            assertEquals(3, content.split("event:home", -1).length - 1);
            assertTrue(content.contains("id:connection-local-1"));
            assertTrue(content.contains("id:connection-local-3"));
            for (JsonNode event : events) {
                assertEquals(200, event.path("code").asInt());
                assertEquals("77", event.path("data").path("sequence").asText());
                assertEquals(json.valueToTree(sampled), event.path("data").path("updatedAt"));
                assertFalse(event.path("data").path("pushedAt").isNull());
                assertTrue(event.path("data").path("winches").isArray());
            }
            assertNull(snapshot.pushedAt(), "transport does not mutate shared business snapshot");
            verify(pages, times(3)).homeSnapshot("SYS-01", auth);
            when(pages.homeSnapshot("SYS-01", auth)).thenThrow(new AccessDeniedException("disabled"));
            stream.publishCycle();
            stream.publishCycle();
            verify(pages, times(4)).homeSnapshot("SYS-01", auth);
        } finally {
            stream.stop();
        }
        verify(workers).unregister("command-center-home-push");
    }

    @Test void rejectsUnauthorizedSubscriptionBeforeOpeningSse() {
        var pages = mock(CommandCenterPresentation.class);
        var auth = UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of());
        when(pages.homeSnapshot("SYS-01", auth)).thenThrow(new AccessDeniedException("system denied"));
        var stream = new HomeStreamPresentation(pages, mock(BackgroundWorkerRegistrar.class));
        assertThrows(AccessDeniedException.class, () -> stream.subscribe("SYS-01", auth));
        stream.publishCycle();
        verify(pages).homeSnapshot("SYS-01", auth);
    }

    @RestController
    static class TestController {
        private final HomeStreamPresentation stream;
        private final Authentication auth;
        TestController(HomeStreamPresentation stream, Authentication auth) { this.stream = stream; this.auth = auth; }
        @GetMapping(value = "/test/home-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream() { return stream.subscribe("SYS-01", auth); }
    }
}

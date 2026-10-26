package com.company.project.presentation.web;

import com.company.project.domain.Result;
import com.company.project.domain.exception.PageApiException;
import com.company.project.info.thread.core.BackgroundWorkerRegistrar;
import com.company.project.vo.page.PageResponses.HomeSnapshot;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 新首页每秒推送完整视图；采集时刻不因重发而改变。 */
@Component
public class HomeStreamPresentation {
    private static final String WORKER = "command-center-home-push";
    private final CommandCenterPresentation pages;
    private final BackgroundWorkerRegistrar workers;
    private final Map<String, Connection> clients = new ConcurrentHashMap<>();
    private boolean stopping;

    public HomeStreamPresentation(CommandCenterPresentation pages, BackgroundWorkerRegistrar workers) {
        this.pages = pages;
        this.workers = workers;
    }

    @PostConstruct public void start() {
        if (!workers.registerDedicatedPeriodic(WORKER, this::publishCycle, Duration.ofSeconds(1)))
            throw new IllegalStateException("首页推送任务重复登记");
    }

    public SseEmitter subscribe(String systemCode, Authentication authentication) {
        HomeSnapshot initial = pages.homeSnapshot(systemCode, authentication);
        SseEmitter emitter = new SseEmitter(60_000L);
        Connection connection = new Connection(systemCode, authentication, emitter);
        String connectionId = UUID.randomUUID().toString();
        emitter.onCompletion(() -> clients.remove(connectionId));
        emitter.onTimeout(() -> clients.remove(connectionId));
        emitter.onError(error -> clients.remove(connectionId));
        // 首次发送与登记作为同一连接操作，周期线程不能先发更新后再补发较早的首帧。
        synchronized (connection) {
            synchronized (this) {
                if (stopping) throw new PageApiException(503, "SERVER_STOPPING", "服务正在停止");
                if (clients.size() >= 64) throw new PageApiException(429, "STREAM_CAPACITY_REACHED", "首页实时连接数已满");
                clients.put(connectionId, connection);
            }
            try {
                send(connection, initial);
            } catch (IOException | IllegalStateException failure) {
                clients.remove(connectionId);
                emitter.completeWithError(failure);
            }
        }
        return emitter;
    }

    public void publishCycle() {
        for (var entry : clients.entrySet()) {
            Connection connection = entry.getValue();
            try {
                if (connection.authentication.getDetails() instanceof Instant expires && !Instant.now().isBefore(expires)) {
                    clients.remove(entry.getKey());
                    connection.emitter.complete();
                    continue;
                }
                // 每次读取重新核对账号、权限和系统归属，不保存一份长期授权结论。
                send(connection, pages.homeSnapshot(connection.systemCode, connection.authentication));
            } catch (Exception failure) {
                clients.remove(entry.getKey());
                connection.emitter.complete();
            }
        }
    }

    private void send(Connection connection, HomeSnapshot snapshot) throws IOException {
        synchronized (connection) {
            connection.emitter.send(SseEmitter.event().name("home")
                    .id("connection-local-" + ++connection.sent)
                    .data(Result.success(snapshot.toBuilder().pushedAt(Instant.now()).build())));
        }
    }

    @PreDestroy public void stop() {
        synchronized (this) { stopping = true; }
        workers.unregister(WORKER);
        clients.values().forEach(connection -> connection.emitter.complete());
        clients.clear();
    }

    private static final class Connection {
        final String systemCode;
        final Authentication authentication;
        final SseEmitter emitter;
        long sent;
        Connection(String systemCode, Authentication authentication, SseEmitter emitter) {
            this.systemCode = systemCode;
            this.authentication = authentication;
            this.emitter = emitter;
        }
    }
}

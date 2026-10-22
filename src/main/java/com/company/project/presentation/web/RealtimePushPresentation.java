package com.company.project.presentation.web;
import com.company.project.domain.Result;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.info.thread.core.BackgroundWorkerRegistrar;
import com.company.project.service.CleaningLobbyService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** 网络推送只读取清洗服务快照；只维护连接与发送游标，不持有系统业务状态。 */
@Component
public class RealtimePushPresentation {
    private static final String WORKER="cleaning-realtime-push";
    private final CleaningLobbyService lobby;
    private final CleaningWebConverter converter;
    private final BackgroundWorkerRegistrar workers;
    private final long intervalMs;
    private final Map<String,Connection> clients=new ConcurrentHashMap<>();
    private boolean stopping;
    public RealtimePushPresentation(CleaningLobbyService lobby,CleaningWebConverter converter,
            BackgroundWorkerRegistrar workers,@Value("${cleaning.realtime.push-interval-ms:1000}") long intervalMs) {
        if(intervalMs<50)throw new IllegalArgumentException("推送间隔不能小于50ms");
        this.lobby=lobby;this.converter=converter;this.workers=workers;this.intervalMs=intervalMs;
    }
    @PostConstruct public void start(){
        if(!workers.registerDedicatedPeriodic(WORKER,this::publishCycle,Duration.ofMillis(intervalMs)))
            throw new IllegalStateException("实时推送任务重复登记");
    }
    public SseEmitter subscribe(String deviceId,Authentication auth) {
        var initial=lobby.queryRealtime(converter.context(deviceId,null,auth));
        var emitter=new SseEmitter(60_000L);
        var id=UUID.randomUUID().toString();var connection=new Connection(deviceId,auth,emitter);
        synchronized(this){
            if(stopping)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"服务正在停止");
            if(clients.size()>=64)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"实时连接数已满");
            clients.put(id,connection);
        }
        emitter.onCompletion(()->clients.remove(id));emitter.onTimeout(()->clients.remove(id));emitter.onError(error->clients.remove(id));
        try{send(connection,initial);}catch(IOException|IllegalStateException failure){clients.remove(id);emitter.completeWithError(failure);}
        return emitter;
    }
    public void publishCycle(){
        for(var entry:clients.entrySet()){
            var connection=entry.getValue();
            try{
                if(connection.auth.getDetails() instanceof Instant expires && !Instant.now().isBefore(expires)){
                    clients.remove(entry.getKey());connection.emitter.complete();continue;
                }
                // 持续校验真实账号状态和权限，不沿用建立连接时的角色快照。
                var sample=lobby.queryRealtime(converter.context(connection.deviceId,null,connection.auth));
                send(connection,sample);
            }catch(Exception failure){clients.remove(entry.getKey());connection.emitter.complete();}
        }
    }
    private void send(Connection connection,Optional<TelemetrySample> sample)throws IOException {
        synchronized(connection) {
            long sequence=sample.map(TelemetrySample::sequence).orElse(Long.MIN_VALUE);
            if(connection.sent && sequence<connection.sequence)return;
            if(!connection.sent || sequence!=connection.sequence) {
                var event=SseEmitter.event().name("realtime")
                        .data(Result.success(converter.realtimeVo(connection.deviceId,sample)));
                if(sample.isPresent())event.id(Long.toString(sequence));
                connection.emitter.send(event);
                connection.sequence=sequence;
                connection.sent=true;
            } else connection.emitter.send(SseEmitter.event().comment("keepalive"));
        }
    }
    @PreDestroy public void stop(){
        synchronized(this){stopping=true;}
        workers.unregister(WORKER);clients.values().forEach(c->c.emitter.complete());clients.clear();
    }
    private static final class Connection{
        final String deviceId;final Authentication auth;final SseEmitter emitter;long sequence;boolean sent;
        Connection(String deviceId,Authentication auth,SseEmitter emitter){this.deviceId=deviceId;this.auth=auth;this.emitter=emitter;}
    }
}

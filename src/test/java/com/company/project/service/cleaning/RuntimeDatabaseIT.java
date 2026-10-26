package com.company.project.service.cleaning;

import com.company.project.config.PageAccessProperties;
import com.company.project.domain.bo.*;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import com.company.project.info.buffer.cleaning.*;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.repository.cleaning.*;
import com.company.project.service.statistics.CleaningStatisticsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 仅由verify_runtime_flow.py启动，写入前核对自有实例的端口、目录及所有者标记。 */
@EnabledIfSystemProperty(named="runtime.it.url",matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/fresh_db\\?.*")
class RuntimeDatabaseIT {
    @TempDir Path temporary;
    @Configuration @EnableTransactionManagement static class Transactions { }
    @Test void captureJournalTransactionQueriesPeriodsConfigurationAndAuditWorkTogether() throws Exception {
        Path owned=Path.of(System.getProperty("runtime.it.datadir")).toRealPath();
        Path allowed=Path.of("archive/database-validation").toAbsolutePath().normalize();
        assertTrue(owned.startsWith(allowed));
        assertEquals(System.getProperty("runtime.it.owner"),Files.readString(owned.getParent().resolve(".owner-token")));
        var ds=new DriverManagerDataSource(System.getProperty("runtime.it.url"),"root","");
        var jdbc=new JdbcTemplate(ds);
        assertEquals(owned,Path.of(jdbc.queryForObject("SELECT @@datadir",String.class)).toRealPath());
        var mapper=new ObjectMapper().findAndRegisterModules();
        var mappings=new PageAccessProperties();
        var system=new PageAccessProperties.SystemMapping();
        system.setSystemCode("SYS-IT");system.setDeviceId("plc");system.setSystemName("集成测试系统");
        for(int i=1;i<=4;i++) {
            var w=new PageAccessProperties.WinchMapping();w.setWinchId("W"+i);w.setWinchCode("WJ-"+i);w.setGroupNo(i);
            for(int j=1;j<=2;j++) {
                var s=new PageAccessProperties.SensorMapping();String id="S"+((i-1)*2+j);
                s.setSensorId(id);s.setSensorCode(id);s.setRopeNo(j);w.getSensors().add(s);
            }
            system.getWinches().add(w);
        }
        mappings.getSystems().add(system);
        jdbc.update("INSERT INTO t_user(username,password,role_code) VALUES('runtime-it','test-only','ADMIN')");
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(Transactions.class);
            context.registerBean(PlatformTransactionManager.class,()->new DataSourceTransactionManager(ds));
            context.registerBean(RuntimeHistoryDao.class,()->new JdbcRuntimeHistoryDao(new NamedParameterJdbcTemplate(ds),mapper,mappings));
            context.refresh();
            var dao=context.getBean(RuntimeHistoryDao.class);
            assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(dao));
            try(var inbox=new DurableHistoryInbox(temporary.toString(),mapper)) {
                var beans=new DefaultListableBeanFactory();beans.registerSingleton("dao",dao);beans.registerSingleton("inbox",inbox);
                var messages=new PlcMessageBuffer();
                try(var statistics=new CleaningStatisticsServiceImpl(new TelemetryHistoryBuffer(),mock(TelemetryHistoryDao.class),
                        beans.getBeanProvider(RuntimeHistoryDao.class),beans.getBeanProvider(DurableHistoryInbox.class),messages,
                        new com.company.project.repository.device.PageSystemRegistry(mappings),temporary.resolve("exports").toString())) {
                statistics.initialize();
                var tracker=new RunHistoryTracker();var converter=new CleaningDeviceConverter();
                TelemetrySample previous=null;List<Write> writes=new ArrayList<>();
                for(int i=1;i<=5;i++) {
                    var raw=RunHistoryTrackerTest.raw(i,true,i==1||i==5?0:1,i==3?1:0,0,i==2?"0.04":i==3?"0.54":i==4?"1.04":"0",1,i==3?41:0);
                    var sample=converter.toRealtime(raw,previous);previous=sample;
                    tracker.accept(raw,sample,null,Map.of(),w->{writes.add(w);statistics.acceptRuntimeWrite(w);});
                }
                assertEquals(5,inbox.pendingCount());
                statistics.runPersistenceCycle();assertEquals(0,inbox.pendingCount());
                var last=writes.get(4);dao.save(last);
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_run_record",Integer.class));
                Run run=statistics.history("plc").runs(Instant.EPOCH,Instant.now().plusSeconds(1),null,0,10).entries().get(0);
                assertEquals("ABNORMAL_COMPLETED",run.result());
                assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_up_sample",Integer.class));
                assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_up_sample WHERE w1_winch_id='W1' AND w1_up_value IS NOT NULL AND system_state_code IS NOT NULL",Integer.class));
                assertNotNull(jdbc.queryForObject("SELECT system_force_max_at FROM cleaning_run_record",java.time.LocalDateTime.class));
                assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_run_winch_day",Integer.class));
                assertEquals(0,new BigDecimal("836").compareTo(run.measures().get("SYSTEM/ROPE_TOTAL").average()));
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_event_run",Integer.class));
                Event event=statistics.history("plc").events(Instant.EPOCH,Instant.now().plusSeconds(1),true,2,run.recordCode(),0,10).entries().get(0);
                assertEquals("SERIOUS",event.severity());assertEquals(2,event.winchSlot());assertNotNull(event.endedAt());
                var frames=statistics.history("plc").frames(run.recordCode(),0,10);
                assertEquals(0,new BigDecimal("0.54").compareTo(frames.get(1).sample().winches().get(0).heightMeters()));
                assertEquals(frames.get(1),statistics.history("plc").frame(run.recordCode(),frames.get(1).sampleKey()).orElseThrow());
                long sequence=jdbc.queryForObject("SELECT summary_version FROM cleaning_run_record",Long.class);
                var invalid=new SavedFrame("invalid",run.recordCode(),100,run.observedThrough(),
                        Map.of(5,new Selection(BigDecimal.ONE,true,UUID.randomUUID().toString(),1)),frames.get(0).raw(),frames.get(0).sample());
                var changed=new Run(run.recordCode(),run.deviceId(),run.startedAt(),run.endedAt(),run.observedThrough(),"ENDED",run.result(),
                        run.endReason(),true,false,100,run.measures(),run.motionMillis(),run.eventCodes(),run.configuration());
                assertThrows(org.springframework.dao.DataAccessException.class,()->dao.save(new Write(last.captureId(),"plc",100,
                        run.observedThrough(),last.latest(),changed,invalid,List.of())));
                assertEquals(sequence,jdbc.queryForObject("SELECT summary_version FROM cleaning_run_record",Long.class));
                assertEquals(5,jdbc.queryForObject("SELECT MAX(last_sequence) FROM cleaning_runtime_checkpoint",Long.class));
                statistics.runPeriodCycle();
                var date=run.startedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
                Instant day=date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
                var periods=statistics.history("plc").periods("DAY",day,day.plusSeconds(86400),null);
                assertEquals(1,periods.size());assertEquals(1,periods.get(0).abnormalCompleted());
                assertEquals(4,jdbc.queryForObject("SELECT COUNT(DISTINCT period_type) FROM cleaning_height_bucket WHERE period_type<>'DAY'",Integer.class));
                var heights=statistics.history("plc").heights("DAY",day,null);
                assertEquals(3,heights.size());assertTrue(heights.stream().allMatch(h->h.contributingRuns()==1));
                assertTrue(dao.pendingPeriods(20).isEmpty());
                assertEquals(3,statistics.history("plc").runHeights(run.recordCode(),null).size());
                assertNotNull(jdbc.queryForObject("SELECT height_snapshot FROM cleaning_run_record",String.class));
                assertTrue(statistics.history("plc").archive("W1","ROPE1",day,day.plusSeconds(86400),"DAY").ready());
                var settings=new CleaningSettingsBo(LocalTime.of(8,0),1800L,BigDecimal.TEN);
                Instant now=Instant.now();
                var command=new CleaningCommand(UUID.randomUUID().toString(),"plc","update_cleaning_parameters",
                        Map.of("settings",settings,"expectedVersion",0),now,"config-it","runtime-it",now.plusSeconds(30));
                assertEquals(1,dao.saveSettings(command,0,settings).savedVersion());
                assertEquals(1,dao.saveSettings(command,0,settings).savedVersion());
                assertEquals("SAVED",dao.settings("plc").state());assertNull(dao.settings("plc").appliedVersion());
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_operation_audit",Integer.class));
                var conflicting=new CleaningCommand(UUID.randomUUID().toString(),"plc","update_cleaning_parameters",
                        Map.of("settings",settings,"expectedVersion",0),now,"other-it","runtime-it",now.plusSeconds(30));
                assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->dao.saveSettings(conflicting,0,settings));
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM cleaning_operation_audit",Integer.class));
                assertNotNull(statistics.restoredState("plc").orElse(null)==null?dao.lastState("plc").orElseThrow():statistics.restoredState("plc").get());
                var ctx=new com.company.project.domain.param.cleaning.CleaningOperationContext("export-it","plc","runtime-it",Set.of("device:read","alarm:read"));
                var exports=statistics.exports(ctx);
                List<ExportTaskBo> tasks=new ArrayList<>();
                for(String type:List.of("RUN_LIST","RUN_SAMPLES","EXCEPTIONS","PERIOD_STATISTICS")) {
                    boolean period="PERIOD_STATISTICS".equals(type),events="EXCEPTIONS".equals(type);
                    var selection=new ExportTaskBo.Selection(null,null,period||events?null:run.recordCode(),List.of(),events?List.of(event.eventCode()):List.of(),
                            null,null,null,null,null,null,null,null,period?"DAY":null,period?date:null,period?date.plusDays(1):null,null,null);
                    var task=exports.create("export-"+type,type,"t",selection);tasks.add(task);
                    assertEquals(task.taskCode(),exports.create("export-"+type,type,"t",selection).taskCode());
                }
                for(int i=0;i<4;i++)statistics.runExportCycle();
                for(var requested:tasks) {
                    var done=exports.task(requested.taskCode());assertEquals("SUCCESS",done.state(),done.errorMessage());
                    assertTrue(done.outputRows()>0);assertEquals(1,done.files().size());
                    var download=exports.download(done.taskCode(),done.files().get(0).fileCode());assertTrue(Files.size(download.path())>0);
                    if("PERIOD_STATISTICS".equals(done.exportType()))try(var zip=new java.util.zip.ZipFile(download.path().toFile())) {
                        assertEquals(2,zip.size());assertTrue(done.tableCounts().stream().allMatch(t->t.rows()>0));
                    } else assertTrue(Files.readString(download.path()).contains("SYS-IT"));
                }
                var other=new com.company.project.domain.param.cleaning.CleaningOperationContext(null,"plc","other-user",Set.of("device:read"));
                assertThrows(com.company.project.domain.exception.PageApiException.class,()->statistics.exports(other).task(tasks.get(0).taskCode()));
                }
            }
        }
        verifyApplicationStartupAndHttp(ds,jdbc,mappings);
    }
    private void verifyApplicationStartupAndHttp(DriverManagerDataSource ds,JdbcTemplate jdbc,PageAccessProperties mappings)throws Exception {
        List<String> args=new ArrayList<>(List.of("--spring.profiles.active=runtime-it","--spring.config.location=classpath:/application.yml",
                "--server.address=127.0.0.1","--server.port=0","--spring.datasource.url="+ds.getUrl(),"--spring.datasource.username=root","--spring.datasource.password=",
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver","--jwt.secret=runtime-integration-only-secret-32-bytes-minimum",
                "--jwt.expiration=2592000000","--cleaning.plc.enabled=false","--cleaning.history.persistence-enabled=false","--cleaning.history.runtime-enabled=true",
                "--cleaning.history.spool-directory="+temporary.resolve("application-inbox"),"--cleaning.exports.directory="+temporary.resolve("application-exports")));
        var mapping=mappings.getSystems().get(0);String base="--cleaning.page.systems[0].";
        args.add(base+"system-code="+mapping.getSystemCode());args.add(base+"system-name=Runtime IT");args.add(base+"device-id=plc");
        args.add(base+"allowed-usernames[0]=runtime-it");
        for(int i=0;i<4;i++) {
            var w=mapping.getWinches().get(i);String path=base+"winches["+i+"].";
            args.add(path+"winch-id="+w.getWinchId());args.add(path+"winch-code="+w.getWinchCode());args.add(path+"group-no="+w.getGroupNo());
            for(int j=0;j<2;j++) {
                var s=w.getSensors().get(j);String sensor=path+"sensors["+j+"].";
                args.add(sensor+"sensor-id="+s.getSensorId());args.add(sensor+"sensor-code="+s.getSensorCode());args.add(sensor+"rope-no="+s.getRopeNo());
            }
        }
        try(var app=new org.springframework.boot.builder.SpringApplicationBuilder(com.company.project.Application.class).run(args.toArray(String[]::new))) {
            int port=((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)app).getWebServer().getPort();
            var tokens=app.getBean(com.company.project.security.JwtTokenProvider.class);
            String token=tokens.generateToken("runtime-it","ADMIN");
            var client=java.net.http.HttpClient.newHttpClient();var mapper=app.getBean(ObjectMapper.class);
            java.util.function.Function<String,com.fasterxml.jackson.databind.JsonNode> get=path->{
                try {
                    var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+path))
                            .timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                    assertEquals(200,response.statusCode(),response.body());return mapper.readTree(response.body()).path("data");
                }catch(Exception failure){throw new IllegalStateException(failure);}
            };
            assertFalse(get.apply("/api/command-center/home?systemCode=SYS-IT").path("online").asBoolean());
            var lobby=app.getBean(com.company.project.service.CleaningLobbyService.class);
            lobby.acceptDeviceSnapshot(RunHistoryTrackerTest.raw(1,true,0,0,0,"0",2,0));
            assertTrue(get.apply("/api/command-center/home?systemCode=SYS-IT").path("online").asBoolean());
            var list=get.apply("/api/command-center/history/list?systemCode=SYS-IT&scope=RUN&startTime=2026-10-01T00:00:00Z&endTime=2026-11-01T00:00:00Z&endExclusive=true");
            assertEquals(1,list.path("total").asInt());String record=list.path("entries").get(0).path("id").asText();
            assertEquals(record,get.apply("/api/command-center/history/detail?systemCode=SYS-IT&recordCode="+record).path("entry").path("id").asText());
            assertTrue(get.apply("/api/command-center/winch/tension/trend?systemCode=SYS-IT&winchCode=WJ-1&viewType=FORCE_COMPARISON&comparisonView=MONTH&periodStartDate=2026-10-01").path("series").size()>0);
            assertTrue(get.apply("/api/command-center/archive?systemCode=SYS-IT&winchCode=WJ-1&metric=TENSION&tensionMetric=TOTAL_TENSION&period=MONTH&comparisonType=PREVIOUS_RUN&recordCode="+record+"&changeStartDate=2026-10-26&changeEndDate=2026-10-28").has("summary"));
            assertEquals("SAVED",get.apply("/api/command-center/parameter-settings?systemCode=SYS-IT").path("applyStatus").asText());
            var unauth=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/admin/user/page")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(401,unauth.statusCode());
        }
    }
}

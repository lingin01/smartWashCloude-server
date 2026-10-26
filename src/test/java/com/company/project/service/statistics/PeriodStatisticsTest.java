package com.company.project.service.statistics;

import com.company.project.domain.bo.*;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo;
import com.company.project.repository.cleaning.RuntimeHistoryDao;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PeriodStatisticsTest {
    private static final Instant AT=Instant.parse("2026-10-26T00:00:00Z");
    private static SavedFrame frame(String run,int sequence,int force,boolean fourth) {
        List<TelemetrySample.WinchSample> winches=new ArrayList<>();
        List<TelemetrySample.RopeSample> sensors=new ArrayList<>();
        Map<Integer,Selection> nodes=new LinkedHashMap<>();
        for(int i=1;i<=4;i++) {
            winches.add(new TelemetrySample.WinchSample("W"+i,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.valueOf(force),true,true,"",BigDecimal.TEN,true,true,true,"RUNNING","UP",2));
            for(int j=1;j<=2;j++)sensors.add(new TelemetrySample.RopeSample("S"+i+j,"S"+i+j,"W"+i,BigDecimal.valueOf(force),true));
            if(i<4||fourth)nodes.put(i,new Selection(BigDecimal.ONE,true,UUID.randomUUID().toString(),1));
        }
        var sample=new TelemetrySample("plc",sequence,AT.plusSeconds(sequence),sensors,winches,null,null,null,null,
                new SystemRealtimeStatusBo(SystemRealtimeStatusBo.Status.RUNNING,true,true,false,"","UP"),null,false,8,4,null);
        return new SavedFrame(run+sequence,run,sequence,sample.sampledAt(),nodes,new PlcDeviceSnapshotBo("plc",true,sequence,sample.sampledAt(),List.of()),sample);
    }
    @Test void eachRunGetsOneHeightVoteAndSystemUsesFourCompleteDevices() {
        var engine=new PeriodStatistics(mock(RuntimeHistoryDao.class));
        var work=new RuntimeHistoryDao.PeriodWork("SYSTEM","DAY",AT,AT.plusSeconds(86400),1);
        var output=engine.dayHeights(work,List.of(frame("A",1,100,true),frame("A",2,300,true),frame("B",3,1000,true)));
        var sensor=output.stream().filter(h->h.scopeId().equals("W1")&&h.source().equals("ROPE1")).findFirst().orElseThrow();
        assertEquals(0,new BigDecimal("600").compareTo(sensor.value().average()));
        assertEquals(2,sensor.contributingRuns());assertEquals(2,sensor.value().count());
        assertEquals(0,new BigDecimal("100").compareTo(sensor.value().minimum()));
        var system=output.stream().filter(h->h.scopeId().equals("SYSTEM")).findFirst().orElseThrow();
        assertEquals(0,new BigDecimal("4800").compareTo(system.value().average()));
        var missing=engine.dayHeights(work,List.of(frame("A",1,100,true),frame("B",2,1000,false)));
        var onlyComplete=missing.stream().filter(h->h.scopeId().equals("SYSTEM")).findFirst().orElseThrow();
        assertEquals(1,onlyComplete.contributingRuns());assertEquals(0,new BigDecimal("800").compareTo(onlyComplete.value().average()));
    }
    @Test void mergingPeriodSourcesUsesSumsAndCountsInsteadOfAveragesOfAverages() {
        var a=new Measure(new BigDecimal("400"),2,new BigDecimal("100"),new BigDecimal("300"),AT,AT,null,null);
        var b=new Measure(new BigDecimal("1000"),1,new BigDecimal("1000"),new BigDecimal("1000"),AT,AT,null,null);
        var merged=PeriodStatistics.merge(a,b);
        assertEquals(3,merged.count());assertEquals(new BigDecimal("466.666667"),merged.average());
    }
}

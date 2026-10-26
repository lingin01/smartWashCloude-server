package com.company.project.service.cleaning;

import com.company.project.config.PlcAccessProperties;
import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RunHistoryTrackerTest {
    private static final Instant TIME=Instant.parse("2026-10-26T15:59:58Z");
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private static PlcDeviceSnapshotBo.Point point(String field,int index,String value) {
        int slot=field.equals("ropeTension")?(index-1)/2+1:index;
        return new PlcDeviceSnapshotBo.Point(field+index,field,index,field.equals("ropeTension")?"S"+index:"",
                field.equals("ropeTension")?"S"+index:"","W"+slot,field.equals("ropeTension")||field.equals("winchTorque")?"kg":"",
                BigDecimal.ONE,n(value),true);
    }
    static PlcDeviceSnapshotBo raw(long seq,boolean online,int active,int abnormal,int end,String height,int state,int fault) {
        List<PlcDeviceSnapshotBo.Point> points=new ArrayList<>();
        points.add(point("systemState",1,Integer.toString(state)));
        points.add(point("automaticCycleActive",1,Integer.toString(active)));
        points.add(point("cycleAbnormal",1,Integer.toString(abnormal)));
        points.add(point("cycleAbnormalEnd",1,Integer.toString(end)));
        points.add(point("faultCode",1,Integer.toString(fault)));
        for(int i=1;i<=4;i++) {
            points.add(point("winchState",i,"1"));points.add(point("winchUp",i,active==1?"1":"0"));
            points.add(point("winchDown",i,"0"));points.add(point("winchHeight",i,height));
            points.add(point("axisRunSeconds",i,Long.toString(seq)));
            points.add(point("winchTorque",i,"80"));
        }
        for(int i=1;i<=8;i++)points.add(point("ropeTension",i,Integer.toString(100+i)));
        return new PlcDeviceSnapshotBo("plc",online,seq,TIME.plusSeconds(seq),points);
    }
    private static final class Flow {
        final RunHistoryTracker tracker=new RunHistoryTracker();
        final CleaningDeviceConverter converter=new CleaningDeviceConverter();
        final List<Write> writes=new ArrayList<>();
        TelemetrySample previous;
        void accept(long seq,boolean online,int active,int abnormal,int end,String height,int state,int fault) {
            var raw=raw(seq,online,active,abnormal,end,height,state,fault);
            var sample=converter.toRealtime(raw,previous);
            tracker.accept(raw,sample,null,Map.of(),writes::add);previous=sample;
        }
        void accept(long seq,int active,int abnormal,int end,String height) {accept(seq,true,active,abnormal,end,height,1,0);}
        Run last(){return writes.stream().map(Write::run).filter(Objects::nonNull).reduce((a,b)->b).orElseThrow();}
    }
    @Test void oneM106RoundHasOneIdentityAndBothBoundaryFramesContribute() {
        Flow f=new Flow();f.accept(1,0,0,0,"0");f.accept(2,1,0,0,"0.04");
        f.accept(3,1,0,0,"0.54");f.accept(4,0,0,0,"1");
        assertEquals("NORMAL_COMPLETED",f.last().result());
        assertEquals(1,f.writes.stream().map(Write::run).filter(Objects::nonNull).map(Run::recordCode).distinct().count());
        var frame=f.writes.get(2).frame();
        assertEquals(0,n("0.5").compareTo(frame.selections().get(1).targetHeight()));
        assertEquals(0,n("0.54").compareTo(frame.sample().winches().get(0).heightMeters()));
    }
    @Test void abnormalLatchSurvivesResetAndAbnormalEndHasPriority() {
        Flow f=new Flow();f.accept(1,0,0,0,"0");f.accept(2,1,1,0,"0");
        f.accept(3,1,0,0,"0.5");f.accept(4,0,0,0,"1");
        assertEquals("ABNORMAL_COMPLETED",f.last().result());
        Flow g=new Flow();g.accept(1,0,0,0,"0");g.accept(2,1,1,1,"0");
        g.accept(3,1,0,0,"0.5");g.accept(4,0,0,0,"1");
        assertEquals("ABNORMAL_ENDED",g.last().result());
    }
    @Test void endingBatchAbnormalBitIsObservedBeforeClosing() {
        Flow f=new Flow();f.accept(1,0,0,0,"0");f.accept(2,1,0,0,"0");f.accept(3,0,0,1,"1");
        assertEquals("ABNORMAL_ENDED",f.last().result());
        f.accept(4,0,0,0,"1");assertNull(f.writes.get(3).run());
    }
    @Test void firstContactAndReconnectDuringActiveRoundDoNotInventOrResumeRuns() {
        Flow f=new Flow();f.accept(1,1,0,0,"4");assertNull(f.writes.get(0).run());
        f.accept(2,0,0,0,"0");f.accept(3,1,0,0,"0.5");
        String first=f.last().recordCode();
        f.accept(4,false,1,0,0,"1",1,0);assertEquals("ABNORMAL_ENDED",f.last().result());
        f.accept(5,1,0,0,"2");assertNull(f.writes.get(4).run());
        f.accept(6,0,0,0,"0");f.accept(7,1,0,0,"0");
        assertNotEquals(first,f.last().recordCode());
    }
    @Test void maintenanceHasRealtimeOnlyAndPersistsWhenDisconnected() {
        Flow f=new Flow();f.accept(1,true,0,0,0,"0",3,0);f.accept(2,true,1,0,0,"1",3,0);
        assertTrue(f.writes.stream().allMatch(w->w.run()==null&&w.frame()==null));
        f.accept(3,false,0,0,0,"1",3,0);
        assertEquals(SystemRealtimeStatusBo.Status.MAINTENANCE,f.previous.status().systemStatus());
        assertFalse(f.previous.status().online());
    }
    @Test void persistentPlcFaultIsSeriousAndNotAnAutomaticRoundEnd() {
        Flow f=new Flow();f.accept(1,true,0,0,0,"0",1,50);
        f.accept(2,true,1,1,0,"0",1,50);assertEquals("RUNNING",f.last().state());
        f.accept(3,true,0,0,1,"1",1,50);
        f.accept(4,true,1,1,0,"0",1,50);
        assertEquals(1,f.writes.stream().flatMap(w->w.events().stream()).map(Event::eventCode).distinct().count());
        assertEquals("SERIOUS",f.writes.get(0).events().get(0).severity());
    }
    @Test void encoderFormulaIsContinuousAndLocalSpeedUsesTimerDeltas() {
        assertEquals(0,n("12.516").compareTo(CleaningDeviceConverter.height(n("98314"),n("10"))));
        assertEquals(0,n("25.7988").compareTo(CleaningDeviceConverter.height(n("196618"),n("10"))));
        Flow f=new Flow();f.accept(1,0,0,0,"0");f.accept(2,1,0,0,"10");
        f.accept(3,1,0,0,"10.1");
        assertEquals(0,n("6").compareTo(f.previous.winches().get(0).speed()));
        assertEquals(0,n("203").compareTo(f.previous.winches().get(0).totalTension()));
        assertEquals(0,n("836").compareTo(f.previous.systemTotalTension()));
    }
    @Test void failedInboxAppendRetriesIdenticalMutationAndOldTimestampsAreAccepted() {
        var tracker=new RunHistoryTracker();var converter=new CleaningDeviceConverter();
        var zero=raw(1,true,0,0,0,"0",1,0);tracker.accept(zero,converter.toRealtime(zero,null),null,Map.of(),w->{});
        var begin=raw(2,true,1,0,0,"0",1,0);var sample=converter.toRealtime(begin,null);
        List<Write> attempts=new ArrayList<>();
        assertThrows(IllegalStateException.class,()->tracker.accept(begin,sample,null,Map.of(),w->{attempts.add(w);throw new IllegalStateException("disk");}));
        tracker.accept(begin,sample,null,Map.of(),attempts::add);
        assertSame(attempts.get(0),attempts.get(1));
        assertSame(sample,attempts.get(1).latest());
    }
    @Test void restoringStateRetainsMaintenanceButNeverClaimsConnectionAndAcceptsNewSequenceOrigin() {
        var store=new CleaningSystemState();var converter=new CleaningDeviceConverter();
        store.restore(converter.toRealtime(raw(999,true,0,0,0,"0",3,0),null));
        assertEquals(SystemRealtimeStatusBo.Status.MAINTENANCE,store.latest("plc").orElseThrow().status().systemStatus());
        assertFalse(store.latest("plc").orElseThrow().status().online());
        var current=converter.toRealtime(raw(1,true,0,0,0,"0",2,0),store.latest("plc").orElseThrow());
        assertTrue(store.update(current));assertTrue(store.latest("plc").orElseThrow().status().online());
    }
}

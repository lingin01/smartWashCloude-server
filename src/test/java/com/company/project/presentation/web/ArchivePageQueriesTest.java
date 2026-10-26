package com.company.project.presentation.web;

import com.company.project.domain.bo.page.PageSystemBo;
import com.company.project.domain.bo.telemetry.*;
import com.company.project.domain.bo.telemetry.RunHistoryBo.*;
import com.company.project.domain.param.cleaning.CleaningOperationContext;
import com.company.project.dto.page.PageRequests.ArchiveQuery;
import com.company.project.service.CleaningLobbyService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArchivePageQueriesTest {
    @Test void pagesDoNotTruncateSummaryAndComparisonKeepsIndependentRanges() {
        var lobby=mock(CleaningLobbyService.class);var access=mock(PageAccess.class);var reader=mock(HistoryReader.class);
        var w=new PageSystemBo.Winch("WJ1","一号","W1",1,List.of());
        var system=new PageSystemBo("SYS","系统","plc",List.of(w),Set.of());
        var context=new CleaningOperationContext(null,"plc","user",Set.of("device:read"));
        when(access.resolve("SYS",null,"device:read",null)).thenReturn(new PageAccess.Resolved(system,context));
        when(access.winch(system,"WJ1")).thenReturn(w);when(lobby.history(context)).thenReturn(reader);
        var current=new Measure(new BigDecimal("6000"),3,new BigDecimal("1000"),new BigDecimal("3000"),null,null,null,null);
        var reference=new Measure(new BigDecimal("3000"),3,new BigDecimal("500"),new BigDecimal("1500"),null,null,null,null);
        var d=LocalDate.of(2026,10,1);var zone=ZoneId.of("Asia/Shanghai");
        var points=List.of(new ArchiveMetricBo.Point("2026-10-01",d.atStartOfDay(zone).toInstant(),current),
                new ArchiveMetricBo.Point("2026-10-02",d.plusDays(1).atStartOfDay(zone).toInstant(),current));
        when(reader.archive(eq("W1"),eq("ROPE_TOTAL"),any(),any(),eq("DAY")))
                .thenReturn(new ArchiveMetricBo(points,current,true),new ArchiveMetricBo(points,current,true),new ArchiveMetricBo(List.of(),reference,true));
        var q=new ArchiveQuery();q.setSystemCode("SYS");q.setWinchCode("WJ1");q.setMetric("TENSION");q.setTensionMetric("TOTAL_TENSION");q.setPeriod("MONTH");
        q.setComparisonType("SELECTED_PERIODS");q.setChangeStartDate(d);q.setChangeEndDate(d.plusDays(2));
        q.setCurrentStartDate(d);q.setCurrentEndDate(d.plusDays(2));q.setReferenceStartDate(d.minusMonths(1));q.setReferenceEndDate(d.minusMonths(1).plusDays(2));q.setMaxPointsPerSeries(1);
        var response=new ArchivePageQueries(lobby,access).query(q,null);
        assertEquals(1,response.changePoints().size());assertFalse(response.complete());assertNotNull(response.nextCursor());
        assertEquals(0,new BigDecimal("2").compareTo(response.summary().average()));assertEquals(3,response.summary().currentStatistics().count());
        assertEquals(0,new BigDecimal("100").compareTo(response.summary().changeRate()));
        assertEquals(d.minusMonths(1),response.effectiveRanges().reference().startDate());
        q.setCursor(response.nextCursor());q.setTensionMetric("WINCH_TENSION");
        when(reader.archive(eq("W1"),eq("ROPE1"),any(),any(),eq("DAY"))).thenReturn(new ArchiveMetricBo(List.of(),current,true));
        assertThrows(com.company.project.domain.exception.PageApiException.class,()->new ArchivePageQueries(lobby,access).query(q,null));
    }
}

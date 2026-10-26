package com.company.project.page;

import com.company.project.dto.page.PageRequests.*;
import com.company.project.vo.page.PageResponses;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 筛选口径与字段缺失不能静默变成另一种查询或伪造读数。 */
class PageRequestContractTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test void trendDoesNotRequireDatesOrLocalSourceSelectionsButLimitsEachResponse() {
        var query = new WinchTrendQuery();
        query.setSystemCode("SYS");
        query.setWinchCode("WINCH");
        query.setViewType("FORCE_COMPARISON");
        query.setComparisonView("COUNT");
        assertTrue(VALIDATOR.validate(query).isEmpty());
        assertEquals("OVERVIEW", query.getResolution());
        assertEquals(5, query.getUnitPageSize());
        assertEquals(500, query.getMaxPointsPerSeries());
        query.setMaxPointsPerSeries(2001);
        assertFalse(VALIDATOR.validate(query).isEmpty());
    }

    @Test void omittedAndExplicitlyEmptyAlarmLevelsRemainDifferent() {
        var query = new AlarmHistoryQuery();
        query.setSystemCode("SYS");
        assertEquals("NORMAL,SERIOUS", query.getLevels());
        query.setLevels("");
        assertTrue(VALIDATOR.validate(query).isEmpty());
        assertEquals("", query.getLevels());
        query.setLevels("MEDIUM");
        assertFalse(VALIDATOR.validate(query).isEmpty());
    }

    @Test void alarmCustomDatesAreInclusiveButArchiveEndIsExclusive() {
        var alarms = new AlarmHistoryQuery();
        alarms.setSystemCode("SYS");
        alarms.setRange("CUSTOM");
        alarms.setStartDate(LocalDate.of(2026, 10, 26));
        alarms.setEndDate(alarms.getStartDate());
        assertTrue(VALIDATOR.validate(alarms).isEmpty());
        alarms.setEndDate(alarms.getStartDate().plusDays(366));
        assertFalse(VALIDATOR.validate(alarms).isEmpty());

        var archive = archive();
        archive.setChangeEndDate(archive.getChangeStartDate());
        assertFalse(VALIDATOR.validate(archive).isEmpty());
    }

    @Test void archiveKeepsIndependentRangesAndRejectsMixedLegacyAliases() {
        var query = archive();
        assertTrue(VALIDATOR.validate(query).isEmpty());
        query.setComparisonType("SELECTED_PERIODS");
        query.setRecordCode(null);
        assertFalse(VALIDATOR.validate(query).isEmpty());
        query.setCurrentStartDate(LocalDate.of(2026, 9, 1));
        query.setCurrentEndDate(LocalDate.of(2026, 10, 1));
        query.setReferenceStartDate(LocalDate.of(2025, 9, 1));
        query.setReferenceEndDate(LocalDate.of(2025, 10, 1));
        assertTrue(VALIDATOR.validate(query).isEmpty());
        assertNotEquals(query.getChangeStartDate(), query.getCurrentStartDate());
        query.setCompareStart("2026-01-01");
        assertFalse(VALIDATOR.validate(query).isEmpty());
    }

    @Test void historyQueryChecksBoundsWithoutRejectingAnOldQuery() {
        var query = new RunListQuery();
        query.setSystemCode("SYS");
        query.setScope("RUN");
        query.setStartTime(OffsetDateTime.parse("2000-01-01T00:00:00+08:00"));
        query.setEndTime(OffsetDateTime.parse("2000-01-02T00:00:00+08:00"));
        query.setEndExclusive(true);
        assertTrue(VALIDATOR.validate(query).isEmpty());
        query.setEndExclusive(false);
        assertFalse(VALIDATOR.validate(query).isEmpty());
    }

    @Test void thresholdsRequireTwoDifferentChannelsAndSafeOrdering() {
        var first = new ThresholdValue("S1", new BigDecimal("0.8"), new BigDecimal("1.2"));
        var second = new ThresholdValue("S2", new BigDecimal("0.8"), new BigDecimal("1.2"));
        assertTrue(VALIDATOR.validate(new ThresholdUpdateRequest("SYS", "W", "req-1", "7", List.of(first, second))).isEmpty());
        assertFalse(VALIDATOR.validate(new ThresholdUpdateRequest("SYS", "W", "req-1", "7", List.of(first, first))).isEmpty());
        assertFalse(VALIDATOR.validate(new ThresholdValue("S1", BigDecimal.TEN, BigDecimal.ONE)).isEmpty());
    }

    @Test void nullablePageFactsRemainExplicitEvenWithExistingNonNullGlobalPolicy() throws Exception {
        var mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var view = PageResponses.HomeSnapshot.builder().systemCode("SYS").available(false)
                .sensors(List.of(PageResponses.Sensor.builder().sensorCode("S1").pressure(BigDecimal.ZERO).build()))
                .build();
        var json = mapper.readTree(mapper.writeValueAsString(view));
        assertTrue(json.has("systemStatus"));
        assertTrue(json.get("systemStatus").isNull());
        assertEquals(0, json.at("/sensors/0/pressure").decimalValue().compareTo(BigDecimal.ZERO));
        assertTrue(json.at("/sensors/0/alarmThreshold").isNull());
    }

    private static ArchiveQuery archive() {
        var query = new ArchiveQuery();
        query.setSystemCode("SYS");
        query.setWinchCode("W");
        query.setMetric("TENSION");
        query.setTensionMetric("WINCH_TENSION");
        query.setChangeStartDate(LocalDate.of(2026, 10, 1));
        query.setChangeEndDate(LocalDate.of(2026, 10, 8));
        query.setRecordCode("RUN-1");
        return query;
    }
}

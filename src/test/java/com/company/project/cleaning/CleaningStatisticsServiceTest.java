package com.company.project.cleaning;

import com.company.project.domain.bo.telemetry.MeasurementAggregateBo;
import com.company.project.domain.bo.telemetry.TelemetryBatchBo;
import com.company.project.domain.bo.telemetry.TelemetrySegmentBo;
import com.company.project.info.buffer.cleaning.TelemetryHistoryBuffer;
import com.company.project.repository.cleaning.TelemetryHistoryDao;
import com.company.project.service.statistics.CleaningStatisticsServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CleaningStatisticsServiceTest {
    private static TelemetryBatchBo batch(String id, long sequence) {
        var now = Instant.parse("2026-10-22T00:00:00Z");
        var measurement = new MeasurementAggregateBo(1, "rope-1", new BigDecimal("100"),
                new BigDecimal("120"), new BigDecimal("110"), new BigDecimal("115"), 4);
        var segment = new TelemetrySegmentBo("plc-001", id, sequence, "session-1", 1, "UP",
                now, now.plusSeconds(1), BigDecimal.ONE, new BigDecimal("1.5"), "HEIGHT_STEP", "v1",
                List.of(measurement), List.of(), List.of());
        return new TelemetryBatchBo(id, "plc-001", sequence, sequence, List.of(segment));
    }

    @Test void persistenceKeepsDeviceIdentityAndMeasurementValues() {
        var buffer = new TelemetryHistoryBuffer();
        var dao = mock(TelemetryHistoryDao.class);
        var service = new CleaningStatisticsServiceImpl(buffer, dao);
        service.acceptHistoryBatch(batch("batch-1", 1));
        service.runPersistenceCycle();
        verify(dao).insertBatch(eq("plc-001"), eq("batch-1"), eq(1L), eq(1L), argThat(segments -> {
            var segment = segments.get(0);
            var value = segment.ropeTensions().get(0);
            return segment.deviceId().equals("plc-001") && segment.roundNo() == 1
                    && value.targetId().equals("rope-1") && value.sampleCount() == 4
                    && value.minimum().compareTo(new BigDecimal("100")) == 0
                    && value.maximum().compareTo(new BigDecimal("120")) == 0;
        }));
        service.runPersistenceCycle();
        verifyNoMoreInteractions(dao);
    }

    @Test void failureReturnsOnlyUncommittedBatchesForRetry() {
        var buffer = new TelemetryHistoryBuffer();
        var dao = mock(TelemetryHistoryDao.class);
        var service = new CleaningStatisticsServiceImpl(buffer, dao);
        service.acceptHistoryBatch(batch("batch-1", 1));
        service.acceptHistoryBatch(batch("batch-2", 2));
        doThrow(new IllegalStateException("database unavailable"))
                .doNothing().when(dao).insertBatch(eq("plc-001"), eq("batch-2"), eq(2L), eq(2L), anyList());
        assertThrows(IllegalStateException.class, service::runPersistenceCycle);
        service.runPersistenceCycle();
        verify(dao, times(1)).insertBatch(eq("plc-001"), eq("batch-1"), eq(1L), eq(1L), anyList());
        verify(dao, times(2)).insertBatch(eq("plc-001"), eq("batch-2"), eq(2L), eq(2L), anyList());
        assertTrue(buffer.takeHistoryBatch(20).isEmpty());
    }
}

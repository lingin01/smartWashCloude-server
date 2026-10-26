package com.company.project.service.statistics;
import com.company.project.domain.bo.telemetry.RunHistoryBo.Write;
import com.company.project.info.buffer.cleaning.DurableHistoryInbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DurableHistoryInboxTest {
    @TempDir Path temporary;
    @Test void failedDatabaseSaveKeepsFilesAndRestartPreservesOrder() throws Exception {
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());
        try(var inbox=new DurableHistoryInbox(temporary.toString(),mapper)) {
            inbox.append(new Write("capture","plc",1,Instant.EPOCH,null,null,null,List.of()));
            inbox.append(new Write("capture","plc",2,Instant.EPOCH,null,null,null,List.of()));
            assertEquals(1,inbox.peek().orElseThrow().value().sequence());
            assertEquals(2,inbox.pendingCount());
        }
        try(var inbox=new DurableHistoryInbox(temporary.toString(),mapper)) {
            var first=inbox.peek().orElseThrow();assertEquals(1,first.value().sequence());
            inbox.acknowledge(first);assertEquals(2,inbox.peek().orElseThrow().value().sequence());
            inbox.acknowledge(inbox.peek().orElseThrow());assertTrue(inbox.peek().isEmpty());
        }
    }
    @Test void corruptEntryStopsProcessingInsteadOfLosingHistory() throws Exception {
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());
        Files.writeString(temporary.resolve("00000000000000000001.json"),"broken");
        try(var inbox=new DurableHistoryInbox(temporary.toString(),mapper)) {
            assertThrows(IllegalStateException.class,inbox::peek);
            assertEquals(1,inbox.pendingCount());
        }
    }
}

package com.company.project.service.command;

import com.company.project.config.*;
import com.company.project.domain.bo.*;
import com.company.project.domain.bo.plc.*;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.info.buffer.cleaning.*;
import com.company.project.repository.cleaning.*;
import com.company.project.repository.device.*;
import com.company.project.service.statistics.CleaningStatisticsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandAuditTest {
    @TempDir Path temp;
    @Test void databaseFailureKeepsResultReservedUntilDurableAuditAcknowledgement() throws Exception {
        var props=new PlcAccessProperties();props.setEnabled(true);
        var device=new PlcAccessProperties.Device();device.setId("plc");device.setEnabled(true);device.setWritesEnabled(true);
        var point=new PlcAccessProperties.Point();point.setEnabled(true);point.setFunctionCode(5);point.setAddress(15);
        point.setDataType(PlcAccessProperties.DataType.BOOLEAN);point.setValue(BigDecimal.ONE);device.setEmergencyStop(point);
        props.getDevices().add(device);
        var dao=mock(RuntimeHistoryDao.class);var messages=new PlcMessageBuffer();
        try(var inbox=new DurableHistoryInbox(temp.resolve("inbox").toString(),new ObjectMapper().findAndRegisterModules())) {
            var beans=new DefaultListableBeanFactory();beans.registerSingleton("dao",dao);beans.registerSingleton("inbox",inbox);
            var service=new CleaningCommandServiceImpl(new DeviceManager(props,mock(CleaningDeviceDao.class)),messages,
                    beans.getBeanProvider(RuntimeHistoryDao.class),beans.getBeanProvider(DurableHistoryInbox.class));
            try(var statistics=new CleaningStatisticsServiceImpl(new TelemetryHistoryBuffer(),mock(TelemetryHistoryDao.class),
                    beans.getBeanProvider(RuntimeHistoryDao.class),beans.getBeanProvider(DurableHistoryInbox.class),messages,
                    new PageSystemRegistry(new PageAccessProperties()),temp.resolve("export").toString())) {
                Instant now=Instant.now();
                var command=new CleaningCommand(UUID.randomUUID().toString(),"plc","emergency_stop",Map.of(),now,"stop","user",now.plusSeconds(30));
                assertEquals(CleaningCommandStatusBo.State.QUEUED,service.submit(command).state());
                var operation=messages.pollOperation().payload();
                var result=new PlcOperationResultBo(operation,true,false,1,"OK");messages.offerResult(result);
                var message=messages.peekResult();service.acceptDeviceResult(result);
                assertFalse(messages.completeResult(message.messageId()));assertEquals(1,inbox.pendingCount());
                doThrow(new IllegalStateException("db unavailable")).doNothing().when(dao).save(any());
                assertThrows(IllegalStateException.class,statistics::runPersistenceCycle);
                assertFalse(messages.completeResult(message.messageId()));assertEquals(1,inbox.pendingCount());
                statistics.runPersistenceCycle();assertEquals(0,inbox.pendingCount());
                service.acceptDeviceResult(result);assertTrue(messages.completeResult(message.messageId()));assertNull(messages.peekResult());
                when(dao.operation("plc","stop",null)).thenReturn(Optional.of(new CleaningCommandStatusBo("stop",command.commandId(),"plc","emergency_stop",
                        CleaningCommandStatusBo.State.SUCCEEDED,"OK","persisted",now,now.plusSeconds(30),now)));
                assertEquals("persisted",service.find("plc","stop").orElseThrow().message());
                verify(dao,atLeast(2)).saveOperation(eq(command),any());
            }
        }
    }
}

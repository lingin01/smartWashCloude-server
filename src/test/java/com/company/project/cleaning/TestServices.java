package com.company.project.cleaning;
import com.company.project.config.PlcAccessProperties;
import com.company.project.info.buffer.cleaning.TelemetryHistoryBuffer;
import com.company.project.info.buffer.plc.PlcMessageBuffer;
import com.company.project.repository.cleaning.*;
import com.company.project.repository.device.DeviceManager;
import com.company.project.service.*;
import com.company.project.service.command.CleaningCommandServiceImpl;
import com.company.project.service.cleaning.CleaningServiceImpl;
import com.company.project.service.lobby.CleaningLobbyServiceImpl;
import com.company.project.service.safety.CleaningSafetyServiceImpl;
import com.company.project.service.statistics.CleaningStatisticsServiceImpl;
import static org.mockito.Mockito.mock;
final class TestServices {
    static DeviceManager devices(PlcAccessProperties props){return new DeviceManager(props,mock(CleaningDeviceDao.class));}
    static CleaningCommandServiceImpl commands(PlcAccessProperties props,PlcMessageBuffer buffer){return new CleaningCommandServiceImpl(devices(props),buffer);}
    static CleaningStatisticsServiceImpl statistics(){return new CleaningStatisticsServiceImpl(new TelemetryHistoryBuffer(),mock(TelemetryHistoryDao.class));}
    static CleaningServiceImpl cleaning(CleaningCommandService commands){return new CleaningServiceImpl(devices(new PlcAccessProperties()),commands,statistics());}
    static CleaningLobbyServiceImpl lobby(CleaningService cleaning){return new CleaningLobbyServiceImpl(cleaning,new CleaningSafetyServiceImpl(),statistics());}
    static com.company.project.domain.message.Message<com.company.project.domain.bo.plc.PlcOperationBo> prepare(
            PlcAccessProperties props,com.company.project.domain.bo.CleaningCommand command){
        var buffer=new PlcMessageBuffer();var status=commands(props,buffer).submit(command);
        if(status.state()!=com.company.project.domain.bo.CleaningCommandStatusBo.State.QUEUED)
            throw new AssertionError("operation was not queued: "+status.code());
        return buffer.pollOperation();
    }
}

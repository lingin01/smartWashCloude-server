package com.company.project.presentation.plc;
import com.company.project.dto.plc.PlcReadSnapshotDto;
import com.company.project.domain.bo.plc.PlcOperationResultBo;
import com.company.project.service.CleaningLobbyService;
import org.springframework.stereotype.Component;
/** 上层设备表示入口：DTO转换后进入大厅，不写系统状态、命令状态或DAO。 */
@Component
public class PlcDevicePresentation {
    private final CleaningLobbyService lobby;
    private final PlcSnapshotConverter converter=new PlcSnapshotConverter();
    public PlcDevicePresentation(CleaningLobbyService lobby){this.lobby=lobby;}
    public void onSnapshot(PlcReadSnapshotDto snapshot){lobby.acceptDeviceSnapshot(converter.convert(snapshot));}
    public void onWriteResult(PlcOperationResultBo result){lobby.acceptDeviceWriteResult(result);}
}

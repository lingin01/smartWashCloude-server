package com.company.project.repository.device;
import com.company.project.config.PlcAccessProperties;
import com.company.project.domain.bo.CleaningSystemTopologyBo;
import com.company.project.domain.bo.plc.PlcDeviceSnapshotBo;
import com.company.project.repository.cleaning.CleaningDeviceDao;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** 数据层设备管理：只保管采集事实、技术配置和档案，不生成系统业务状态。 */
@Component
public class DeviceManager {
    private final PlcAccessProperties properties;
    private final CleaningDeviceDao dao;
    private final Map<String, PlcDeviceSnapshotBo> snapshots = new ConcurrentHashMap<>();
    public DeviceManager(PlcAccessProperties properties, CleaningDeviceDao dao) {
        this.properties=properties; this.dao=dao;
    }
    public void recordSnapshot(PlcDeviceSnapshotBo snapshot) {
        snapshots.compute(snapshot.deviceId(), (id, old) ->
                old == null || snapshot.sequence() > old.sequence() ? snapshot : old);
    }
    public Optional<PlcDeviceSnapshotBo> snapshot(String deviceId) { return Optional.ofNullable(snapshots.get(deviceId)); }
    public Optional<PlcAccessProperties.Device> activeDevice(String deviceId) { return properties.activeDevice(deviceId); }
    public int commandCapacity() { return properties.getQueueCapacity(); }
    public Optional<CleaningSystemTopologyBo> topology(String deviceId) { return dao.findByDeviceId(deviceId); }
    public void saveTopology(CleaningSystemTopologyBo topology) { dao.insertNew(topology); }
}

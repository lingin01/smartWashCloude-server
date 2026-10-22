package com.company.project.repository.cleaning;

import com.company.project.domain.bo.CleaningSystemTopologyBo;
import java.util.Optional;

/** 设备档案保存圆环自重与设备映射，不把 3 吨当成实时拉力阈值。 */
public interface CleaningDeviceDao {
    Optional<CleaningSystemTopologyBo> findByDeviceId(String deviceId);
    Optional<CleaningSystemTopologyBo> findBySystemId(String cleaningSystemId);
    void insertNew(CleaningSystemTopologyBo topology);
}

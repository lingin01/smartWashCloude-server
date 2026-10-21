package com.company.project.repository.cleaning;

import com.company.project.domain.entity.cleaning.MaintenanceOperationEntity;
import java.util.Optional;

public interface MaintenanceOperationDao {
    void insert(MaintenanceOperationEntity operation);
    Optional<MaintenanceOperationEntity> findById(String operationId);
    void updateResult(String operationId, String result, String endReason);
}

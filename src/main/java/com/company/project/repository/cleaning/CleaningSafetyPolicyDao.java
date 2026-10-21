package com.company.project.repository.cleaning;

import com.company.project.domain.entity.cleaning.CleaningSafetyPolicyEntity;
import java.util.List;

/** 业务规则从 DAO 读取；未审核、未生效的阈值不得用于现场控制。 */
public interface CleaningSafetyPolicyDao {
    List<CleaningSafetyPolicyEntity> findEffective(String cleaningSystemId);
    void insertVersion(CleaningSafetyPolicyEntity policy);
}

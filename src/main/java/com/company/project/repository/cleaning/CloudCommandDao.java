package com.company.project.repository.cleaning;

import com.company.project.domain.bo.CloudCommand;
import com.company.project.domain.bo.CloudCommandStatusBo;
import java.util.Optional;

/** 命令审计聚合接口；保存请求和本地操作结果，不依赖远程传输回执。 */
public interface CloudCommandDao {
    Optional<CloudCommand> findByRequestId(String requestId);
    void insert(CloudCommand command);
    void updateStatus(CloudCommandStatusBo result);
}

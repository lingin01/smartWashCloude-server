package com.company.project.service.safety;
import com.company.project.domain.param.cleaning.CleaningOperationContext;
import com.company.project.service.CleaningSafetyService;
import org.springframework.stereotype.Service;
/** 安全门面；当前权限与请求规则由本模块策略执行，不伪造现场安全条件。 */
@Service
public class CleaningSafetyServiceImpl implements CleaningSafetyService {
    private final OperationAccessPolicy policy=new OperationAccessPolicy();
    @Override public void verifyRead(CleaningOperationContext context) { policy.verifyRead(context); }
    @Override public void verifyEmergencyStop(CleaningOperationContext context) { policy.verifyEmergencyStop(context); }
    @Override public void verifySettingsChange(CleaningOperationContext context) { policy.verifySettingsChange(context); }
    @Override public void verifyPagePermission(CleaningOperationContext context, String permission) { policy.verifyPagePermission(context, permission); }
    @Override public void verifyPageSystem(CleaningOperationContext context, com.company.project.domain.bo.page.PageSystemBo system) {
        policy.verifyPageSystem(context, system);
    }
    @Override public boolean canReadPageSystem(CleaningOperationContext context, com.company.project.domain.bo.page.PageSystemBo system) {
        return policy.canReadPageSystem(context, system);
    }
}

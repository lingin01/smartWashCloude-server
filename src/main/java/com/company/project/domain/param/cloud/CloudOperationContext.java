package com.company.project.domain.param.cloud;
import java.util.Set;

/** 表示层从已验证登录身份生成权限，客户端不能自填 operatorId 或 permissions。 */
public record CloudOperationContext(String requestId, String boxId, String operatorId,
                                     Set<String> permissions) {
    public CloudOperationContext { permissions = permissions == null ? Set.of() : Set.copyOf(permissions); }
}

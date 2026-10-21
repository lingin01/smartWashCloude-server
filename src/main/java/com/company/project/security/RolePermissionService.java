package com.company.project.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class RolePermissionService {

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "ADMIN", Set.of("device:read", "device:write", "device:delete",
                    "alarm:read", "alarm:write", "alarm:delete",
                    "user:read", "user:write", "user:delete"),
            "OPERATOR", Set.of("device:read", "device:write",
                    "alarm:read", "alarm:write"),
            "VIEWER", Set.of("device:read", "alarm:read")
    );

    public Set<String> getPermissions(String roleCode) {
        return ROLE_PERMISSIONS.getOrDefault(roleCode, Set.of());
    }

    public boolean hasPermission(String roleCode, String permission) {
        Set<String> permissions = getPermissions(roleCode);
        return permissions.contains(permission);
    }
}

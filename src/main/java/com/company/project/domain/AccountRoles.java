package com.company.project.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 与当前权限表中的三个角色编码一致，未知角色不猜显示名称。 */
public final class AccountRoles {
    private static final Map<String, String> NAMES = Map.of(
            "ADMIN", "管理员", "OPERATOR", "操作员", "VIEWER", "只读用户");

    private AccountRoles() { }

    public static String name(String code) { return code == null ? null : NAMES.get(code); }

    public static List<String> matchingCodes(String keyword) {
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return NAMES.entrySet().stream()
                .filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).contains(normalized))
                .map(Map.Entry::getKey).toList();
    }
}

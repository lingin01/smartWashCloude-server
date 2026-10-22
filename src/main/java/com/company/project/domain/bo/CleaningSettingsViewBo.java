package com.company.project.domain.bo;
/** 尚未收到正式配置快照时 version/settings 均为空，避免制造“当前配置”。 */
public record CleaningSettingsViewBo(String deviceId, String state, Long version,
                                   CleaningSettingsBo settings) { }

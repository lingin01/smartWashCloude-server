package com.company.project.domain.param.cleaning;
import com.company.project.domain.bo.CleaningSettingsBo;
public record SettingsUpdateParam(CleaningOperationContext context, long expectedVersion,
                                        CleaningSettingsBo settings) { }

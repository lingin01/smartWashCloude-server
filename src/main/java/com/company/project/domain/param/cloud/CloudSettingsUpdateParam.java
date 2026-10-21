package com.company.project.domain.param.cloud;
import com.company.project.domain.bo.CloudCleaningSettingsBo;
public record CloudSettingsUpdateParam(CloudOperationContext context, long expectedVersion,
                                        CloudCleaningSettingsBo settings) { }

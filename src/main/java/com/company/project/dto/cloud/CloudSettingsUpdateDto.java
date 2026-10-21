package com.company.project.dto.cloud;
public record CloudSettingsUpdateDto(String requestId, Long expectedVersion,
                                      CloudCleaningSettingsDto settings) { }

package com.company.project.dto.cleaning;
public record SettingsUpdateDto(String requestId, Long expectedVersion,
                                      CleaningSettingsDto settings) { }

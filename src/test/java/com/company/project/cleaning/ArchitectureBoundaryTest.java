package com.company.project.cleaning;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureBoundaryTest {
    @Test void lowerLayersHaveNoDependenciesOnServicesOrNetworkEntrypoints() throws Exception {
        var root = Path.of("src/main/java/com/company/project");
        for (String layer : List.of("repository", "info", "infrastructure")) {
            try (var files = Files.walk(root.resolve(layer))) {
                for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    var source = Files.readString(file);
                    for (String forbidden : List.of("service", "presentation", "controller"))
                        assertFalse(source.contains("com.company.project." + forbidden + "."), file + " depends on upper layer " + forbidden);
                }
            }
        }
        try (var files = Files.walk(root.resolve("service"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var source = Files.readString(file);
                assertFalse(source.contains("com.company.project.presentation."), file.toString());
                assertFalse(source.contains("com.company.project.controller."), file.toString());
            }
        }
    }

    @Test void mutableStateAndBusinessRulesCannotBeAccessedOutsideTheirOwningModule() throws Exception {
        for (String name : List.of("service.cleaning.CleaningSystemState", "service.cleaning.CleaningOperationPlanner", "service.cleaning.CleaningDeviceConverter",
                "service.command.CommandState", "service.command.CommandSubmission", "service.command.PlcCommandEncoder", "service.safety.OperationAccessPolicy", "service.statistics.SampleStatistics")) {
            var type = Class.forName("com.company.project." + name);
            assertFalse(Modifier.isPublic(type.getModifiers()), name + " must remain private to its package");
            assertNull(type.getAnnotation(org.springframework.stereotype.Component.class));
        }
    }
}

package com.company.project.field;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FieldTestConfigurationTest {
    @TempDir Path directory;

    @Test void readsValuesAndEmptyPasswordDirectlyFromYaml() throws Exception {
        Path file = directory.resolve("field-test.yml");
        Files.writeString(file, """
                field:
                  device-id: site-plc
                  duration-seconds: 15
                  plc:
                    config: field-config/plc.yml
                  history:
                    datasource:
                      url: jdbc:mysql://127.0.0.1:3306/test_only
                      username: root
                      password: ""
                """);
        var config = FieldTestConfiguration.load(file, directory);
        assertEquals("site-plc", config.text("field.device-id"));
        assertEquals(15, config.integer("field.duration-seconds", 60));
        assertEquals(directory.resolve("field-config/plc.yml"), config.path("field.plc.config", null));
        assertEquals("", config.text("field.history.datasource.password", "fallback"));
        assertEquals("root", config.text("field.history.datasource.username"));
        assertEquals(3, config.integer("field.api.samples", 3));
        assertFalse(config.toString().contains("test_only"));
    }

    @Test void missingAndInvalidValuesPointToTheConfigurationKey() throws Exception {
        Path file = directory.resolve("field-test.yml");
        Files.writeString(file, "field:\n  duration-seconds: invalid\n");
        var config = FieldTestConfiguration.load(file, directory);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> config.text("field.device-id"))
                .getMessage().contains("field.device-id"));
        assertThrows(IllegalArgumentException.class, () -> config.integer("field.duration-seconds", 60));
        assertThrows(IllegalArgumentException.class, () -> config.path("field.capture-dir", null));
        assertThrows(IllegalArgumentException.class,
                () -> FieldTestConfiguration.load(directory.resolve("missing.yml"), directory));
    }

    @Test void optionalCommandLineOverrideRemainsAvailableWithoutBeingRequired() throws Exception {
        Path file = directory.resolve("field-test.yml");
        Files.writeString(file, "field:\n  device-id: file-device\n");
        String old = System.getProperty("field.device-id");
        try {
            System.setProperty("field.device-id", "optional-override");
            assertEquals("optional-override", FieldTestConfiguration.load(file, directory).text("field.device-id"));
        } finally {
            if (old == null) System.clearProperty("field.device-id");
            else System.setProperty("field.device-id", old);
        }
    }
}

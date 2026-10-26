package com.company.project.field;

import com.company.project.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 只加载配置文档，不创建数据源、不连接数据库，也不启动应用或 PLC。 */
class ApplicationLocalConfigurationTest {
    private static final String LOCAL_IMPORT = "optional:file:./application-local.yml";
    private static final String TEST_KEY = "configuration-file-test-key-32-bytes-or-more";

    @TempDir Path directory;

    @Test void devImportsLocalValuesWithoutAnyEnvironmentVariables() throws Exception {
        Files.writeString(directory.resolve("application-local.yml"), """
                spring:
                  datasource:
                    url: jdbc:mysql://127.0.0.1:1/configuration_file_only
                    username: file-test-user
                    password: file-test-password
                jwt:
                  secret: %s
                """.formatted(TEST_KEY));

        try (var context = loadConfiguration()) {
            var environment = context.getEnvironment();
            var database = Binder.get(environment).bind("spring.datasource", Bindable.of(DataSourceProperties.class))
                    .orElseThrow(() -> new AssertionError("Missing datasource configuration"));
            assertEquals("jdbc:mysql://127.0.0.1:1/configuration_file_only", database.getUrl());
            assertEquals("file-test-user", database.getUsername());
            assertEquals("file-test-password", database.getPassword());
            assertEquals(TEST_KEY, environment.getProperty("jwt.secret"));
            assertDoesNotThrow(() -> new JwtTokenProvider(environment.getProperty("jwt.secret"),
                    Long.parseLong(environment.getProperty("jwt.expiration")), 604800000));
            assertFalse(environment.getProperty("cleaning.plc.enabled", Boolean.class, true));
            assertEquals(0, context.getBeanDefinitionCount(), "Config loading must not bootstrap application beans");
        }
    }

    @Test void optionalLocalFileDoesNotLeaveUnresolvedEnvironmentPlaceholders() throws Exception {
        try (var context = loadConfiguration()) {
            var environment = context.getEnvironment();
            assertEquals("jdbc:mysql://127.0.0.1:3306/smartwash_cloud?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                    environment.getProperty("spring.datasource.url"));
            assertEquals("root", environment.getProperty("spring.datasource.username"));
            assertEquals("", environment.getProperty("spring.datasource.password"));
            assertEquals("", environment.getProperty("jwt.secret"), "Shared config must not provide a fixed JWT key");
        }
    }

    private GenericApplicationContext loadConfiguration() throws Exception {
        String development = Files.readString(Path.of("src/main/resources/application-dev.yml"));
        assertTrue(development.contains("import: " + LOCAL_IMPORT));
        // 将测试副本的导入指向临时目录，避免读取或修改用户真实的本机配置。
        development = development.replace(LOCAL_IMPORT,
                "optional:" + directory.resolve("application-local.yml").toUri());
        Files.writeString(directory.resolve("application-dev.yml"), development);
        Files.writeString(directory.resolve("application.yml"),
                Files.readString(Path.of("src/main/resources/application.yml")));

        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("isolated-config-location",
                Map.of("spring.config.location", directory.resolve("application.yml").toUri().toString())));
        var context = new GenericApplicationContext();
        context.setEnvironment(environment);
        new ConfigDataApplicationContextInitializer().initialize(context);
        return context;
    }
}

package com.company.project.field;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 现场测试直接读取本地 YAML；IDE 中运行测试无需设置环境变量或 VM 参数。 */
final class FieldTestConfiguration {
    private final StandardEnvironment environment;
    private final Path file;
    private final Path projectRoot;

    private FieldTestConfiguration(StandardEnvironment environment, Path file, Path projectRoot) {
        this.environment = environment;
        this.file = file;
        this.projectRoot = projectRoot;
    }

    static FieldTestConfiguration load() throws IOException {
        Path root = Path.of(System.getProperty("basedir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
        Path file = Path.of(System.getProperty("field.config", "field-config/field-test.yml"));
        return load(file.isAbsolute() ? file : root.resolve(file), root);
    }

    static FieldTestConfiguration load(Path file, Path projectRoot) throws IOException {
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException(
                "找不到现场配置文件 " + file.toAbsolutePath()
                        + "；请复制 tools/field/field-test.example.yml 为 field-config/field-test.yml 并填写配置");
        var environment = new StandardEnvironment();
        var sources = new YamlPropertySourceLoader().load("field-test", new FileSystemResource(file));
        for (var source : sources) environment.getPropertySources().addLast(source);
        return new FieldTestConfiguration(environment, file.toAbsolutePath().normalize(), projectRoot.toAbsolutePath().normalize());
    }

    String text(String key) {
        String value = text(key, null);
        if (value == null || value.isBlank()) throw missing(key);
        return value;
    }

    String text(String key, String fallback) {
        return environment.getProperty(key, fallback);
    }

    int integer(String key, int fallback) {
        String value = text(key, null);
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("请在 " + file + " 中将 " + key + " 填为整数");
        }
    }

    Path path(String key, String fallback) {
        String value = text(key, fallback);
        if (value == null || value.isBlank()) throw missing(key);
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : projectRoot.resolve(path)).normalize();
    }

    private IllegalArgumentException missing(String key) {
        return new IllegalArgumentException("请在 " + file + " 中填写 " + key);
    }

    @Override public String toString() { return "FieldTestConfiguration[file=" + file + "]"; }
}

package com.company.project.field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** IDE 直接运行，从 field-config/field-test.yml 读取现场目录；不启动应用、不连接 PLC 或数据库。 */
class PlcFieldReplayTest {
    @Test void replayCapturedFramesThroughCurrentBusinessCode() throws Exception {
        var configuration = FieldTestConfiguration.load();
        long frames = PlcFieldCapture.replay(
                configuration.path("field.capture-dir", configuration.text("field.capture-dir")),
                configuration.path("field.output-dir", "field-results"));
        assertTrue(frames > 0, "采集文件为空");
    }
}

package com.company.project.field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** IDEA 直接运行，参数来自 field-config/field-test.yml；普通 Maven 测试排除此类。 */
class PlcFieldCaptureTest {
    @Test void captureRealPlcAndPreserveEvidence() throws Exception {
        var result = PlcFieldCapture.captureConfigured();
        assertTrue(result.passed(), "存在离线/无效点位或没有采样，请查看 " + result.directory().resolve("summary.json"));
    }
}

package com.company.project.field;

import com.company.project.config.PlcAccessProperties;
import com.company.project.config.PlcAccessProperties.ByteOrder;
import com.company.project.config.PlcAccessProperties.DataType;
import com.company.project.config.PlcAccessProperties.Point;
import com.company.project.infrastructure.plc.ModbusTcpCodec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 只验证新点位配置的绑定、四机归属和换算，不连接PLC。 */
class PlcPointConfigurationTest {
    private static final Set<String> NEW_WINCH_FIELDS = Set.of("winchState", "winchUp", "winchDown",
            "encoderCount", "axisRunSeconds", "motorEnableFaultCode", "motorRunningFaultCode");

    @Test void latestPointMapBindsWithoutImportingChineseExplanations() throws Exception {
        var properties = template();
        var device = properties.getDevices().get(0);
        assertEquals(48, device.getPoints().size());
        assertTrue(device.getPoints().stream().noneMatch(Point::isEnabled));
        assertTrue(device.getPoints().stream().allMatch(p -> p.getAddress() == null));
        for (String field : NEW_WINCH_FIELDS) {
            assertEquals(4, device.getPoints().stream().filter(p -> field.equals(p.getField())).count());
        }
        assertEquals("M15", device.getEmergencyStop().getSymbol());
        assertEquals(BigDecimal.ONE, device.getEmergencyStop().getValue());
        assertFalse(device.isWritesEnabled());
        assertFalse(Pattern.compile("[\\p{IsHan}]").matcher(PlcFieldCapture.JSON.writeValueAsString(properties)).find(),
                "Chinese descriptions must stay in YAML comments, outside bound runtime values");
    }

    @Test void allFourMachineSlotsAreAcceptedAndIdentityErrorsAreRejected() throws Exception {
        var properties = configuredForValidation();
        assertDoesNotThrow(properties::validate);
        var encoder = point(properties, "encoderCount", 4);
        encoder.setIndex(5);
        assertThrows(IllegalArgumentException.class, properties::validate);
        encoder.setIndex(4);
        encoder.setWinchId("");
        assertThrows(IllegalArgumentException.class, properties::validate);
        encoder.setWinchId("W4");
        var timer = point(properties, "axisRunSeconds", 4);
        timer.setWinchId("W3");
        assertThrows(IllegalArgumentException.class, properties::validate);
        timer.setWinchId("W4");
        assertDoesNotThrow(properties::validate);
    }

    @Test void tensionAndMotorEquivalentForceUseConfirmedLinearFormulas() throws Exception {
        var properties = configuredForValidation();
        var tension = point(properties, "ropeTension", 1);
        assertEquals("D30", tension.getSymbol());
        assertEquals(0, new BigDecimal("100.951884").compareTo(
                ModbusTcpCodec.decode(tension, new byte[]{3, (byte) 232}, 0, false)));
        var motor = point(properties, "winchTorque", 4);
        assertEquals("D73", motor.getSymbol());
        BigDecimal expected = new BigDecimal("100").multiply(new BigDecimal("0.35"))
                .multiply(new BigDecimal("163.0883")).divide(
                        new BigDecimal("9.80665").multiply(new BigDecimal("0.16")), MathContext.DECIMAL128);
        BigDecimal actual = ModbusTcpCodec.decode(motor, new byte[]{0, 100}, 0, false);
        assertTrue(expected.subtract(actual).abs().compareTo(new BigDecimal("1E-25")) < 0);
        assertEquals("kg", motor.getUnit());
    }

    private static PlcAccessProperties template() throws Exception {
        var environment = new StandardEnvironment();
        var sources = new YamlPropertySourceLoader().load("point-template",
                new FileSystemResource(Path.of("src/main/resources/plc-points.example.yml")));
        for (var source : sources) environment.getPropertySources().addFirst(source);
        return Binder.get(environment).bind("cleaning.plc", Bindable.of(PlcAccessProperties.class))
                .orElseThrow(() -> new AssertionError("Missing point map"));
    }

    private static PlcAccessProperties configuredForValidation() throws Exception {
        var properties = template();
        properties.setEnabled(true);
        var device = properties.getDevices().get(0);
        device.setId("local-validation-only");
        device.setHost("127.0.0.1");
        device.setEnabled(true);
        int address = 1000;
        for (var point : device.getPoints()) {
            // 以下技术值仅用于内存校验，不是建议填写到真实PLC的类型/字序/地址。
            point.setEnabled(true);
            point.setAddress(address);
            address += 4;
            if (point.getDataType() == null) point.setDataType(DataType.UINT16);
            if (point.getByteOrder() == null) point.setByteOrder(ByteOrder.ABCD);
            if ("ropeTension".equals(point.getField())) {
                point.setWinchId("W" + ((point.getIndex() - 1) / 2 + 1));
                point.setRopeId("R" + point.getIndex());
                point.setSensorId("S" + point.getIndex());
            } else if (point.getField().startsWith("winch") || NEW_WINCH_FIELDS.contains(point.getField())) {
                point.setWinchId("W" + point.getIndex());
            }
        }
        return properties;
    }

    private static Point point(PlcAccessProperties properties, String field, int index) {
        return properties.getDevices().get(0).getPoints().stream()
                .filter(p -> field.equals(p.getField()) && p.getIndex() == index).findFirst().orElseThrow();
    }
}

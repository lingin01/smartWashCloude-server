package com.company.project;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK,properties={
        "spring.profiles.active=offline-test","spring.datasource.url=jdbc:mysql://127.0.0.1:9/offline_test",
        "spring.datasource.username=offline","spring.datasource.password=offline",
        "jwt.secret=offline-unit-tests-secret-at-least-32-bytes","jwt.expiration=86400000"})
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}

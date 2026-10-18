package com.f1strategy.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "f1.db.path=target/test.db",
        "spring.datasource.url=jdbc:sqlite:target/test.db"
})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}

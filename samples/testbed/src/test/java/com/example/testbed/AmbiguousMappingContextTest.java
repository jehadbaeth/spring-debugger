package com.example.testbed;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rule 5.8: two handler methods mapped to the same path + method, detected at startup. */
@SpringBootTest(classes = { TestbedApplication.class, AmbiguousMappingContextTest.DupConfig.class })
class AmbiguousMappingContextTest {
    @RestController
    static class DupController {
        @GetMapping("/dup") String one() { return "one"; }
        @GetMapping("/dup") String two() { return "two"; }
    }
    @Configuration static class DupConfig {
        @Bean DupController dupController() { return new DupController(); }
    }
    @Test void contextLoads() { }
}

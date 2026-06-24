package com.example.testbed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Rule 3.1: a @Value references a property defined in no source and with no default. The full
 *  Boot context is loaded so the placeholder configurer actually attempts resolution. */
@SpringBootTest(classes = { TestbedApplication.class, UnresolvedPlaceholderContextTest.Config.class })
class UnresolvedPlaceholderContextTest {
    static class Greeter { Greeter(String msg) { } }
    @Configuration static class Config {
        @Bean Greeter greeter(@Value("${app.greeting.message}") String msg) { return new Greeter(msg); }
    }
    @Test void contextLoads() { }
}

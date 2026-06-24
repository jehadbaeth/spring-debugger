package com.example.testbed;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Rule 2.2: two beans of the same type and a consumer that needs exactly one. */
@SpringBootTest(classes = NoUniqueBeanContextTest.Config.class)
class NoUniqueBeanContextTest {
    interface Notifier { }
    static class EmailNotifier implements Notifier { }
    static class SmsNotifier implements Notifier { }
    static class AlertService { AlertService(Notifier notifier) { } }
    @Configuration static class Config {
        @Bean Notifier email() { return new EmailNotifier(); }
        @Bean Notifier sms() { return new SmsNotifier(); }
        @Bean AlertService alertService(Notifier notifier) { return new AlertService(notifier); }
    }
    @Test void contextLoads() { }
}

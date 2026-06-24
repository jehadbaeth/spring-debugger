package com.example.testbed;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Rule 2.x / 2.13: a bean needs a collaborator that is never defined. */
@SpringBootTest(classes = MissingBeanContextTest.Config.class)
class MissingBeanContextTest {
    interface PaymentGateway { }
    static class OrderService { OrderService(PaymentGateway gateway) { } }
    @Configuration static class Config {
        @Bean OrderService orderService(PaymentGateway gateway) { return new OrderService(gateway); }
    }
    @Test void contextLoads() { }
}

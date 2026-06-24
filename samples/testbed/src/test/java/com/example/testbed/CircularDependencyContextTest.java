package com.example.testbed;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Rule 1.13 / 2.7: two beans require each other through the constructor. */
@SpringBootTest(classes = CircularDependencyContextTest.Config.class)
class CircularDependencyContextTest {
    static class A { A(B b) { } }
    static class B { B(A a) { } }
    @Configuration static class Config {
        @Bean A a(B b) { return new A(b); }
        @Bean B b(A a) { return new B(a); }
    }
    @Test void contextLoads() { }
}

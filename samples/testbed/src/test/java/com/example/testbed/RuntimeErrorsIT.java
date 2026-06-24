package com.example.testbed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Runtime errors hit through the live web layer. Each request drives the server down a failure
 * path; the server-side exception is logged to the console (visible to the Gradle/terminal tap).
 * The assertions are loose on purpose so the suite keeps going and exercises every endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RuntimeErrorsIT {

    @Autowired MockMvc mockMvc;

    // Rule 5.5: invalid @RequestBody -> MethodArgumentNotValidException.
    @Test void validationFailure() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    // Rule 5.1: no handler for this path (throw-exception-if-no-handler-found is on).
    @Test void noHandlerFound() throws Exception {
        mockMvc.perform(get("/does-not-exist"));
    }

    // Rule 7.2: Jackson infinite recursion serialising a bidirectional object graph.
    @Test void jacksonRecursion() throws Exception {
        try {
            mockMvc.perform(get("/recursion"));
        } catch (Throwable ignored) {
            // StackOverflowError may surface here; the server-side log is what the plugin reads.
        }
    }
}

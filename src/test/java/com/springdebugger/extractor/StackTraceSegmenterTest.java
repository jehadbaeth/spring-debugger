package com.springdebugger.extractor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StackTraceSegmenterTest {

    @Test
    void singleErrorStaysOneBlock() {
        String log = """
                org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean
                \tat org.springframework.Foo.bar(Foo.java:1)
                Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'X'
                \tat org.springframework.Baz.qux(Baz.java:2)
                """;
        List<String> blocks = StackTraceSegmenter.segment(log);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0)).contains("NoSuchBeanDefinitionException");
    }

    @Test
    void multipleTopLevelExceptionsAreSplit() {
        String log = """
                2024-01-01 ERROR c.e.A : request failed
                java.lang.IllegalStateException: first failure
                \tat com.example.A.run(A.java:10)
                2024-01-01 ERROR c.e.B : another request failed
                org.springframework.web.servlet.NoHandlerFoundException: No handler found for GET /x
                \tat org.springframework.web.servlet.DispatcherServlet.noHandlerFound(DispatcherServlet.java:1)
                """;
        List<String> blocks = StackTraceSegmenter.segment(log);
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).contains("IllegalStateException: first failure");
        assertThat(blocks.get(0)).doesNotContain("NoHandlerFoundException");
        assertThat(blocks.get(1)).contains("NoHandlerFoundException");
    }

    @Test
    void causedByLinesDoNotStartNewBlocks() {
        String log = """
                com.example.WrapperException: outer
                Caused by: com.example.MiddleException: middle
                Caused by: com.example.RootException: root
                """;
        assertThat(StackTraceSegmenter.segment(log)).hasSize(1);
    }

    @Test
    void blankOrNullYieldsNoBlocks() {
        assertThat(StackTraceSegmenter.segment(null)).isEmpty();
        assertThat(StackTraceSegmenter.segment("   ")).isEmpty();
    }

    @Test
    void textWithNoTopLevelExceptionReturnedWhole() {
        // Banner-only startup failures have no col-0 exception line; keep them intact.
        String banner = """
                ***************************
                APPLICATION FAILED TO START
                ***************************

                Description:

                Web server failed to start. Port 8080 was already in use.
                """;
        assertThat(StackTraceSegmenter.segment(banner)).containsExactly(banner);
    }
}

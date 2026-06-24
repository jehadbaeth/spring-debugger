package com.springdebugger.extractor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts failure and error text from a JUnit-style test-results XML file (the format Gradle
 * writes to {@code build/test-results/test/*.xml} and Maven Surefire to
 * {@code target/surefire-reports/*.xml}).
 *
 * <p>This is the terminal-agnostic capture path for tests: a developer running {@code ./gradlew
 * test} in any terminal (classic or new) produces these files, so reading them needs no hook into
 * the terminal at all. Each {@code <failure>}/{@code <error>} carries the message attribute plus
 * the full stack trace as its body, which is exactly what the diagnosis engine consumes.
 *
 * <p>Pure and dependency-free so it is unit-tested directly against real result XML.
 */
public final class TestResultsParser {

    private TestResultsParser() {}

    /** Returns one text block per failed/errored test case: its message followed by its stack body. */
    public static List<String> failureTexts(String xml) {
        List<String> out = new ArrayList<>();
        if (xml == null || xml.isBlank()) return out;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Harden against XXE; result files are local but this keeps the parser safe on any input.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            for (String tag : new String[]{"failure", "error"}) {
                NodeList nodes = doc.getElementsByTagName(tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node n = nodes.item(i);
                    if (!(n instanceof Element)) continue;
                    Element el = (Element) n;
                    String message = el.getAttribute("message");
                    String body = el.getTextContent();
                    StringBuilder block = new StringBuilder();
                    if (message != null && !message.isBlank()) block.append(message).append('\n');
                    if (body != null && !body.isBlank()) block.append(body);
                    String text = block.toString().trim();
                    if (!text.isEmpty()) out.add(text);
                }
            }
        } catch (Exception e) {
            // A half-written file (Gradle writes them mid-run) or malformed XML: skip, the next
            // watch event re-reads the completed file.
            return out;
        }
        return out;
    }

    /** True if the suite XML reports at least one failure or error, cheaply, without full parsing. */
    public static boolean hasFailures(String xml) {
        if (xml == null) return false;
        return xml.contains("<failure") || xml.contains("<error");
    }
}

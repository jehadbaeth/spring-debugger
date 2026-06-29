package com.springdebugger.convention.robot;

import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.List;

/**
 * The parsed shape of a Robot Framework suite file, holding only what the convention checks need.
 * Produced by {@link RobotSuiteParser}; ranges are absolute offsets into the file text so they can
 * anchor an inspection highlight directly.
 */
public final class RobotSuite {

    /** A {@code Metadata  Name  Value} entry from the *** Settings *** section. */
    public static final class Metadata {
        public final String name;
        public final String value;
        public final TextRange lineRange;
        public Metadata(String name, String value, TextRange lineRange) {
            this.name = name;
            this.value = value;
            this.lineRange = lineRange;
        }
    }

    /** One test case under *** Test Cases ***. */
    public static final class TestCase {
        public final String name;
        public final TextRange nameRange;
        public boolean hasDocumentation;
        public boolean hasTags;
        public final List<String> tags = new ArrayList<>();
        public TestCase(String name, TextRange nameRange) {
            this.name = name;
            this.nameRange = nameRange;
        }
    }

    public final TextRange settingsHeaderRange; // null if no *** Settings *** header
    public final List<Metadata> metadata;
    public final List<TestCase> testCases;
    public final boolean hasTestCasesSection;

    public RobotSuite(TextRange settingsHeaderRange, List<Metadata> metadata,
                      List<TestCase> testCases, boolean hasTestCasesSection) {
        this.settingsHeaderRange = settingsHeaderRange;
        this.metadata = metadata;
        this.testCases = testCases;
        this.hasTestCasesSection = hasTestCasesSection;
    }

    /** First metadata entry whose name matches (ignoring case, spaces, and dashes), or null. */
    public Metadata findMetadata(String name) {
        String want = normalize(name);
        for (Metadata m : metadata) {
            if (normalize(m.name).equals(want)) return m;
        }
        return null;
    }

    /** Normalize a metadata name so "Pass-Fail Criteria" and "Pass Fail Criteria" compare equal. */
    public static String normalize(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }
}

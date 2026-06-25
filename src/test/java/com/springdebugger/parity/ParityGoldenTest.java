package com.springdebugger.parity;

import com.springdebugger.model.DiagnosisCard;
import com.springdebugger.rule.RuleCatalog;
import com.springdebugger.tap.ConsoleDiagnoser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-engine parity anchor. Runs the SHIPPED {@link ConsoleDiagnoser#diagnoseAll} (the same
 * entry point both products use) over every fixture and real-world log, and serialises the
 * resulting cards to {@code parity/golden.json}. The TypeScript engine's parity test consumes
 * the exact same golden and must reproduce identical cards, so this file is the single source of
 * truth for "what the engine produces" across the Java and TypeScript implementations.
 *
 * <p>By default this test ASSERTS the committed golden still matches. Run with
 * {@code -Dparity.regenerate=true} to rewrite it after an intentional engine or rule change; the
 * git diff then makes the behavioural change reviewable, and any unintended drift in the TS port
 * fails its own parity test.
 *
 * <p>The golden is driven through the real diagnoser (not a hand replica) precisely so it certifies
 * shipped behaviour: {@code diagnoseAll(text, null)} runs headless because a null project means no
 * enrichment context and {@code LlmFallback.fromSettings()} returns null with no running IDE.
 */
class ParityGoldenTest {

    private static final File GOLDEN = new File("parity/golden.json");

    private static final String[] CORPUS_DIRS = {
            "src/main/resources/fixtures",
            "src/test/resources/real-world-logs"
    };

    @Test
    void goldenMatchesShippedDiagnoser() throws IOException {
        String produced = buildGoldenJson();

        boolean regenerate = Boolean.getBoolean("parity.regenerate");
        if (regenerate || !GOLDEN.exists()) {
            GOLDEN.getParentFile().mkdirs();
            Files.write(GOLDEN.toPath(), produced.getBytes(StandardCharsets.UTF_8));
            // When regenerating, do not also assert; the freshly written file trivially matches.
            return;
        }

        String committed = new String(Files.readAllBytes(GOLDEN.toPath()), StandardCharsets.UTF_8);
        assertThat(normalise(produced))
                .as("parity/golden.json is stale. If this change to engine/rules is intentional, "
                        + "regenerate with: ./gradlew test --tests '*ParityGoldenTest*' "
                        + "-Dparity.regenerate=true  then commit the diff.")
                .isEqualTo(normalise(committed));
    }

    /** Builds the deterministic golden JSON over the whole corpus, keyed by "<dir>/<file>". */
    static String buildGoldenJson() throws IOException {
        ConsoleDiagnoser diagnoser = new ConsoleDiagnoser(RuleCatalog.load());
        // TreeMap keeps the output stable regardless of filesystem iteration order.
        TreeMap<String, List<DiagnosisCard>> byKey = new TreeMap<>();

        for (String dirPath : CORPUS_DIRS) {
            File dir = new File(dirPath);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
            if (files == null) continue;
            String label = new File(dirPath).getName();
            for (File f : files) {
                String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                byKey.put(label + "/" + f.getName(), diagnoser.diagnoseAll(content, null));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int entryIdx = 0;
        for (var entry : byKey.entrySet()) {
            sb.append("  ").append(quote(entry.getKey())).append(": [");
            List<DiagnosisCard> cards = entry.getValue();
            if (cards.isEmpty()) {
                sb.append("]");
            } else {
                sb.append("\n");
                for (int i = 0; i < cards.size(); i++) {
                    sb.append(cardJson(cards.get(i)));
                    if (i + 1 < cards.size()) sb.append(",");
                    sb.append("\n");
                }
                sb.append("  ]");
            }
            if (++entryIdx < byKey.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String cardJson(DiagnosisCard c) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        appendField(sb, "ruleId", c.getRuleId(), true);
        appendField(sb, "phase", c.getPhase() != null ? c.getPhase().name() : null, true);
        appendField(sb, "diagnosis", c.getDiagnosisSentence(), true);
        appendField(sb, "fix", c.getFixSentence(), true);
        appendField(sb, "confidence", c.getConfidence() != null ? c.getConfidence().name() : null, true);
        // Excerpt is raw passthrough (last 8000 chars); byte-comparing it adds false-failure risk
        // across line-ending handling without testing behaviour, so we assert presence only.
        boolean excerptNonEmpty = c.getExcerpt() != null && !c.getExcerpt().isBlank();
        sb.append("      ").append(quote("excerptNonEmpty")).append(": ").append(excerptNonEmpty).append("\n");
        sb.append("    }");
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value, boolean more) {
        sb.append("      ").append(quote(key)).append(": ")
                .append(value == null ? "null" : quote(value));
        if (more) sb.append(",");
        sb.append("\n");
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.append("\"").toString();
    }

    /** Normalises line endings so a checkout on either platform compares equal. */
    private static String normalise(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }
}

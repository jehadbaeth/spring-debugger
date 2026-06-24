package com.springdebugger.extractor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds JUnit result XML files under a project tree: Gradle's {@code build/test-results/**.xml} and
 * Maven Surefire/Failsafe's {@code target/surefire-reports/*.xml} / {@code target/failsafe-reports}.
 *
 * <p>Walks with a bounded depth and prunes directories that never hold build output (source, VCS,
 * caches) so it stays cheap on large multi-module monorepos. Pure file-system logic, unit-tested
 * against a temporary tree.
 */
public final class TestResultsLocator {

    private static final int MAX_DEPTH = 8;

    private TestResultsLocator() {}

    public static List<File> locate(File projectBase) {
        List<File> results = new ArrayList<>();
        if (projectBase == null || !projectBase.isDirectory()) return results;
        walk(projectBase, 0, results);
        return results;
    }

    private static void walk(File dir, int depth, List<File> out) {
        if (depth > MAX_DEPTH) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                String name = child.getName();
                if (isPruned(name)) continue;
                if (isResultDir(child)) {
                    collectXml(child, out);
                    // Result dirs do not nest result dirs; no need to descend further into them.
                    continue;
                }
                walk(child, depth + 1, out);
            }
        }
    }

    /** A directory whose contents are JUnit XML: Gradle test-results leaves and Maven report dirs. */
    private static boolean isResultDir(File dir) {
        String path = dir.getPath().replace(File.separatorChar, '/');
        return path.contains("/build/test-results/")
                || path.endsWith("/surefire-reports")
                || path.endsWith("/failsafe-reports");
    }

    private static boolean isPruned(String name) {
        switch (name) {
            case ".git":
            case ".gradle":
            case ".idea":
            case "node_modules":
            case "src":
            case ".m2":
                return true;
            default:
                return name.startsWith(".") && !name.equals(".");
        }
    }

    private static void collectXml(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".xml")) {
                out.add(f);
            } else if (f.isDirectory()) {
                // Gradle nests per-suite XML one level down (build/test-results/test/*.xml).
                collectXml(f, out);
            }
        }
    }
}

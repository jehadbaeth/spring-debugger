package com.springdebugger.extractor;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;

/**
 * Discovers a Spring Boot application's configured log file by reading {@code logging.file.name}
 * (or the legacy {@code logging.file}) from {@code application*.properties} / {@code application*.yml}.
 * Used to offer near-zero-config log tailing for {@code bootRun} runs started in a terminal.
 *
 * <p>The property parsing is pure and unit-tested; {@link #discover(File)} layers a small file
 * search on top.
 */
public final class LogFilePropertyFinder {

    private LogFilePropertyFinder() {}

    /** Reads {@code logging.file.name}/{@code logging.file} from .properties content, or null. */
    public static String fromProperties(String content) {
        if (content == null) return null;
        try {
            Properties p = new Properties();
            p.load(new java.io.StringReader(content));
            String v = p.getProperty("logging.file.name");
            if (v == null) v = p.getProperty("logging.file");
            return blankToNull(v);
        } catch (Exception e) {
            return null;
        }
    }

    /** Reads {@code logging.file.name}/{@code logging.file} from YAML content, or null. */
    @SuppressWarnings("unchecked")
    public static String fromYaml(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            Object root = new Yaml().load(content);
            if (!(root instanceof Map)) return null;
            Map<String, Object> map = (Map<String, Object>) root;
            // Support both nested (logging: file: name:) and flattened (logging.file.name:) forms.
            String flat = nestedString(map, "logging.file.name");
            if (flat == null) flat = nestedString(map, "logging.file");
            return blankToNull(flat);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static String nestedString(Map<String, Object> map, String dottedKey) {
        // Try the flattened key first, then walk the nested maps.
        Object direct = map.get(dottedKey);
        if (direct != null) return direct.toString();
        Object node = map;
        for (String part : dottedKey.split("\\.")) {
            if (!(node instanceof Map)) return null;
            node = ((Map<String, Object>) node).get(part);
            if (node == null) return null;
        }
        return node.toString();
    }

    /** Searches the project base for an application config that declares a log file; null if none. */
    public static String discover(File base) {
        if (base == null || !base.isDirectory()) return null;
        String[] names = {
                "application-local.properties", "application.properties",
                "application-local.yml", "application.yml",
                "application-local.yaml", "application.yaml"
        };
        for (String name : names) {
            File f = findFirst(base, name, 0);
            if (f == null) continue;
            String content = read(f);
            String found = name.endsWith(".properties") ? fromProperties(content) : fromYaml(content);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Finds every {@code application*.properties/yml} under the tree that declares a log file and
     * returns the resolved log {@link File} for each, de-duplicated. Relative paths are resolved
     * against the owning module (the directory above {@code src/.../resources}), because that is the
     * working directory Spring Boot uses, so a multi-module project with one
     * {@code logging.file.name} per service yields one log file per service.
     */
    public static java.util.List<File> discoverAll(File base) {
        java.util.LinkedHashSet<File> out = new java.util.LinkedHashSet<>();
        if (base == null || !base.isDirectory()) return new java.util.ArrayList<>(out);
        java.util.List<File> configs = new java.util.ArrayList<>();
        collectConfigs(base, 0, configs);
        for (File cfg : configs) {
            String content = read(cfg);
            String declared = cfg.getName().endsWith(".properties")
                    ? fromProperties(content) : fromYaml(content);
            if (declared == null) continue;
            out.add(resolveAgainstModule(cfg, declared));
        }
        return new java.util.ArrayList<>(out);
    }

    private static void collectConfigs(File dir, int depth, java.util.List<File> out) {
        if (depth > 10) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isFile()) {
                String n = c.getName();
                boolean isConfig = (n.startsWith("application") )
                        && (n.endsWith(".properties") || n.endsWith(".yml") || n.endsWith(".yaml"));
                if (isConfig) out.add(c);
            } else if (c.isDirectory()) {
                String n = c.getName();
                // Config lives under src/main/resources, so do NOT prune src here; only skip caches.
                if (n.equals(".git") || n.equals(".gradle") || n.equals(".idea")
                        || n.equals("node_modules") || n.equals("build") || n.equals("target")
                        || n.equals(".m2")) continue;
                collectConfigs(c, depth + 1, out);
            }
        }
    }

    /** Resolves a declared log path against the module owning a config file. */
    static File resolveAgainstModule(File configFile, String declaredPath) {
        File f = new File(declaredPath);
        if (f.isAbsolute()) return f;
        String path = configFile.getPath().replace(File.separatorChar, '/');
        int srcIdx = path.indexOf("/src/");
        File moduleRoot;
        if (srcIdx > 0) {
            moduleRoot = new File(path.substring(0, srcIdx));
        } else {
            // Not under a standard src layout; fall back to the config file's directory.
            moduleRoot = configFile.getParentFile();
        }
        return new File(moduleRoot, declaredPath);
    }

    private static File findFirst(File dir, String fileName, int depth) {
        if (depth > 8) return null;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File c : children) {
            if (c.isFile() && c.getName().equals(fileName)) return c;
        }
        for (File c : children) {
            if (c.isDirectory()) {
                String n = c.getName();
                if (n.equals(".git") || n.equals(".gradle") || n.equals("node_modules") || n.equals("build")
                        || n.equals("target") || n.startsWith(".")) continue;
                File hit = findFirst(c, fileName, depth + 1);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

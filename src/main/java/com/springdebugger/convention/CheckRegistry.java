package com.springdebugger.convention;

import com.springdebugger.convention.checks.JavadocRequiredCheck;
import com.springdebugger.convention.checks.RobotMetadataRequiredCheck;
import com.springdebugger.convention.checks.RobotTestCaseDocumentationCheck;
import com.springdebugger.convention.checks.RobotTestCaseTagsCheck;
import com.springdebugger.convention.checks.RobotTestIdFormatCheck;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps a checkType string to its {@link ConventionCheck} implementation. Adding a new check type
 * (for example the Robot Framework template check) is: implement {@link ConventionCheck}, register
 * it here, and author rules that reference it.
 */
public final class CheckRegistry {

    private static final Map<String, ConventionCheck> CHECKS;

    static {
        Map<String, ConventionCheck> m = new HashMap<>();
        register(m, new JavadocRequiredCheck());
        register(m, new RobotMetadataRequiredCheck());
        register(m, new RobotTestIdFormatCheck());
        register(m, new RobotTestCaseDocumentationCheck());
        register(m, new RobotTestCaseTagsCheck());
        CHECKS = Map.copyOf(m);
    }

    private CheckRegistry() {}

    private static void register(Map<String, ConventionCheck> m, ConventionCheck check) {
        m.put(check.checkType(), check);
    }

    public static ConventionCheck get(String checkType) { return CHECKS.get(checkType); }
    public static boolean has(String checkType) { return CHECKS.containsKey(checkType); }
    public static Set<String> checkTypes() { return CHECKS.keySet(); }
}

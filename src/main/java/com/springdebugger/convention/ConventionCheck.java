package com.springdebugger.convention;

import com.intellij.psi.PsiFile;

import java.util.List;

/**
 * One convention check implementation, selected by {@link ConventionRule#getCheckType()}. Each
 * implementation is a pure function of the file plus the rule's params: it reads the file's PSI and
 * returns the violations, with no IO and no cross-file resolution. That purity is what keeps the
 * checks deterministic and testable (see CONVENTIONS-PLAN.md section 4.3).
 */
public interface ConventionCheck {

    /** The checkType string this implementation handles, matching the catalog's `checkType` field. */
    String checkType();

    /** Find every violation of the given rule in the given file. */
    List<Violation> check(PsiFile file, ConventionRule rule);
}

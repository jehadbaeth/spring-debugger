package com.springdebugger.convention;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * One convention violation found by a {@link ConventionCheck}: the PSI element to highlight, the
 * already-interpolated plain-language message and fix text, and an optional {@link TextRange}.
 *
 * <p>When {@code range} is null the whole {@code anchor} element is highlighted (the natural choice
 * for a structured language like Java, anchoring to a method name). When {@code range} is set, the
 * highlight covers that sub-range of {@code anchor} (used for plain-text files such as Robot suites,
 * where the anchor is the whole file element and the range pins the offending line).
 */
public record Violation(PsiElement anchor, TextRange range, String message, String fix) {

    public Violation(PsiElement anchor, String message, String fix) {
        this(anchor, null, message, fix);
    }
}

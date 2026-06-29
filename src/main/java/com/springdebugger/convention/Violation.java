package com.springdebugger.convention;

import com.intellij.psi.PsiElement;

/**
 * One convention violation found by a {@link ConventionCheck}: the PSI element to highlight, and the
 * already-interpolated plain-language message and fix text.
 */
public record Violation(PsiElement anchor, String message, String fix) {}

package com.springdebugger.convention;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.springdebugger.convention.checks.JavadocRequiredCheck;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Exercises the Javadoc check against real in-memory PSI, one fixture per param branch. Drives the
 * check directly (no inspection registration) so the parsing logic is proven in isolation.
 */
public class JavadocRequiredCheckTest extends LightJavaCodeInsightFixtureTestCase {

    private final JavadocRequiredCheck check = new JavadocRequiredCheck();

    /** The seed rule from the shipped catalog: public-only, skip overrides, skip accessors. */
    private ConventionRule rule() {
        return ConventionCatalog.load().byId("JAVADOC_METHOD");
    }

    private List<String> violatingMethods(String javaSource) {
        PsiFile file = myFixture.configureByText("Sample.java", javaSource);
        return check.check(file, rule()).stream()
                .map(v -> v.anchor().getText())
                .collect(Collectors.toList());
    }

    public void testPublicMethodWithoutJavadocIsFlagged() {
        assertEquals(List.of("doWork"),
                violatingMethods("public class Sample { public void doWork() {} }"));
    }

    public void testPublicMethodWithJavadocIsNotFlagged() {
        assertTrue(violatingMethods(
                "public class Sample { /** Does the work. */ public void doWork() {} }").isEmpty());
    }

    public void testPrivateAndPackageMethodsAreNotFlagged() {
        assertTrue(violatingMethods(
                "public class Sample { private void a() {} void b() {} }").isEmpty());
    }

    public void testOverrideMethodIsExempt() {
        assertTrue(violatingMethods(
                "public class Sample { @Override public String toString() { return \"\"; } }").isEmpty());
    }

    public void testGettersAndSettersAreExempt() {
        assertTrue(violatingMethods(
                "public class Sample { private int x; "
                + "public int getX() { return x; } "
                + "public void setX(int x) { this.x = x; } "
                + "public boolean isReady() { return true; } }").isEmpty());
    }

    public void testConstructorIsNeverFlagged() {
        assertTrue(violatingMethods(
                "public class Sample { public Sample() {} }").isEmpty());
    }

    public void testMultipleMethodsAllReported() {
        assertEquals(List.of("first", "second"),
                violatingMethods("public class Sample { public void first() {} public void second() {} }"));
    }
}

package com.springdebugger.convention;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.springdebugger.convention.checks.ApiVersionPathCheck;
import com.springdebugger.convention.checks.EntityStringFieldBoundedCheck;
import com.springdebugger.convention.checks.FieldInjectionForbiddenCheck;
import com.springdebugger.convention.checks.NoSystemOutErrCheck;
import com.springdebugger.convention.checks.OptionalUsageCheck;
import com.springdebugger.convention.checks.RequestBodyRequiresValidCheck;
import com.springdebugger.convention.checks.ServiceClassNamingCheck;
import com.springdebugger.convention.checks.TransactionalMisplacedCheck;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Exercises the Spring Boot convention checks (docs/spring-boot-conventions.md) against real
 * in-memory PSI, one positive and one negative fixture per check. Annotations are used unresolved
 * (no Spring/Jakarta classpath in these fixtures) since the checks match by simple name.
 */
public class SpringBootChecksTest extends LightJavaCodeInsightFixtureTestCase {

    private ConventionRule rule(String id) {
        return ConventionCatalog.load().byId(id);
    }

    private List<String> anchors(ConventionCheck check, ConventionRule rule, String javaSource) {
        PsiFile file = myFixture.configureByText("Sample.java", javaSource);
        return check.check(file, rule).stream().map(v -> v.anchor().getText()).collect(Collectors.toList());
    }

    // ── fieldInjectionForbidden ─────────────────────────────────────────────

    public void testFieldInjectionIsFlagged() {
        assertEquals(List.of("repo"), anchors(new FieldInjectionForbiddenCheck(), rule("FIELD_INJECTION_FORBIDDEN"),
                "public class Sample { @Autowired private Object repo; }"));
    }

    public void testConstructorInjectedFieldIsNotFlagged() {
        assertTrue(anchors(new FieldInjectionForbiddenCheck(), rule("FIELD_INJECTION_FORBIDDEN"),
                "public class Sample { private final Object repo; public Sample(Object repo) { this.repo = repo; } }").isEmpty());
    }

    // ── noSystemOutErr ──────────────────────────────────────────────────────

    public void testSystemOutIsFlagged() {
        assertEquals(List.of("System.out"), anchors(new NoSystemOutErrCheck(), rule("NO_SYSTEM_OUT_ERR"),
                "public class Sample { void run() { System.out.println(\"hi\"); } }"));
    }

    public void testLoggerCallIsNotFlagged() {
        assertTrue(anchors(new NoSystemOutErrCheck(), rule("NO_SYSTEM_OUT_ERR"),
                "public class Sample { void run() { log.debug(\"hi\"); } }").isEmpty());
    }

    // ── optionalUsage (parameter) ───────────────────────────────────────────

    public void testOptionalParameterIsFlagged() {
        assertEquals(List.of("id"), anchors(new OptionalUsageCheck(), rule("OPTIONAL_AS_PARAMETER"),
                "import java.util.Optional; public class Sample { void run(Optional<String> id) {} }"));
    }

    public void testPlainParameterIsNotFlagged() {
        assertTrue(anchors(new OptionalUsageCheck(), rule("OPTIONAL_AS_PARAMETER"),
                "public class Sample { void run(String id) {} }").isEmpty());
    }

    // ── optionalUsage (field) ───────────────────────────────────────────────

    public void testOptionalFieldIsFlagged() {
        assertEquals(List.of("id"), anchors(new OptionalUsageCheck(), rule("OPTIONAL_AS_FIELD"),
                "import java.util.Optional; public class Sample { Optional<String> id; }"));
    }

    public void testOptionalReturnTypeIsNotFlaggedAsField() {
        assertTrue(anchors(new OptionalUsageCheck(), rule("OPTIONAL_AS_FIELD"),
                "import java.util.Optional; public class Sample { Optional<String> find() { return null; } }").isEmpty());
    }

    // ── transactionalMisplaced ──────────────────────────────────────────────

    public void testTransactionalOnRepositoryMethodIsFlagged() {
        assertEquals(List.of("@Transactional"), anchors(new TransactionalMisplacedCheck(), rule("TRANSACTIONAL_MISPLACED"),
                "@Repository public class SampleRepository { @Transactional void save() {} }"));
    }

    public void testTransactionalOnServiceIsNotFlagged() {
        assertTrue(anchors(new TransactionalMisplacedCheck(), rule("TRANSACTIONAL_MISPLACED"),
                "@Service public class SampleService { @Transactional void save() {} }").isEmpty());
    }

    // ── entityStringFieldBounded ────────────────────────────────────────────

    public void testUnboundedEntityStringFieldIsFlagged() {
        assertEquals(List.of("name"), anchors(new EntityStringFieldBoundedCheck(), rule("ENTITY_STRING_FIELD_BOUNDED"),
                "@Entity public class Sample { String name; }"));
    }

    public void testBoundedEntityStringFieldIsNotFlagged() {
        assertTrue(anchors(new EntityStringFieldBoundedCheck(), rule("ENTITY_STRING_FIELD_BOUNDED"),
                "@Entity public class Sample { @Size(max = 64) String name; }").isEmpty());
    }

    public void testStringFieldOnNonEntityIsNotFlagged() {
        assertTrue(anchors(new EntityStringFieldBoundedCheck(), rule("ENTITY_STRING_FIELD_BOUNDED"),
                "public class Sample { String name; }").isEmpty());
    }

    // ── requestBodyRequiresValid ────────────────────────────────────────────

    public void testRequestBodyWithoutValidIsFlagged() {
        assertEquals(List.of("dto"), anchors(new RequestBodyRequiresValidCheck(), rule("REQUEST_BODY_REQUIRES_VALID"),
                "public class Sample { void create(@RequestBody Object dto) {} }"));
    }

    public void testRequestBodyWithValidIsNotFlagged() {
        assertTrue(anchors(new RequestBodyRequiresValidCheck(), rule("REQUEST_BODY_REQUIRES_VALID"),
                "public class Sample { void create(@Valid @RequestBody Object dto) {} }").isEmpty());
    }

    // ── serviceClassNaming ──────────────────────────────────────────────────

    public void testServiceClassWithoutSuffixIsFlagged() {
        assertEquals(List.of("Screening"), anchors(new ServiceClassNamingCheck(), rule("SERVICE_CLASS_NAMING"),
                "@Service public class Screening {}"));
    }

    public void testServiceClassWithSuffixIsNotFlagged() {
        assertTrue(anchors(new ServiceClassNamingCheck(), rule("SERVICE_CLASS_NAMING"),
                "@Service public class ScreeningService {}").isEmpty());
    }

    // ── apiVersionPath ──────────────────────────────────────────────────────

    public void testUnversionedControllerPathIsFlagged() {
        assertEquals(List.of("@RequestMapping(\"/propagation\")"), anchors(new ApiVersionPathCheck(), rule("API_VERSION_PATH"),
                "@RequestMapping(\"/propagation\") public class Sample {}"));
    }

    public void testVersionedControllerPathIsNotFlagged() {
        assertTrue(anchors(new ApiVersionPathCheck(), rule("API_VERSION_PATH"),
                "@RequestMapping(\"/api/v1/propagation\") public class Sample {}").isEmpty());
    }

    public void testNonLiteralControllerPathIsSkipped() {
        assertTrue(anchors(new ApiVersionPathCheck(), rule("API_VERSION_PATH"),
                "public class Sample { static final String PATH = \"/propagation\"; } "
                + "@RequestMapping(Sample.PATH) class Controller {}").isEmpty());
    }
}

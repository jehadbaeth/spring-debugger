# Spring Boot Debugger Plugin: Implementation Plan

**Target IDE:** IntelliJ IDEA (Community and Ultimate)  
**Implementation language:** Java (plugin) + YAML (rule catalog)  
**Mode:** Offline rule engine first; LLM (Ollama) deferred behind a config switch  
**Status:** Living document. Update catalog entries as rules are implemented and validated.

---

## 1. Goal and Scope

The plugin monitors all output streams produced during compile, run, and test phases. When a Spring Boot error is detected it surfaces a one-to-two sentence card: one sentence naming the exact problem, one sentence giving the fix. No stack-trace reading required from the developer.

### In scope

- Spring Boot application startup and runtime failures
- Spring Boot test context failures
- Build/packaging failures that are Spring-specific (fat jar, Lombok, annotation processing)
- OpenAPI/springdoc integration errors
- JPA, Security, Jackson, and MVC configuration errors

### Out of scope

- Generic Java compiler errors (IntelliJ already explains those better than we can)
- Non-Spring framework errors
- Errors in languages other than Java (Kotlin support is a future extension)

### Compile-time note

The guide's own introduction is correct: Spring Boot externalises almost all error detection to runtime. The compile-time surface we own is thin and specific: Lombok annotation processing not running, Java version mismatches, and Spring configuration processor metadata failures. We handle those under section 12 of the catalog and nothing more.

---

## 2. Detection Sources and Phase Mapping

Each phase surfaces errors through a different IntelliJ API. A rule can apply to more than one phase if the same pattern fires in multiple streams.

| Tap ID | IntelliJ API | Phase | What it catches |
|---|---|---|---|
| `BUILD_OUTPUT` | `CompileContext`, `BuildManagerListener`, Maven/Gradle build tool window | COMPILE | Annotation processor failures, Lombok not generating, Java version mismatch |
| `RUN_CONSOLE` | `ProcessListener` / `ProcessAdapter` on `ProcessHandler` from a run configuration | STARTUP, RUNTIME | FailureAnalyzer banners, Caused-by chains, port conflicts, datasource failures, all of sections 1 through 8 and 10 through 11 |
| `TEST_CONSOLE` | `SMTRunnerEventsListener`, test process `ProcessHandler` | TEST | Failed to load ApplicationContext in tests, MockBean mismatches, slice errors (sections 9 and echoes of 1, 2, 3) |
| `PSI` | `JavaPsiFacade`, IntelliJ inspections | COMPILE, STARTUP | Optional enrichment: confirm a class lacks `@Component`, a method is non-public, a package path relative to the main class. Never the primary signal; always secondary. |

**IntelliJ Ultimate note:** The Spring plugin in Ultimate exposes a bean graph and context model through `SpringModel` and `SpringBeanService`. If Ultimate is detected at runtime the enrichment layer can query this for much richer context. The fallback (for Community) is PSI-only. This is an optional enrichment that must not break the Community build.

### Phase labels used in the catalog

- `STARTUP` means the RUN_CONSOLE tap during application context refresh (before the app accepts requests)
- `RUNTIME` means the RUN_CONSOLE tap after the app is running
- `TEST` means the TEST_CONSOLE tap
- `COMPILE` means the BUILD_OUTPUT tap
- A rule may list multiple phases separated by a comma

---

## 3. Pipeline Architecture

```
[Tap: BUILD_OUTPUT] ─┐
[Tap: RUN_CONSOLE]  ─┤─► [Extractor] ─► [Classifier] ─► [Enricher] ─► [Synthesizer] ─► [UI Card]
[Tap: TEST_CONSOLE] ─┘
```

### 3.1 Extractor

Reads the raw output and pulls structured signals:

- The innermost `Caused by:` exception class and message
- The Spring Boot FailureAnalyzer banner block (between the asterisk rows): `Description:` and `Action:` lines
- The failing bean name (from "Error creating bean with name '...'")
- The HTTP status code and path (for runtime web errors)
- The relevant build tool error line (for compile phase)

The extractor does not classify. It produces a `RawSignal` object consumed by the classifier.

### 3.2 Classifier

Matches the `RawSignal` against the rule catalog loaded from YAML. Each rule carries:

- One or more regex patterns targeting specific fields of `RawSignal`
- A confidence level when the rule fires (HIGH for banner-based matches, MEDIUM for exception-class matches, LOW for heuristic matches)

The classifier returns a `ClassifierResult`: the matched rule ID, the confidence, and the filled-in diagnosis and fix templates.

When no rule fires (confidence stays below threshold): the result is `{ruleId: null, confidence: NONE}`. This is the LLM fallback trigger once that mode is enabled.

### 3.3 Enricher

Optional second pass. Only runs when the classifier returns MEDIUM or LOW confidence. The enricher may:

- Read the PSI tree to confirm a structural claim in the diagnosis (e.g. "class X lacks @Component")
- Query the Actuator endpoint if the app is running and the port is known
- Check the dependency tree from the build model for missing starters

### 3.4 Synthesizer

Fills the diagnosis and fix templates from the matched rule using the extracted signal values (bean name, exception message, port number, etc.) and produces the final `DiagnosisCard`.

### 3.5 Output contract

Every path (offline rule engine and future LLM) produces the same type:

```java
record DiagnosisCard(
    String ruleId,           // e.g. "2.1" or "llm" for LLM-generated
    Phase phase,             // COMPILE | STARTUP | RUNTIME | TEST
    String diagnosisSentence,
    String fixSentence,
    Confidence confidence,   // HIGH | MEDIUM | LOW | NONE
    String excerpt           // the log snippet that triggered this
) {}
```

---

## 4. Rule Catalog Format

Rules live in `src/main/resources/rules/spring-boot-rules.yaml`. This file is the source of truth. The Java engine loads it at plugin startup. Do not hardcode rules in Java.

Each rule entry:

```yaml
- id: "2.1"
  name: "NoSuchBeanDefinitionException"
  phases: [STARTUP, TEST]
  taps: [RUN_CONSOLE, TEST_CONSOLE]
  signals:
    caused_by_class: "org.springframework.beans.factory.NoSuchBeanDefinitionException"
    message_contains: "required a bean of type"
  diagnosis: "Spring cannot find a bean of type {{beanType}} because no class with that type is registered in the application context."
  fix: "Annotate {{beanType}} with @Service, @Component, or @Repository, or declare it as an @Bean method in a @Configuration class, and confirm it lives inside the package tree rooted at your @SpringBootApplication class."
  confidence: HIGH
  fixture: "fixtures/2.1-no-such-bean.log"
  status: TODO
```

Field definitions:

| Field | Required | Notes |
|---|---|---|
| `id` | yes | Mirrors the guide section numbering |
| `name` | yes | Short human label |
| `phases` | yes | One or more of: COMPILE, STARTUP, RUNTIME, TEST |
| `taps` | yes | One or more of: BUILD_OUTPUT, RUN_CONSOLE, TEST_CONSOLE, PSI |
| `signals` | yes | At minimum one pattern; see signal keys below |
| `diagnosis` | yes | One sentence; `{{variable}}` for runtime-substituted values |
| `fix` | yes | One sentence; same substitution |
| `confidence` | yes | HIGH when the signal is unambiguous; MEDIUM when heuristic |
| `fixture` | yes | Relative path to a real log file that should match this rule |
| `status` | yes | TODO / IN_PROGRESS / DONE |

Signal keys:

| Key | Matches against |
|---|---|
| `caused_by_class` | The fully qualified exception class of the deepest Caused by |
| `caused_by_message` | The message string of the deepest Caused by |
| `message_contains` | Any line in the extracted signal block |
| `banner_description_contains` | The Description: field of the FailureAnalyzer banner |
| `banner_action_contains` | The Action: field of the FailureAnalyzer banner |
| `build_line_contains` | A line from BUILD_OUTPUT tap |
| `http_status` | The HTTP status code (runtime errors) |
| `exception_class` | The top-level exception class (not deepest Caused by) |

---

## 5. Rule Catalog

All entries below are in TODO status until a real fixture log is collected and the regex is validated against it. The signal descriptions are discriminating signals, not final regex patterns. Write final patterns once you hold the real log.

### Section 1: Application Context and Startup Errors (Phase: STARTUP, TEST)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 1.1 | ApplicationContextException | RUN_CONSOLE, TEST_CONSOLE | `banner_description_contains`: "Unable to start web server" OR `caused_by_class` ends with `ApplicationContextException` | "The application context failed to start; the real cause is in the innermost Caused by exception below the banner." | "Scroll to the deepest Caused by in the log and jump to the relevant section; the top-level ApplicationContextException is always a wrapper." |
| 1.2 | BeanDefinitionStoreException | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `BeanDefinitionStoreException` | "Spring could not parse a @Configuration class or @PropertySource, usually due to a bad @Import or a class reference that does not exist." | "Check recently edited @Configuration classes and any @PropertySource or @Import annotations for missing or misspelled class references." |
| 1.3 | BeanCreationException | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `BeanCreationException`; `message_contains`: "Error creating bean with name" | "Spring found the bean '{{beanName}}' but failed during instantiation or wiring for the reason shown in the nested exception." | "Read the nested Caused by to find whether the failure is an unresolved dependency, a @Value placeholder, or a constructor exception, then fix that root cause." |
| 1.4 | BeanInstantiationException | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `BeanInstantiationException`; `message_contains`: "No default constructor" | "Spring cannot instantiate '{{beanType}}' because it has no default constructor or the constructor threw an exception." | "Add a no-arg constructor, or move risky initialisation logic into a @PostConstruct method where exceptions are easier to trace." |
| 1.5 | BeanDefinitionOverrideException | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `BeanDefinitionOverrideException`; `banner_description_contains`: "could not be registered" | "Two beans are registered under the same name '{{beanName}}', which is disallowed by default in Spring Boot 2.1+." | "Rename one bean with @Bean(\"uniqueName\") or remove the duplicate definition; avoid setting spring.main.allow-bean-definition-overriding=true as it hides the real conflict." |
| 1.6 | Component scan misses beans | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `NoSuchBeanDefinitionException`; PSI enrichment: main class package vs bean package | "The class '{{beanType}}' is annotated but lives outside the package tree that @SpringBootApplication scans." | "Move the @SpringBootApplication main class to a parent package that contains all your beans (e.g. com.example instead of com.example.web)." |
| 1.7 | @SpringBootApplication in wrong package | RUN_CONSOLE | same signals as 1.6; PSI: main class in sub-package | "The main class is in '{{mainPackage}}' but beans exist in sibling packages that are never scanned." | "Move the main class up one package level so all component packages become sub-packages of the main class package." |
| 1.8 | Port already in use | RUN_CONSOLE | `banner_description_contains`: "Port {{port}} was already in use" | "Another process is already listening on port {{port}}, so the embedded server cannot bind." | "Run 'lsof -i :{{port}}' to find and kill the other process, or change the port with server.port=8081 in application.properties." |
| 1.9 | Wrong or no active profile | RUN_CONSOLE, TEST_CONSOLE | `message_contains`: "No active profile set, falling back to default profiles" | "No Spring profile is active, so profile-specific configuration files and @Profile beans are not loaded." | "Set spring.profiles.active=yourprofile in application.properties, via the SPRING_PROFILES_ACTIVE environment variable, or with -Dspring.profiles.active=yourprofile as a JVM argument." |
| 1.10 | Failed to load ApplicationContext (tests) | TEST_CONSOLE | `caused_by_class`: `IllegalStateException`; `message_contains`: "Failed to load ApplicationContext" | "The test application context could not be built; the real cause is in the nested exception." | "Read the nested Caused by to find the missing bean or configuration failure, then provide the missing collaborator via @MockitoBean or widen the test slice." |
| 1.11 | No main manifest attribute | BUILD_OUTPUT | `build_line_contains`: "no main manifest attribute" OR "Unable to find main class" | "The Spring Boot plugin could not determine a single main class, so the fat jar has no runnable entry point." | "Set the main class explicitly in your build file: in Maven use <start-class>com.example.App</start-class>; in Gradle use springBoot { mainClass = 'com.example.App' }." |
| 1.12 | Auto-configuration not applied | RUN_CONSOLE | `message_contains`: "UserDetailsService bean"; OR no DataSource/embedded server messages; add --debug and parse negative-match section | "The auto-configuration for '{{feature}}' was not applied because the required starter dependency is missing from the classpath." | "Add the missing starter (e.g. spring-boot-starter-data-jpa or spring-boot-starter-web) and re-run; use --debug to print the auto-configuration report and confirm." |
| 1.13 | BeanCurrentlyInCreationException (startup) | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `BeanCurrentlyInCreationException` | "A circular dependency was detected during startup: {{cycleDescription}}." | "Break the cycle by extracting shared logic into a third bean; as a last resort use @Lazy on one injection point, or set spring.main.allow-circular-references=true only while debugging." |

### Section 2: Dependency Injection Errors (Phase: STARTUP, TEST)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 2.1 | NoSuchBeanDefinitionException | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `NoSuchBeanDefinitionException`; `message_contains`: "required a bean of type" | "Spring cannot find a bean of type '{{beanType}}'; no class with that type is registered in the context." | "Annotate the class with @Service, @Component, or @Repository, or declare it as an @Bean; confirm it is inside the package tree rooted at your @SpringBootApplication class." |
| 2.2 | NoUniqueBeanDefinitionException | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `NoUniqueBeanDefinitionException`; `message_contains`: "required a single bean, but" | "More than one bean of type '{{beanType}}' exists ({{candidates}}), and Spring cannot choose between them." | "Mark one implementation @Primary, or use @Qualifier(\"beanName\") at the injection point to specify which one you want." |
| 2.3 | UnsatisfiedDependencyException | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `UnsatisfiedDependencyException` | "Bean '{{beanName}}' has an unsatisfied dependency; read the nested exception to find whether the dependency is missing (2.1) or ambiguous (2.2)." | "Resolve the nested NoSuchBeanDefinitionException or NoUniqueBeanDefinitionException shown below this line." |
| 2.4 | Missing stereotype annotation | RUN_CONSOLE, TEST_CONSOLE | same as 2.1 + PSI enrichment: class exists but lacks any stereotype | "The class '{{beanType}}' exists but has no Spring stereotype annotation, so it is invisible to component scanning." | "Add @Component, @Service, @Repository, or @Controller to the class, or declare it as an @Bean in a @Configuration class." |
| 2.5 | Missing @Bean method | RUN_CONSOLE, TEST_CONSOLE | same as 2.1 + type is a third-party class (PSI: no source attachment or from library) | "No @Bean factory method was declared for the third-party type '{{beanType}}', so Spring cannot create it." | "Add a @Bean method in a @Configuration class that constructs and returns a configured instance of '{{beanType}}'." |
| 2.6 | Field injection NPE (new instead of inject) | RUNTIME | `caused_by_class`: `NullPointerException`; PSI enrichment: field is @Autowired but class instantiated with new | "An @Autowired field is null because the object was created with 'new' instead of obtained from the Spring context." | "Inject the bean instead of constructing it; never use 'new' on Spring-managed classes, as autowired fields are never populated in manually constructed instances." |
| 2.7 | Circular dependency | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `BeanCurrentlyInCreationException`; `message_contains`: "form a cycle" OR "depends on" | "Beans {{cycleDescription}} depend on each other in a cycle that Spring cannot resolve with constructor injection." | "Refactor to break the cycle by extracting shared logic into a third bean; use @Lazy on one injection point only as a temporary measure." |
| 2.8 | @Autowired on static field | STARTUP | `message_contains`: "static field"; PSI enrichment: @Autowired on static member | "Spring cannot inject into a static field; the field '{{fieldName}}' will always be null." | "Remove the static modifier, or move the configuration into a properly injected non-static bean." |
| 2.9 | Prototype in singleton (stale instance) | RUNTIME | No hard exception; PSI enrichment: @Scope(prototype) injected into @Singleton without ObjectProvider | "A prototype-scoped bean is injected into a singleton, so the same prototype instance is reused for every call instead of a fresh one." | "Inject ObjectProvider<T> instead and call getObject() each time a fresh instance is needed, or annotate the prototype bean with proxyMode = ScopedProxyMode.TARGET_CLASS." |
| 2.10 | @Qualifier not resolving | RUN_CONSOLE | `caused_by_class`: `NoUniqueBeanDefinitionException` despite @Qualifier present (PSI check) | "The @Qualifier value '{{qualifier}}' does not match any registered bean name; the qualifier string must match the bean name exactly." | "Check that the @Qualifier value matches the bean name (which defaults to the decapitalised class name), or use @Primary on the intended default implementation." |
| 2.11 | Self-invocation defeats @Transactional/@Async | RUNTIME | No exception; PSI enrichment: method calls this.annotatedMethod() where annotation is @Transactional or @Async | "Calling '{{methodName}}' via 'this' bypasses the Spring proxy, so the @{{annotation}} advice never runs." | "Move '{{methodName}}' to a separate Spring bean and call it through that bean, or inject a self-reference using @Lazy to force proxy routing." |
| 2.12 | Required optional injection failing | RUN_CONSOLE | `caused_by_class`: `NoSuchBeanDefinitionException`; bean is conditionally present under a profile | "An injection point requires a bean that is only present under certain profiles or conditions, causing startup to fail when those conditions are not met." | "Mark the injection as optional using @Autowired(required=false), Optional<T>, or ObjectProvider<T>, then handle the absent case explicitly." |

### Section 3: Configuration and Properties Errors (Phase: STARTUP, TEST)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 3.1 | Could not resolve placeholder | RUN_CONSOLE, TEST_CONSOLE | `caused_by_class`: `IllegalArgumentException`; `message_contains`: "Could not resolve placeholder" | "The @Value placeholder '{{placeholder}}' references a property that is not defined in any active property source." | "Define the property in application.properties (or the active profile file), or add a default value in the annotation: @Value(\"${{{placeholder}}}:defaultValue\")." |
| 3.2 | @ConfigurationProperties not bound | RUN_CONSOLE | PSI: class has @ConfigurationProperties but no @EnableConfigurationProperties or @Component | "The @ConfigurationProperties class '{{className}}' is declared but never registered, so all fields retain their default values silently." | "Add @EnableConfigurationProperties({{className}}.class) to a @Configuration class, or annotate the class itself with @Component." |
| 3.3 | Relaxed binding mismatch | RUNTIME | No exception; property has no effect; PSI: camelCase key in YAML where kebab-case expected | "The property key '{{key}}' does not map to the field '{{fieldName}}' because of a naming mismatch between the source format and the field name." | "Use kebab-case in YAML (e.g. max-retry-count) and UPPER_SNAKE_CASE for environment variables; Spring's relaxed binding handles the translation to camelCase fields." |
| 3.4 | Type conversion failure | RUN_CONSOLE | `caused_by_class` contains `ConversionFailedException` OR `BindException`; `message_contains`: "Failed to bind" | "The value '{{value}}' for property '{{key}}' cannot be converted to the expected type '{{targetType}}'." | "Fix the value format: Duration accepts '5s' or '200ms', DataSize accepts '10MB', and enum values are matched case-insensitively." |
| 3.5 | YAML syntax error | RUN_CONSOLE | `caused_by_class` contains `YAMLException` OR `ScannerException` | "The YAML file has a syntax error at line {{line}}: {{yamlMessage}}." | "Fix the indentation or syntax at that line; use only spaces (never tabs) and quote any string containing special characters like colons or brackets." |
| 3.6 | YAML type pitfall | RUN_CONSOLE | `message_contains`: value parsed as boolean where string expected; OR value silently truncated | "The YAML value '{{rawValue}}' was interpreted as {{parsedType}} instead of a string because YAML has aggressive implicit typing." | "Quote the value in YAML: '{{rawValue}}' should be written as \"{{rawValue}}\" to prevent implicit type coercion." |
| 3.7 | Property precedence confusion | RUNTIME | PSI or Actuator: property defined in lower-precedence source, overridden elsewhere | "The property '{{key}}' you set is overridden by a higher-precedence source (environment variable or command-line argument)." | "Check /actuator/env to see which source supplies the effective value; command-line args beat env vars, which beat profile files, which beat application.properties." |
| 3.8 | Profile-specific file not loading | RUN_CONSOLE | `message_contains`: "No active profile" + expected properties missing | "The file 'application-{{profile}}.yml' is not loaded because the profile '{{profile}}' is not active." | "Activate the profile with spring.profiles.active={{profile}} and confirm the startup log lists it in the active profiles line." |
| 3.9 | Missing spring.config.import | RUN_CONSOLE | `caused_by_class`: `ConfigDataLocationNotFoundException`; `message_contains`: "config data location" | "The application requires an external config source (Config Server or Vault) declared in spring.config.import, but it could not be reached." | "Add 'optional:' before the import value (e.g. optional:configserver:) to allow startup without the external source, or ensure the source is reachable." |
| 3.10 | Env var not applied | RUNTIME | No exception; property has no effect via env var | "The environment variable '{{envVar}}' is not applied because the key format does not match Spring's env-to-property translation rules." | "Convert dotted property keys to UPPER_SNAKE_CASE for environment variables (e.g. app.timeout becomes APP_TIMEOUT) and verify with /actuator/env." |
| 3.11 | @ConfigurationProperties not registered | RUN_CONSOLE | PSI: @ConfigurationProperties present, no registration; same as 3.2 but distinct signal path | "The @ConfigurationProperties class '{{className}}' is not registered and silently produces all-default values with no error." | "Register it via @EnableConfigurationProperties({{className}}.class) in a @Configuration class, or add @Component to the class." |
| 3.12 | Secrets leaking into logs | RUN_CONSOLE | `message_contains` matches a known secret-like pattern (password=, secret=, token= followed by a non-placeholder value) | "A sensitive value ('{{fieldName}}') appears in plaintext in the log output." | "Remove the field from toString(), store secrets in environment variables or a vault instead of application.yml, and restrict /actuator/env and /actuator/configprops in production." |

### Section 4: Data Access and JPA / Hibernate Errors (Phase: STARTUP, RUNTIME)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 4.1 | Cannot determine embedded database driver | RUN_CONSOLE | `banner_description_contains`: "Cannot determine embedded database driver class" | "The JPA starter is on the classpath but no datasource URL is configured and no embedded database (H2, HSQLDB, Derby) is present." | "Configure spring.datasource.url, username, and password for a real database, or add the H2 dependency for development/test use." |
| 4.2 | Failed to configure a DataSource | RUN_CONSOLE | `banner_description_contains`: "Failed to configure a DataSource" | "Spring Boot cannot auto-configure a DataSource because the 'url' attribute is missing from application properties." | "Provide spring.datasource.url (and driver class if needed), or exclude DataSourceAutoConfiguration if a datasource is genuinely not needed in this context." |
| 4.3 | JDBC driver mismatch | RUN_CONSOLE | `caused_by_class`: `SQLException`; `message_contains`: "No suitable driver found" | "No JDBC driver was found for the URL '{{jdbcUrl}}'; either the driver dependency is missing or the URL scheme is wrong." | "Add the correct JDBC driver dependency for your database and verify the URL scheme matches the driver (e.g. jdbc:postgresql:// requires the PostgreSQL driver)." |
| 4.4 | Connection pool exhaustion | RUNTIME | `caused_by_class`: `SQLTransientConnectionException`; `message_contains`: "Connection is not available, request timed out" | "The HikariCP connection pool is exhausted; all {{poolSize}} connections are held and none was returned within the timeout." | "Enable Hikari leak detection with spring.datasource.hikari.leak-detection-threshold=20000 to find which code path is holding connections; fix the leak before increasing pool size." |
| 4.5 | JpaSystemException / PersistenceException wrapper | RUNTIME | `caused_by_class`: `JpaSystemException` OR `PersistenceException` | "A JPA operation failed; the real cause is several Caused by levels below this wrapper exception." | "Read the deepest Caused by (usually a SQL error or constraint message from the JDBC driver) and fix that root issue." |
| 4.6 | Not a managed type | RUN_CONSOLE | `caused_by_class`: `IllegalArgumentException`; `message_contains`: "Not a managed type" | "The class '{{entityClass}}' is not recognised as a JPA entity, either because it lacks @Entity or because it is outside Hibernate's entity scan path." | "Add @Entity to the class, and if it is outside the main package tree add @EntityScan(basePackages=\"com.example.entities\") to a @Configuration class." |
| 4.7 | Missing @Entity, @Id, or no-arg constructor | RUN_CONSOLE | `caused_by_class` contains `MappingException`; `message_contains`: "No identifier specified" OR "No default constructor" | "The entity class '{{entityClass}}' is missing a required JPA element: @Id, a no-arg constructor, or both." | "Add @Id (and @GeneratedValue if using auto-increment), and ensure a no-arg constructor exists; with Lombok add @NoArgsConstructor." |
| 4.8 | LazyInitializationException | RUNTIME | `caused_by_class`: `LazyInitializationException`; `message_contains`: "could not initialize proxy - no Session" | "A lazily loaded association on '{{entityType}}' was accessed after the Hibernate session closed, typically during JSON serialisation in the controller layer." | "Fetch the association inside the transaction using JOIN FETCH or an @EntityGraph, or map the entity to a DTO before the transaction ends; do not use spring.jpa.open-in-view=true as a workaround." |
| 4.9 | N+1 query problem | RUNTIME | PSI/SQL log: repeated SELECT statements for child entities in a loop; `message_contains` (with show-sql): same SELECT repeated N times | "The query for '{{entityType}}' loads {{count}} child records in {{count}} separate SELECT statements instead of one JOIN." | "Add a JOIN FETCH or @EntityGraph to the repository query, or configure hibernate.default_batch_fetch_size to batch the lookups." |
| 4.10 | @Transactional not applied | RUNTIME | No exception but data not persisted or rollback not happening; PSI: method is non-public or self-invocation | "The @Transactional annotation on '{{methodName}}' has no effect because the method is non-public or called via 'this' instead of through the Spring proxy." | "Make the method public and call it through an injected reference, not via 'this'; self-invocation bypasses the proxy and all AOP advice." |
| 4.11 | TransactionRequiredException | RUNTIME | `caused_by_class`: `TransactionRequiredException`; `message_contains`: "no transaction is in progress" | "A modifying JPA operation was executed without an active transaction." | "Annotate the service method with @Transactional, and for modifying @Query repository methods also add @Modifying." |
| 4.12 | ddl-auto data loss | STARTUP | `message_contains`: "Hibernate: drop table" with `message_contains`: "ddl-auto=create" or "create-drop" | "The ddl-auto setting '{{ddlAuto}}' is causing Hibernate to drop and recreate the schema on startup, which destroys existing data." | "Change spring.jpa.hibernate.ddl-auto to 'validate' or 'none' in production and manage schema changes with Flyway or Liquibase migrations." |
| 4.13 | DataIntegrityViolationException | RUNTIME | `caused_by_class`: `DataIntegrityViolationException`; `message_contains`: "constraint" OR "unique" OR "not-null" OR "foreign key" | "A database write violated the constraint '{{constraintName}}' on table '{{table}}'." | "Validate input before persisting to catch the violation early, and map this exception to a meaningful API error response in your @RestControllerAdvice." |
| 4.14 | OptimisticLockingFailureException | RUNTIME | `caused_by_class`: `ObjectOptimisticLockingFailureException` | "An optimistic lock conflict was detected on '{{entityType}}': another transaction modified the row since you read it." | "This is optimistic locking working correctly; catch the exception and retry the operation, or surface a 'data changed, please reload' message to the user." |
| 4.15 | Detached entity passed to persist | RUNTIME | `caused_by_class`: `PersistenceException`; `message_contains`: "detached entity passed to persist" | "A detached entity with an existing ID was passed to persist(), which only accepts new (transient) entities." | "Use save() or merge() for entities that may already have an ID; use persist() only for entities you know are brand new." |
| 4.16 | MappingException (relationship) | RUN_CONSOLE | `caused_by_class` contains `MappingException`; `message_contains`: "mappedBy" OR "Could not determine type" | "The JPA relationship on '{{entityClass}}.{{fieldName}}' is misconfigured: {{mappingMessage}}." | "Verify both sides of the relationship: the owning side holds the foreign key, the inverse side uses mappedBy pointing at the owning field name." |
| 4.17 | Repository method name not parseable | RUN_CONSOLE | `caused_by_class`: `PropertyReferenceException`; `message_contains`: "No property" | "The Spring Data repository method '{{methodName}}' references a property '{{property}}' that does not exist on '{{entityType}}'." | "Correct the property name in the method name to match the entity field exactly, or replace the derived query with an explicit @Query annotation." |
| 4.18 | InvalidDataAccessApiUsageException | RUNTIME | `caused_by_class`: `InvalidDataAccessApiUsageException` | "A JPA query or API call is malformed: {{errorMessage}}." | "Fix the JPQL syntax or parameter binding in the @Query; named parameters (:name) must have a matching @Param(\"name\") on the method argument." |
| 4.19 | Flyway migration failure | RUN_CONSOLE | `caused_by_class` contains `FlywayException` OR `MigrationException`; `message_contains`: "checksum mismatch" OR "Validate failed" | "Flyway migration version {{version}} failed because {{flywayMessage}}." | "Never edit an already-applied migration; add a new migration to correct the change, or run 'flyway repair' only if the checksum drift was intentional and safe." |
| 4.20 | Naming strategy mismatch | RUN_CONSOLE | `caused_by_class`: `SQLGrammarException`; `message_contains`: "Table" OR "column" + "doesn't exist" | "Hibernate generated the table or column name '{{generatedName}}' using the implicit naming strategy, but the database has a different name." | "Map the table and column names explicitly with @Table(name=\"...\") and @Column(name=\"...\"), or align the database schema to match Hibernate's snake_case naming strategy." |

### Section 5: Web, REST, and MVC Errors (Phase: RUNTIME)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 5.1 | 404 NoHandlerFoundException | RUN_CONSOLE | `http_status`: 404; `caused_by_class`: `NoHandlerFoundException` | "No handler was mapped to '{{method}} {{path}}'; the controller may not be scanned or the path mapping is wrong." | "Verify the exact path including any server.servlet.context-path prefix, confirm the controller class is a registered Spring bean, and enable spring.mvc.throw-exception-if-no-handler-found=true during debugging." |
| 5.2 | 405 Method Not Allowed | RUNTIME | `http_status`: 405 | "The path '{{path}}' exists but does not accept the HTTP method '{{method}}'." | "Check the @GetMapping, @PostMapping, or @RequestMapping annotation on the handler and match it to the HTTP verb the client is sending." |
| 5.3 | 415 Unsupported Media Type / 406 Not Acceptable | RUNTIME | `http_status`: 415 OR 406 | "The {{statusCode}} error means the {{direction}} content type '{{contentType}}' is not supported by this endpoint." | "For 415: set Content-Type: application/json on the request; for 406: ensure the response type is supported and Jackson is on the classpath via spring-boot-starter-web." |
| 5.4 | 400 Bad Request on @RequestBody | RUNTIME | `http_status`: 400; `caused_by_class`: `HttpMessageNotReadableException` OR binding failure | "The request body could not be bound to '{{targetType}}' because the JSON shape does not match the expected structure." | "Enable DEBUG logging for org.springframework.web.servlet.mvc to see the binding error detail, then fix the JSON payload or DTO class to match each other." |
| 5.5 | MethodArgumentNotValidException | RUNTIME | `caused_by_class`: `MethodArgumentNotValidException` | "Bean Validation rejected the request body: {{violationSummary}}." | "This is validation working correctly; handle it with @ExceptionHandler(MethodArgumentNotValidException.class) in a @RestControllerAdvice to return a clean 400 error payload instead of the raw Spring error." |
| 5.6 | Missing request parameter or path variable | RUNTIME | `caused_by_class`: `MissingServletRequestParameterException` OR `MissingPathVariableException` | "The required {{paramType}} '{{paramName}}' is missing from the request." | "Mark the parameter required=false with a defaultValue if it is optional, or ensure the client always supplies it; for path variables, verify the {placeholder} name in the mapping matches the @PathVariable name." |
| 5.7 | HttpMessageNotReadableException | RUNTIME | `caused_by_class`: `HttpMessageNotReadableException`; `message_contains`: "JSON parse error" | "The incoming request body contains malformed JSON: {{parseError}}." | "Fix the client payload (check for trailing commas, unquoted strings, or wrong value types) and return a friendly error via @RestControllerAdvice rather than exposing the raw parser message." |
| 5.8 | Ambiguous mapping | RUN_CONSOLE | `caused_by_class`: `IllegalStateException`; `message_contains`: "Ambiguous mapping" | "Two controller methods are both mapped to '{{method}} {{path}}', which Spring cannot resolve at startup." | "Make the mappings distinct by using different paths, HTTP methods, or 'params' and 'headers' conditions on the @RequestMapping annotation." |
| 5.9 | CORS error | RUNTIME | PSI/client-side: no server-side exception but browser blocks; `message_contains`: "CORS" OR "Cross-Origin" | "The browser is blocking the request from origin '{{origin}}' because no matching CORS configuration was found for '{{path}}'." | "Configure CORS globally via WebMvcConfigurer#addCorsMappings or per-controller with @CrossOrigin, and if Spring Security is present also call .cors() in your SecurityFilterChain." |
| 5.10 | Filter or interceptor ordering | RUNTIME | No clear exception; PSI: multiple @Order or FilterRegistrationBean without explicit order | "A filter or interceptor is executing in the wrong order relative to other filters, causing unexpected behaviour." | "Set an explicit order with @Order(n) or FilterRegistrationBean.setOrder(n) on each filter; lower numbers run earlier." |
| 5.11 | @ControllerAdvice not catching exceptions | RUNTIME | No exception caught; PSI: @ControllerAdvice class not in scanned package | "The @ControllerAdvice '{{adviceClass}}' is not catching exceptions because it is either not a Spring bean, its exception type does not match, or the exception is thrown in a filter before the dispatcher." | "Confirm the advice class is scanned, that the @ExceptionHandler signature matches the thrown exception type (or its supertype), and handle filter-level exceptions separately in the filter or an ErrorController." |
| 5.12 | Static resources swallowing routes | RUNTIME | `http_status`: 404; PSI: static resource mapping overlaps with API path | "A static resource handler or SPA catch-all is intercepting requests to '{{path}}' before they reach the controller." | "Keep API routes under a distinct prefix such as /api/** and scope any SPA fallback mapping to exclude API paths." |
| 5.13 | Servlet vs WebFlux mismatch | RUN_CONSOLE | `caused_by_class`: `IllegalStateException`; `message_contains`: "block()" OR "blocking call" on reactive thread | "A blocking call is being made on a reactive (Netty event loop) thread, which will stall all other requests." | "Pick one model: either remove spring-boot-starter-webflux and use the servlet stack, or replace all blocking IO with reactive alternatives (WebClient, R2DBC) and offload unavoidable blocking work to a bounded scheduler." |

### Section 6: Spring Security Errors (Phase: RUNTIME, STARTUP)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 6.1 | 401 vs 403 confusion | RUNTIME | `http_status`: 401 OR 403 | "The {{statusCode}} response means {{statusMeaning}}, not the other; treat them differently during debugging." | "401 means unauthenticated (fix credentials or token); 403 means authenticated but not authorised (fix roles or security rules)." |
| 6.2 | Unexpected login page | STARTUP | `message_contains`: "Using generated security password" | "Spring Security is active with default configuration, securing all endpoints with a generated password because no SecurityFilterChain bean was defined." | "Define a @Bean of type SecurityFilterChain that declares your own authorization rules and user store, then remove the generated-password configuration." |
| 6.3 | CSRF blocking writes | RUNTIME | `http_status`: 403; `message_contains`: "CSRF" OR "Invalid CSRF token" | "A POST, PUT, or DELETE request is rejected by CSRF protection because no valid CSRF token was included." | "For a stateless REST API disable CSRF with http.csrf(csrf -> csrf.disable()); for browser-session apps keep CSRF enabled and include the token in each state-changing request." |
| 6.4 | WebSecurityConfigurerAdapter removed | COMPILE, RUN_CONSOLE | `build_line_contains`: "cannot find symbol WebSecurityConfigurerAdapter" OR `caused_by_class` related to absent class | "The class WebSecurityConfigurerAdapter was removed in Spring Security 6 and Spring Boot 3; the old extension approach no longer compiles." | "Migrate to the component-based model: expose a @Bean of type SecurityFilterChain and optionally a WebSecurityCustomizer @Bean instead of extending WebSecurityConfigurerAdapter." |
| 6.5 | Password encoder mismatch | RUNTIME | `caused_by_class`: `IllegalArgumentException`; `message_contains`: "There is no PasswordEncoder mapped for the id" | "Passwords stored without a {bcrypt}-style encoding prefix cannot be verified by DelegatingPasswordEncoder." | "Encode all passwords with BCryptPasswordEncoder before storing them, and ensure new passwords are encoded on write; never store or compare plaintext passwords." |
| 6.6 | JWT / OAuth2 token validation failure | RUNTIME | `http_status`: 401; `message_contains`: "expired" OR "invalid signature" OR "issuer" | "The JWT or OAuth2 token was rejected: {{jwtReason}}." | "Verify spring.security.oauth2.resourceserver.jwt.issuer-uri matches the token issuer, check that the token is not expired, and decode the token header/payload to inspect exp, iss, and aud before guessing at the cause." |
| 6.7 | Method security not enforced | RUNTIME | No exception; @PreAuthorize ignored; PSI: @EnableMethodSecurity not present | "The @PreAuthorize or @PostAuthorize annotations are silently ignored because method security is not enabled." | "Add @EnableMethodSecurity to a @Configuration class, and confirm the annotated method is called through the Spring proxy (self-invocation defeats it)." |
| 6.8 | CORS preflight blocked by Security | RUNTIME | `http_status`: 401 or 403 on OPTIONS request | "Spring Security is rejecting the CORS preflight OPTIONS request before it reaches the MVC CORS configuration." | "Call .cors(withDefaults()) in your SecurityFilterChain so Security delegates OPTIONS preflights to your CorsConfigurationSource, and ensure your CORS config permits the expected origin and methods." |

### Section 7: Serialization / Jackson Errors (Phase: RUNTIME)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 7.1 | No serializer found | RUNTIME | `caused_by_class`: `HttpMessageNotWritableException`; `message_contains`: "No serializer found" | "Jackson cannot serialise '{{type}}' because it has no public getters or accessible fields." | "Add public getters to the class, or use a DTO with explicit fields instead of serialising an entity or proxy directly." |
| 7.2 | Infinite recursion on bidirectional relationship | RUNTIME | `caused_by_class`: `StackOverflowError`; `message_contains`: "Infinite recursion" | "Jackson is looping infinitely while serialising the bidirectional relationship between '{{typeA}}' and '{{typeB}}'." | "Break the cycle with @JsonManagedReference and @JsonBackReference, use @JsonIgnore on one side, or map the entity to a DTO before serialising it." |
| 7.3 | UnrecognizedPropertyException | RUNTIME | `caused_by_class`: `UnrecognizedPropertyException` | "The incoming JSON contains the field '{{fieldName}}' which has no matching property in '{{targetType}}'." | "Add the field to the DTO, remove it from the JSON payload, or add @JsonIgnoreProperties(ignoreUnknown=true) if unknown fields should be silently ignored." |
| 7.4 | Cannot construct instance | RUNTIME | `caused_by_class`: `InvalidDefinitionException`; `message_contains`: "no Creators, like default constructor" | "Jackson cannot deserialise '{{type}}' because it has no default constructor and no @JsonCreator." | "Add a no-arg constructor, or annotate an existing constructor with @JsonCreator and annotate each parameter with @JsonProperty." |
| 7.5 | Date/time serialisation failure | RUNTIME | `caused_by_class` contains `InvalidDefinitionException`; `message_contains`: "LocalDate" OR "LocalDateTime" OR "java.time" | "Java 8 date/time type '{{dateType}}' cannot be serialised because the JSR-310 module is not registered with this ObjectMapper." | "If you customised the ObjectMapper, register JavaTimeModule and disable WRITE_DATES_AS_TIMESTAMPS; spring-boot-starter-web registers it automatically for the default ObjectMapper." |
| 7.6 | Naming strategy or @JsonIgnore mismatch | RUNTIME | `http_status`: 400 or field missing from response; PSI: @JsonIgnore on wrong field | "The field '{{fieldName}}' is missing from the JSON output or input because of @JsonIgnore or a naming strategy that renames it." | "Audit @JsonIgnore usage and confirm the naming strategy (spring.jackson.property-naming-strategy) matches what the client expects." |
| 7.7 | Hibernate lazy proxy in JSON | RUNTIME | `caused_by_class`: `InvalidDefinitionException`; `message_contains`: "ByteBuddyInterceptor" OR "HibernateProxy" | "A Hibernate proxy or uninitialized lazy association is being passed to Jackson, which cannot serialise it." | "Map the entity to a DTO inside the transaction before the session closes; if you must serialise entities directly, add jackson-datatype-hibernate and register the Hibernate module." |

### Section 8: OpenAPI / springdoc Errors (Phase: STARTUP, RUNTIME)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 8.1 | Swagger UI 404 | RUNTIME | `http_status`: 404; path matches /swagger-ui or /v3/api-docs | "The Swagger UI is not reachable, either because the springdoc dependency is missing or Security is blocking the UI paths." | "Add springdoc-openapi-starter-webmvc-ui and permit /swagger-ui/**, /v3/api-docs/** in your SecurityFilterChain; also account for any server.servlet.context-path prefix." |
| 8.2 | Generated spec missing endpoints | RUNTIME | PSI: controllers exist but /v3/api-docs omits them | "Some controllers are not included in the generated OpenAPI spec because they are outside the scanned packages or use unsupported annotations." | "Ensure all controllers are in the component-scan path and return typed objects (not Object or ResponseEntity<?>); add springdoc to your Security permitAll list." |
| 8.3 | OpenAPI spec parse failure (codegen) | BUILD_OUTPUT | `build_line_contains`: "Invalid spec" OR "unsupported openapi version" | "The OpenAPI spec file fails to parse during code generation: {{parseMessage}}." | "Validate the spec with an OpenAPI linter before generating, fix any YAML syntax issues (especially indentation), and confirm the openapi: version field matches the generator's supported version." |
| 8.4 | $ref resolution failure | BUILD_OUTPUT | `build_line_contains`: "Could not resolve reference" OR "$ref" | "A $ref in the spec cannot be resolved: {{refPath}}." | "Bundle multi-file specs into a single file before generation, and verify each $ref path is correct relative to the file it appears in; component names are case-sensitive." |
| 8.5 | Generated code does not match schema | BUILD_OUTPUT | PSI: generated model fields differ from schema | "The generated model for '{{schemaName}}' does not match the schema because required, nullable, or format fields are missing from the spec." | "Make the schema explicit (add required, nullable, and format: date-time where needed), regenerate, and never hand-edit generated files as they will be overwritten." |
| 8.6 | springfox / springdoc version conflict | STARTUP | `caused_by_class` contains springfox classes in a Boot 3 project | "springfox is incompatible with Spring Boot 3 and the Jakarta namespace; using it in a Boot 3 project causes startup failures." | "Remove springfox entirely and replace it with springdoc-openapi-starter-webmvc-ui whose version matches your Spring Boot major version." |
| 8.7 | Security scheme missing from UI | RUNTIME | PSI: no @SecurityScheme annotation and /swagger-ui shows no Authorize button | "The Swagger UI has no Authorize button because no SecurityScheme is declared in the OpenAPI configuration." | "Add a @SecurityScheme annotation (or declare it via an OpenAPI @Bean) for your authentication method (bearer/JWT/basic) and reference it on secured operations." |

### Section 9: Testing Errors (Phase: TEST)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 9.1 | Failed to load ApplicationContext in tests | TEST_CONSOLE | `caused_by_class`: `IllegalStateException`; `message_contains`: "Failed to load ApplicationContext" | "The test application context could not be built; read the nested exception for the root cause." | "Provide missing collaborators via @MockitoBean, narrow the test slice with @WebMvcTest or @DataJpaTest, or use a full @SpringBootTest if all beans are genuinely needed." |
| 9.2 | @MockBean not replacing real bean | TEST_CONSOLE | PSI: @Mock used instead of @MockitoBean; or type mismatch | "The real implementation is running instead of the mock because @Mock (plain Mockito) does not replace beans in the Spring context." | "Use @MockitoBean (Boot 3.4+) or @MockBean to replace the bean in the Spring context; plain @Mock only works for non-Spring unit tests." |
| 9.3 | Test slice too wide or narrow | TEST_CONSOLE | `caused_by_class`: `UnsatisfiedDependencyException` in a slice test | "The test slice '{{sliceAnnotation}}' loaded '{{missingBean}}', which is outside the slice's scope and was not mocked." | "Mock the out-of-scope bean with @MockitoBean in the test class, or switch to a @SpringBootTest if the test genuinely needs the full context." |
| 9.4 | @SpringBootTest webEnvironment confusion | TEST_CONSOLE | `caused_by_class` related to connection refused in test; PSI: MOCK webEnvironment with TestRestTemplate | "The test uses MOCK webEnvironment but attempts a real HTTP connection with TestRestTemplate, which has no server to connect to." | "Switch to webEnvironment = RANDOM_PORT when using TestRestTemplate or WebTestClient for real HTTP tests; use MOCK webEnvironment with MockMvc for in-process tests." |
| 9.5 | Test context state leaking | TEST_CONSOLE | Intermittent test failures when run together | "Tests are sharing mutable state through a cached application context; a test that modifies context state causes others to fail." | "Use @DirtiesContext on tests that mutate context state (to force a rebuild), or reset mutable state in @AfterEach; prefer transactional rollback over @DirtiesContext for database tests." |
| 9.6 | Testcontainers not starting | TEST_CONSOLE | `caused_by_class`: `IllegalStateException`; `message_contains`: "Could not find a valid Docker environment" | "Testcontainers cannot start because Docker is not available on this machine or CI runner." | "Ensure Docker is running and accessible; in CI, confirm the runner has Docker available; pin image tags to avoid pull failures on restricted networks." |
| 9.7 | H2 vs real DB dialect difference | TEST_CONSOLE | `caused_by_class`: `SQLGrammarException` in tests with H2 | "The test passes with H2 but a query uses a SQL feature that H2 does not support or emulates differently from the real database." | "Use Testcontainers with the real database engine for dialect-sensitive tests; reserve H2 for fast tests that do not rely on database-specific SQL features." |
| 9.8 | @Transactional rollback hiding behaviour | TEST_CONSOLE | No exception but production failure; PSI: @Transactional on test method | "The @Transactional test rolls back after each test, which means commit-time constraint violations or ID generation side-effects are never exercised." | "Force a flush with entityManager.flush() inside the test to trigger constraint evaluation, or run the test without @Transactional if commit-time behaviour must be verified." |
| 9.9 | UnnecessaryStubbingException | TEST_CONSOLE | `caused_by_class`: `UnnecessaryStubbingException` | "Mockito's strict stubbing detected a stub that was set up but never called during the test." | "Remove the unused stub, or if it is genuinely shared across test cases mark it lenient(); the error usually points at a real mismatch between test assumptions and current code." |
| 9.10 | Flaky test (time, order, async) | TEST_CONSOLE | Intermittent failures; message varies | "The test is non-deterministic because it depends on wall-clock time, execution order, or unsynchronised async results." | "Inject a fixed Clock for time-dependent tests, ensure test independence with @AfterEach cleanup, and wait for async results with Awaitility instead of Thread.sleep." |

### Section 10: Build, Packaging, and Dependency Errors (Phase: COMPILE, STARTUP)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 10.1 | NoSuchMethodError / NoClassDefFoundError | RUN_CONSOLE | `caused_by_class`: `NoSuchMethodError` OR `NoClassDefFoundError` OR `AbstractMethodError` | "Two versions of '{{library}}' are on the classpath; the wrong version loaded at runtime does not have the method '{{method}}' that was compiled against." | "Run 'mvn dependency:tree' or 'gradle dependencies' to find the version conflict, exclude the unwanted transitive version, and let the Spring Boot BOM manage the correct version." |
| 10.2 | Dependency convergence / missing BOM | BUILD_OUTPUT | `build_line_contains`: "dependency convergence" OR multiple version warnings | "Library versions are unmanaged because the Spring Boot BOM is not used, causing unpredictable version selection." | "Inherit from spring-boot-starter-parent or import spring-boot-dependencies as a BOM and remove explicit version declarations for Spring-managed dependencies." |
| 10.3 | ClassNotFoundException at runtime | RUN_CONSOLE | `caused_by_class`: `ClassNotFoundException`; class present at compile time | "The class '{{missingClass}}' was on the compile classpath but is absent at runtime because the dependency is scoped as provided, optional, or test." | "Change the dependency scope to compile or implementation so it is included in the packaged artifact." |
| 10.4 | Fat jar no main manifest | BUILD_OUTPUT | `build_line_contains`: "no main manifest attribute" | "The jar was not built with the Spring Boot plugin, so it lacks the Main-Class manifest entry and the nested-jar class loader." | "Build with the Spring Boot Maven or Gradle plugin (spring-boot:repackage / bootJar) and run the result with 'java -jar app.jar', not with a manual classpath." |
| 10.5 | Lombok not generating code | COMPILE | `build_line_contains`: "cannot find symbol" for getters/setters/builders OR "annotation processing" warning | "Lombok annotations are not generating code because annotation processing is disabled or the Lombok IntelliJ plugin is not installed." | "Enable annotation processing in IntelliJ (Settings > Build > Compiler > Annotation Processors), install the Lombok plugin, and declare Lombok as an annotationProcessor in your build file." |
| 10.6 | Java version mismatch | RUN_CONSOLE, COMPILE | `caused_by_class`: `UnsupportedClassVersionError`; OR `build_line_contains`: "class file has wrong version" | "The classes were compiled with a newer JDK (class version {{compiledVersion}}) than the JRE running them (class version {{runtimeVersion}})." | "Align the sourceCompatibility or <java.version> in the build file with the JDK version on the target machine; verify with 'java -version' on the deployment target." |
| 10.7 | Duplicate classes / split packages | RUN_CONSOLE | `caused_by_class`: `SecurityException`; `message_contains`: "signer information" OR package sealing violation | "The same Java package is provided by two different jars on the classpath, causing a sealed package or signer mismatch." | "Remove the duplicate jar by excluding the transitive dependency that brings in the second copy; inspect 'mvn dependency:tree' to find the duplicate." |
| 10.8 | GraalVM native image reflection failure | RUN_CONSOLE | `caused_by_class`: `ClassNotFoundException` OR `NoSuchMethodException` in a native executable | "The native image is missing a reflection or proxy registration for '{{type}}', which worked on the JVM but cannot be resolved ahead-of-time." | "Add the missing registration via @RegisterReflectionForBinding or a RuntimeHints bean, and run the native compilation in CI to catch these failures early." |

### Section 11: Runtime, Performance, and Operational Errors (Phase: RUNTIME)

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 11.1 | OutOfMemoryError | RUN_CONSOLE | `caused_by_class`: `OutOfMemoryError`; `message_contains`: "Java heap space" OR "Metaspace" | "The JVM ran out of {{memoryArea}} memory." | "Capture a heap dump with -XX:+HeapDumpOnOutOfMemoryError and analyse it to find the leak before raising -Xmx; a leak will exhaust any heap size eventually." |
| 11.2 | Thread pool exhaustion | RUNTIME | `message_contains`: "BLOCKED" in thread dump; all threads waiting on same monitor | "The thread pool is exhausted: all threads are blocked waiting for a resource, likely a slow database or external HTTP call." | "Take a thread dump with jstack, identify the common blocking point, add timeouts to every external call, and isolate slow work onto a separate bounded executor." |
| 11.3 | Slow startup | RUN_CONSOLE | Startup log shows very long time for context refresh | "Application startup is taking unusually long, likely due to eager initialisation of heavy beans or blocking calls in @PostConstruct methods." | "Narrow component scanning, use @Lazy on expensive beans, move slow initialisation off the startup path, and consider Spring AOT or a native image for cold-start-sensitive deployments." |
| 11.4 | Actuator health DOWN | RUNTIME | `message_contains`: "status: DOWN" from /actuator/health | "The health indicator reports DOWN because {{failingComponent}} is not reachable or misconfigured." | "Check the health detail at /actuator/health/{{component}} for the specific failure, fix the connection or configuration, and restrict the health endpoint to trusted networks in production." |
| 11.5 | Logging misconfiguration | RUN_CONSOLE | No log output OR duplicate log lines OR wrong level | "Logging is misconfigured: either two logging backends are present on the classpath or the logback configuration is invalid." | "Use exactly one logging backend (Spring Boot defaults to Logback; exclude Log4j2 or others); set levels with logging.level.<package>=DEBUG and avoid root-level DEBUG which produces enormous output." |
| 11.6 | Graceful shutdown resource leak | RUNTIME | Process terminates abruptly; in-flight requests dropped | "The application is shutting down without allowing in-flight requests to complete, and resources are not being released cleanly." | "Enable server.shutdown=graceful with spring.lifecycle.timeout-per-shutdown-phase, and release resources in @PreDestroy methods on your beans." |
| 11.7 | Time zone or locale surprise | RUNTIME | Times off by hours; date parsing fails on some environments | "Dates or times are incorrect because the code relies on the JVM default time zone or locale, which differs between environments." | "Store and compute all times in UTC, set an explicit time zone in the JVM startup flags (-Duser.timezone=UTC), and inject a fixed Clock bean so time-dependent code is testable." |

### Section 12: Pure Compile-Time Spring-Specific Errors (Phase: COMPILE)

These are the thin set of Spring-specific failures that surface at compile time rather than runtime.

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 12.1 | Spring configuration processor metadata failure | BUILD_OUTPUT | `build_line_contains`: "spring-boot-configuration-processor" warning OR IDE shows no completion for @ConfigurationProperties keys | "The Spring configuration annotation processor did not run, so @ConfigurationProperties keys have no IDE completion or validation." | "Add spring-boot-configuration-processor as an annotationProcessor dependency in the build file; it does not need to be a compile or runtime dependency." |
| 12.2 | @SpringBootTest cannot find test configuration | COMPILE | `build_line_contains`: "cannot find symbol" for test configuration class OR `message_contains`: "No qualifying bean" only in test compile | "A test configuration class referenced by @SpringBootTest or @ContextConfiguration does not exist or is not compiled before the test." | "Confirm the referenced configuration class is compiled as part of the test source set and is reachable from the test classpath." |
| 12.3 | Annotation processor order conflict (Lombok + MapStruct) | BUILD_OUTPUT | `build_line_contains`: "cannot find symbol" for getter/setter generated by Lombok when MapStruct is also present | "Lombok and MapStruct are running in the wrong annotation processor order, so MapStruct cannot see Lombok-generated getters and setters." | "Declare Lombok before MapStruct in the annotationProcessor block; see section 13 for the full MapStruct error catalog." |

### Section 13: MapStruct Errors (Phase: COMPILE, STARTUP)

MapStruct errors split into two families. Compile-time errors come from the MapStruct annotation processor failing to generate an implementation. Runtime errors arise when the generated implementation is absent from the Spring context, usually because `componentModel = "spring"` was omitted.

**Detection note.** MapStruct compile errors surface in BUILD_OUTPUT; MapStruct Spring injection failures surface in RUN_CONSOLE or TEST_CONSOLE and are distinguishable from ordinary DI failures only by the mapper type name (interfaces annotated with `@Mapper`). PSI enrichment is the most reliable way to distinguish a missing MapStruct bean from a missing ordinary bean.

| ID | Name | Taps | Key Signals | Diagnosis Template | Fix Template |
|---|---|---|---|---|---|
| 13.1 | Unmapped target properties | BUILD_OUTPUT | `buildLineContains`: "Unmapped target property" | "MapStruct found properties in the target type that have no source mapping, which is a warning by default but becomes a build error when unmappedTargetPolicy = ReportingPolicy.ERROR." | "Add an explicit @Mapping for each unmapped property, annotate it with @Mapping(target = \"field\", ignore = true) if intentionally skipped, or change unmappedTargetPolicy to WARN if the default is causing too much noise." |
| 13.2 | Incompatible types in @Mapping | BUILD_OUTPUT | `buildLineContains`: "Can't map property" | "MapStruct cannot map the source property to the target property because the types are incompatible and no conversion or mapping method exists for that pair." | "Declare a custom mapping method that converts between the two types, implement a Spring Converter, or use @Mapping with a qualifiedBy or expression attribute to specify the conversion explicitly." |
| 13.3 | MapStruct implementation class missing | BUILD_OUTPUT, RUN_CONSOLE | `buildLineContains`: "cannot find symbol" AND contains "MapperImpl" OR `causedByClass`: "NoSuchBeanDefinitionException" + type ends with "Mapper" | "The MapStruct annotation processor did not generate the mapper implementation class, so the class does not exist at compile time or the Spring bean cannot be found at runtime." | "Confirm annotation processing is enabled in your build (annotationProcessor scope for Gradle, or maven-compiler-plugin configuration for Maven) and that the mapstruct-processor artifact is on the annotation processor classpath." |
| 13.4 | MapStruct mapper not a Spring bean | RUN_CONSOLE, TEST_CONSOLE | `causedByClass`: "NoSuchBeanDefinitionException"; PSI enrichment: injection target is a @Mapper interface | "The mapper interface is not managed by Spring because componentModel = \"spring\" is missing from the @Mapper annotation, so the generated implementation is not registered as a bean." | "Add componentModel = \"spring\" to the @Mapper annotation: @Mapper(componentModel = \"spring\"), then inject it with @Autowired or constructor injection as you would any other Spring bean." |
| 13.5 | MapStruct cannot implement abstract method | BUILD_OUTPUT | `buildLineContains`: "No implementation type is registered for return type" OR "can't generate mapping method" | "MapStruct cannot generate an implementation for a mapping method because the return type has no registered implementation strategy." | "Provide a custom mapping method body or declare an intermediate mapping step; if the type is a complex third-party class, use @BeanMapping(ignoreByDefault=true) and map only what MapStruct can handle." |
| 13.6 | Missing @Mapper annotation on mapper class | BUILD_OUTPUT, STARTUP | `buildLineContains`: "Could not generate implementation" OR PSI: class implements a mapper interface but lacks @Mapper | "A mapper class or interface is missing the @Mapper annotation, so MapStruct does not recognise it as a mapper and does not generate an implementation." | "Add @Mapper (from org.mapstruct, not another library) to the interface; if componentModel = \"spring\" is also needed, add it at the same time." |
| 13.7 | MapStruct and Lombok processor order conflict | BUILD_OUTPUT | `buildLineContains`: "cannot find symbol" AND Lombok-generated getter absent during MapStruct processing | "Lombok and MapStruct annotation processors are running in the wrong order; MapStruct tries to read getters before Lombok has generated them." | "In Gradle, declare Lombok before MapStruct in the annotationProcessor configuration: annotationProcessor 'org.projectlombok:lombok' must appear before annotationProcessor 'org.mapstruct:mapstruct-processor'." |
| 13.8 | MapStruct ignoring null values unexpectedly | RUNTIME | No exception; mapped object has null fields despite non-null source | "MapStruct's default nullValuePropertyMappingStrategy leaves target fields null when the corresponding source value is null, which can be surprising when partial updates are expected." | "Set @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE) on the mapping method to keep existing target values when the source is null, or SET to overwrite with null explicitly." |

---

## 6. Test Strategy and Fixture Corpus

The rule catalog is only trustworthy when every rule has a real log file that proves it fires. Without fixtures, the rules are untested guesses.

### Fixture structure

```
src/test/resources/fixtures/
  1.1-application-context-exception.log
  1.2-bean-definition-store.log
  ...
  12.3-lombok-mapstruct-order.log
```

Each fixture is a real Spring Boot log excerpt captured from a project that actually hit the error. It should contain enough context (a few hundred lines around the failure) without being the entire startup log.

### CI test contract

Every CI run must:

1. Load all rules from `spring-boot-rules.yaml`
2. Run the extractor and classifier against each fixture file
3. Assert that the resulting `DiagnosisCard.ruleId` matches the fixture's expected rule ID
4. Assert that the confidence is HIGH or MEDIUM (never NONE for a rule with a fixture)
5. Fail the build if any fixture is missing or any rule has status DONE but no fixture

A rule is not DONE until its fixture exists and CI passes.

### Collecting fixtures

The fastest way to collect fixtures is to run a small sample project with each error deliberately introduced. A companion project (`spring-debugger-fixtures`) should hold these trigger projects, one sub-module per error section.

---

## 7. Implementation Milestones

| Milestone | Deliverable | Status | Done when |
|---|---|---|---|
| M0 | IntelliJ plugin skeleton | ✅ DONE | Plugin loads in IntelliJ, shows a tool window, no logic yet |
| M1 | RUN_CONSOLE tap | ✅ DONE | ProcessListener attached to run configurations, raw output captured |
| M2 | Extractor | ✅ DONE | RawSignal produced reliably for banner, Caused-by chain, and bean name |
| M3 | YAML rule loader | ✅ DONE | Rules loaded from `spring-boot-rules.yaml` at plugin startup |
| M4 | Classifier (sections 1 and 2) | ✅ DONE | Rules implemented and passing fixtures |
| M5 | TEST_CONSOLE tap | ✅ DONE | SMTRunnerEventsListener attached, section 9 rules passing |
| M6 | BUILD_OUTPUT tap | 🔄 WIRED, LIVE CHECK PENDING | BuildOutputTap registered via the `compiler.task` extension (internal JPS builds); ExternalBuildOutputTap added for delegated Gradle/Maven builds. Shared BuildOutputAnalyzer is unit-tested; classification is fixture-verified. Not yet confirmed firing in a running IDE sandbox |
| M7 | Full rule catalog | ✅ DONE | 43 of 44 rules DONE with passing fixtures; only 13.8 (MapStruct null-mapping) deferred to M8/PSI. Rule 9.1 removed (dead duplicate of 1.10); rules 4.14 (Redis) and 9.6 (Testcontainers) added |
| M8 | PSI enrichment layer | ✅ DONE | PsiEnricher confirms structural claims for non-HIGH matches: MapStruct @Mapper confirmation (13.3/13.4 upgrade to HIGH), DI missing-stereotype and outside-scan-tree detection (2.x). IdeEnrichmentContext is the thin PSI adapter; logic unit-tested with stubbed ClassFacts. Wired into run and test taps |
| M9 | Actuator enrichment layer | ✅ DONE (narrow surface) | ActuatorReader parses /actuator/health and /actuator/env; ActuatorEnricher confirms non-HIGH RUNTIME cards against live health and upgrades to HIGH. RunConsoleTap detects the bound port. Parsing/logic unit-tested; fires only when the app stays alive and exposes Actuator |
| M10 | UI card polish | ✅ DONE | Full tool window with status bar, current card view, scrollable history, settings panel in Preferences |
| M11 | Settings persistence | ✅ DONE | PersistentStateComponent + DiagnosisHistoryService with listener pattern |
| M12 | Real-life testing | ✅ DONE | 15 real-world logs from GitHub Issues and blog tutorials tested; 13/13 expected matches correct, 2 acknowledged format gaps, 0 false positives; ACCURACY-ANALYSIS-v0.1.0.md (living doc); several bugs and gaps found and fixed (phase filter, rule 13.4 signal, catch-all ordering, 10.1 TEST phase, Redis rule, DONE-only runtime) |
| M13 | LLM integration (Ollama) | ⏳ PENDING | Config switch enables the LLM fallback path |
| M14 | How-to-use guide | ✅ DONE | HOW-TO-USE.md published covering installation, UI, settings, rule catalog, and contributing guide |

M0–M7, M10–M12, and M14 are complete. Plugin is functional, documented, and validated against real-world logs.

### Open items before v1.0

- M6 live verification: the build taps are now registered (internal `compiler.task` plus external-system listener for delegated Gradle/Maven) and the parsing core is unit-tested, but firing has not been confirmed in a running IDE sandbox. Before marking M6 fully DONE, run the IntelliJ Community sandbox with a Gradle Spring project that has a MapStruct/WebSecurityConfigurerAdapter compile error (build delegated to Gradle, the default) and confirm ExternalBuildOutputTap surfaces the card.
- 13.8 (MapStruct null-mapping): stays TODO on purpose. Its diagnosis claims a specific cause (null-value property mapping strategy) that an NPE-through-MapperImpl signal cannot prove. Promoting it to MEDIUM just to clear the test would mislabel the confidence. Resolve it with M8 (PSI) so the structural claim can be verified, or only after the signal is tightened.
- Expand real-life testing corpus to 25+ logs for v0.2.0
- M8 PSI enrichment and M9 Actuator enrichment for higher-confidence MEDIUM rules
- M13 Ollama LLM fallback

### Resolved since v0.1.0

- 9.1: removed as a dead duplicate of 1.10 (its `causedByClass: IllegalStateException` signal could never match the deepest Caused by). Replaced with rule 9.6 (Testcontainers Docker not available).
- 10.1: extended to the TEST phase so NoSuchMethodError during test context load gets the version-conflict diagnosis.
- 1.10: moved to the end of the catalog as a true last-resort catch-all so specific rules win first.
- 4.14 (Redis connection factory missing): added to close the SOL-003 real-world gap.
- Classifier now only fires DONE rules; TODO rules are inactive at runtime.

---

## 8. LLM Extension Plan (Deferred)

Design decisions made now to avoid painting the offline implementation into a corner.

### 8.1 Config switch

A project-level setting in `.idea/springDebugger.xml`:

```xml
<option name="llmEnabled" value="false" />
<option name="llmProvider" value="OLLAMA" />
<option name="ollamaBaseUrl" value="http://localhost:11434" />
<option name="ollamaModel" value="llama3" />
```

The LLM path is disabled by default. Projects with strict data policies keep it off permanently. For Ollama the data never leaves the machine, which resolves most security concerns.

### 8.2 Interface parity

The classifier returns a `ClassifierResult`. Both the rule engine and the LLM adapter implement the same interface:

```java
interface DiagnosisEngine {
    ClassifierResult classify(RawSignal signal);
}
```

The LLM adapter is called only when the rule engine returns `confidence == NONE`. The rule engine is always tried first.

### 8.3 Context sent to the LLM

When the LLM fallback fires, the context package sent to the model contains:

- The extracted `RawSignal` (exception chain, banner, bean name)
- The active Spring Boot version (read from the build model)
- The guide condensed into a system prompt (the section headers and fix lines only, not the full text)
- Instruction: return JSON matching the `DiagnosisCard` schema with one diagnosis sentence and one fix sentence

The response is parsed against the `DiagnosisCard` schema. If parsing fails, nothing is shown rather than showing a malformed result.

### 8.4 Supported providers (planned)

- Ollama (local; the recommended default for LLM-enabled mode)
- Anthropic API (cloud; requires API key)
- OpenAI-compatible endpoints (configurable base URL)

---

## 9. Open Decisions

| Decision | Options | Current position |
|---|---|---|
| UI surface for the diagnosis card | Notification balloon / Gutter icon + popup / Dedicated tool window panel | Use balloon for now; tool window panel in M10 |
| How to detect Spring Boot version | Parse pom.xml or build.gradle from the build model | Build model is cleanest; fallback to scanning classpath jars |
| Rule file location | Bundled in plugin JAR vs. user-editable external file | Bundled first; user override path in M7 |
| Kotlin support | In scope or out of scope | Out of scope for now; Java-only |
| IntelliJ minimum version | 2023.1+ or 2022.x | 2023.1 (for modern API stability) |
| Severity threshold for showing a card | Any match / HIGH only / configurable | Show HIGH and MEDIUM; suppress LOW unless debug mode |

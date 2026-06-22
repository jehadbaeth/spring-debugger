# The Comprehensive Spring Boot Debugging Guide

A thorough catalogue of common Spring Boot errors, what causes them, how to read the (often enormous) logs they produce, and how to fix them. Errors are grouped by the phase in which they surface: application context startup, dependency injection, configuration and properties, data and JPA, web and REST, security, serialization, testing, build, and runtime.

---

## How to Use This Guide

Spring Boot errors are frequently *cascading*: one root cause (a missing bean, a typo in a property name, an unparseable YAML file) generates dozens of downstream stack traces. The single most important debugging skill is learning to ignore the noise and find the root cause.

Three habits will save you the most time:

1. **Read the log from the bottom up, then find the top "Caused by".** A Spring stack trace is a chain of wrapped exceptions. The *last* `Caused by:` in the chain is usually closest to the real problem. The first few hundred lines are usually framework plumbing.
2. **Look for the `***************************` banner.** Spring Boot's `FailureAnalyzer` prints a clean, human readable diagnosis between rows of asterisks. When present, this is almost always the actual answer. Most people scroll right past it.
3. **Trust the "Description" and "Action" lines.** The failure analyzer literally tells you what to do. Read those two lines before reading anything else.

A note on *when* errors appear. Compile time catches very little in Spring: annotation typos, missing beans, bad property values, and misconfigured YAML are all resolved at runtime (context refresh) or during test bootstrapping, not by `javac`. That is why this guide is organized by runtime phase rather than by compiler vs runtime.

---

## Quick Reference: The Master Error List

This is the complete index of errors covered in this document. Use it as a checklist so nothing is missed.

### 1. Application Context and Startup Errors
- 1.1 `ApplicationContextException` / failed to start application context
- 1.2 `BeanDefinitionStoreException`
- 1.3 `BeanCreationException`
- 1.4 `BeanInstantiationException`
- 1.5 `BeanDefinitionOverrideException` (duplicate bean names)
- 1.6 Component scanning misses your beans (wrong package placement of the main class)
- 1.7 `@SpringBootApplication` placed in the wrong package
- 1.8 Port already in use (`Web server failed to start. Port 8080 was already in use`)
- 1.9 No active profile / wrong profile loaded
- 1.10 `IllegalStateException: Failed to load ApplicationContext` (most common in tests)
- 1.11 Multiple main classes / no main manifest attribute
- 1.12 Auto-configuration not applied (missing starter dependency)
- 1.13 Circular reference in lifecycle / `BeanCurrentlyInCreationException`

### 2. Dependency Injection Errors
- 2.1 `NoSuchBeanDefinitionException` (no qualifying bean of type X)
- 2.2 `NoUniqueBeanDefinitionException` (multiple candidate beans)
- 2.3 `UnsatisfiedDependencyException`
- 2.4 Missing `@Component` / `@Service` / `@Repository` / `@Controller` annotation
- 2.5 Missing `@Bean` method in a `@Configuration` class
- 2.6 Field injection NPE because the bean was created with `new`
- 2.7 Circular dependency (`BeanCurrentlyInCreationException`)
- 2.8 `@Autowired` on a static field or final field with constructor mismatch
- 2.9 Injecting a prototype bean into a singleton (stale instance)
- 2.10 `@Qualifier` / `@Primary` not resolving the right candidate
- 2.11 Self-injection and `@Async` / `@Transactional` proxy not applied
- 2.12 Optional vs required injection (`required = false`, `Optional<T>`, `ObjectProvider<T>`)

### 3. Configuration and Properties Errors
- 3.1 `@Value` placeholder cannot be resolved (`Could not resolve placeholder 'x'`)
- 3.2 Property not bound to `@ConfigurationProperties`
- 3.3 Relaxed binding / naming mismatch (camelCase vs kebab-case)
- 3.4 Type conversion failure binding a property
- 3.5 YAML syntax / indentation errors
- 3.6 YAML type pitfalls (unquoted `yes`/`no`/`on`/`off`, leading zeros, `:` in values)
- 3.7 Property precedence confusion (which source wins)
- 3.8 Profile-specific properties not loading (`application-{profile}.yml`)
- 3.9 Missing or wrong `spring.config.import` (e.g. Config Server, Vault)
- 3.10 Environment variable / `SPRING_APPLICATION_JSON` overrides not understood
- 3.11 `@ConfigurationProperties` class not registered
- 3.12 Sensitive value logged or placeholder leaking into logs

### 4. Data Access and JPA / Hibernate Errors
- 4.1 `Cannot determine embedded database driver class for database type NONE`
- 4.2 `Failed to configure a DataSource` (no url, no driver)
- 4.3 Wrong JDBC URL / driver mismatch
- 4.4 Connection pool exhaustion / `HikariPool ... Connection is not available`
- 4.5 `PersistenceException` / `JpaSystemException` wrapping the real cause
- 4.6 Entity not mapped (`Not a managed type` / `Unknown entity`)
- 4.7 Missing `@Entity`, `@Id`, or no-arg constructor
- 4.8 `LazyInitializationException` (no session, proxy accessed outside transaction)
- 4.9 N+1 query problem (performance, not a crash)
- 4.10 `@Transactional` not applied (self-invocation, non-public method, wrong proxy)
- 4.11 `TransactionRequiredException` (no active transaction for a write)
- 4.12 Schema mismatch / `ddl-auto` surprises (data wiped, table missing)
- 4.13 `DataIntegrityViolationException` (constraint, not-null, unique, FK)
- 4.14 `ObjectOptimisticLockingFailureException` (`@Version` mismatch)
- 4.15 Detached entity passed to persist / `StaleObjectStateException`
- 4.16 `MappingException` (bad relationship, missing `mappedBy`, wrong cascade)
- 4.17 Repository method name not parseable by Spring Data
- 4.18 `InvalidDataAccessApiUsageException` on a malformed query
- 4.19 Flyway / Liquibase migration checksum or ordering failure
- 4.20 Naming strategy surprises (physical vs implicit naming, snake_case columns)

### 5. Web, REST, and MVC Errors
- 5.1 404 `NoHandlerFoundException` / wrong mapping or context path
- 5.2 405 Method Not Allowed (verb mismatch)
- 5.3 415 Unsupported Media Type / 406 Not Acceptable
- 5.4 400 Bad Request from failed `@RequestBody` binding
- 5.5 `MethodArgumentNotValidException` (Bean Validation `@Valid` failures)
- 5.6 `MissingServletRequestParameterException` / missing path variable
- 5.7 `HttpMessageNotReadableException` (malformed JSON body)
- 5.8 Ambiguous mapping (`Ambiguous mapping. Cannot map ... method`)
- 5.9 CORS errors (blocked by browser, not always visible server-side)
- 5.10 Filter / interceptor ordering problems
- 5.11 `@ControllerAdvice` not catching exceptions
- 5.12 Static resources / favicon mapping swallowing routes
- 5.13 Servlet vs WebFlux mismatch (blocking call on reactive thread)

### 6. Security Errors (Spring Security)
- 6.1 401 Unauthorized vs 403 Forbidden confusion
- 6.2 Default generated password / login page appearing unexpectedly
- 6.3 CSRF blocking POST/PUT/DELETE (especially for APIs)
- 6.4 `SecurityFilterChain` not picking up your config (old `WebSecurityConfigurerAdapter`)
- 6.5 Password encoder mismatch (`There is no PasswordEncoder mapped for the id "null"`)
- 6.6 JWT / OAuth2 token validation failures (expired, wrong issuer, bad signature)
- 6.7 Method security (`@PreAuthorize`) not enforced (annotation not enabled)
- 6.8 CORS + Security interaction (preflight blocked before reaching controller)

### 7. Serialization / Jackson Errors
- 7.1 `HttpMessageNotWritableException` / no serializer found
- 7.2 Infinite recursion on bidirectional relationships (`StackOverflowError`)
- 7.3 `UnrecognizedPropertyException` (unknown field in JSON)
- 7.4 `InvalidDefinitionException` (no default constructor / cannot construct)
- 7.5 Date/time serialization (`LocalDate`, time zones, JSR-310 module missing)
- 7.6 `@JsonIgnore`, `@JsonProperty`, naming strategy mismatches
- 7.7 Lazy proxies serialized (Hibernate proxy leaking into JSON)

### 8. OpenAPI / Swagger / springdoc Errors
- 8.1 Swagger UI 404 / not loading
- 8.2 Generated spec missing endpoints or schemas
- 8.3 OpenAPI YAML/JSON spec fails to parse (codegen)
- 8.4 `$ref` resolution failures in a multi-file spec
- 8.5 Generated code mismatch (model not matching schema, missing fields)
- 8.6 Version conflicts (springdoc vs Spring Boot 3, springfox legacy)
- 8.7 Security scheme not shown / "Authorize" button missing

### 9. Testing Errors
- 9.1 `Failed to load ApplicationContext` in tests
- 9.2 `@MockBean` / `@MockitoBean` not replacing the real bean
- 9.3 Test slice loads too much or too little (`@WebMvcTest`, `@DataJpaTest`)
- 9.4 `@SpringBootTest` webEnvironment / port confusion
- 9.5 Dirties context / shared state leaking between tests
- 9.6 Testcontainers not starting (Docker not available, wrong image)
- 9.7 H2 vs real DB dialect differences in tests
- 9.8 `@Transactional` test rollback hiding real persistence behavior
- 9.9 Mockito `UnnecessaryStubbingException` / strict stubs
- 9.10 Flaky tests from time, ordering, or async timing

### 10. Build, Packaging, and Dependency Errors
- 10.1 Version conflicts / `NoSuchMethodError` / `NoClassDefFoundError`
- 10.2 Dependency convergence and the BOM (`spring-boot-dependencies`)
- 10.3 `ClassNotFoundException` at runtime (provided/optional scope mistakes)
- 10.4 Fat jar problems (`no main manifest attribute`, nested jar classloading)
- 10.5 Lombok not generating code (annotation processing off, IDE config)
- 10.6 Java version mismatch (compiled vs runtime, `UnsupportedClassVersionError`)
- 10.7 Duplicate classes on classpath / split packages
- 10.8 Native image (GraalVM) reflection / proxy registration failures

### 11. Runtime, Performance, and Operational Errors
- 11.1 `OutOfMemoryError` (heap, metaspace, direct buffers)
- 11.2 Thread pool exhaustion / blocked threads
- 11.3 Slow startup (component scanning, eager beans, DB on boot)
- 11.4 Actuator endpoint exposure / health check failures
- 11.5 Logging misconfiguration (no output, double output, wrong level)
- 11.6 Graceful shutdown and resource leaks
- 11.7 Clock, time zone, and locale surprises

---

## 1. Application Context and Startup Errors

When a Spring Boot application fails to start, the context "refresh" aborts and the JVM usually exits with a non-zero code. Everything in this section happens during that refresh, before your application is serving traffic.

### 1.1 `ApplicationContextException` — failed to start application context

**Symptom**
```
org.springframework.context.ApplicationContextException: Unable to start web server;
nested exception is ...
```
or simply a long trace ending in the application shutting down.

**Cause.** This is an umbrella error. The real cause is always in a nested `Caused by:`. Common roots: a web server cannot bind a port, an auto-configuration failed, or a bean threw during initialization.

**How to debug.** Scroll to the bottom of the trace and read the deepest `Caused by:`. Then look upward for the Spring Boot failure analyzer banner (asterisks). Do not try to interpret the top of the stack; it is generic.

**Fix.** Address whatever the deepest cause says. The rest of section 1 covers the specific roots.

### 1.2 `BeanDefinitionStoreException`

**Symptom.** Thrown while Spring is *reading* bean definitions (parsing `@Configuration`, scanning components, loading XML).

**Cause.** A malformed configuration class, an unparseable `@PropertySource`, a bad `@Import`, or an annotation that points to a class that does not exist.

**Fix.** Check recently edited `@Configuration` classes and any `@PropertySource`/`@Import` references. The message usually names the offending resource or class.

### 1.3 `BeanCreationException`

**Symptom.**
```
Error creating bean with name 'fooService': ...
```

**Cause.** A bean was found and Spring tried to instantiate and wire it, but something failed: a dependency could not be satisfied, a constructor threw, an `@PostConstruct` method threw, or a `@Value` could not resolve.

**How to debug.** The message names the failing bean. Read the nested cause to learn *why*. If it is `UnsatisfiedDependencyException`, see section 2.3. If it is a placeholder error, see 3.1.

### 1.4 `BeanInstantiationException`

**Symptom.** `Failed to instantiate [com.example.Foo]: No default constructor found` or a constructor that threw an exception.

**Cause.** Spring could not call the constructor — often a missing no-arg constructor where one is required, or an exception thrown inside the constructor.

**Fix.** Provide the required constructor, or move risky logic out of the constructor into an `@PostConstruct` method (where exceptions are easier to trace), or fix the thrown exception.

### 1.5 `BeanDefinitionOverrideException` — duplicate bean names

**Symptom.**
```
The bean 'fooService', defined in ..., could not be registered.
A bean with that name has already been defined in ... and overriding is disabled.
```

**Cause.** Two beans resolve to the same name. Frequently caused by two `@Bean` methods named identically, a component scanned twice, or the same class defined both via `@Component` and `@Bean`.

**Fix.** Rename one bean (`@Bean("uniqueName")`), remove the duplicate definition, or — only as a deliberate, documented choice — set `spring.main.allow-bean-definition-overriding=true`. Prefer fixing the duplication; enabling overriding hides real problems.

### 1.6 Component scanning misses your beans

**Symptom.** A class is annotated `@Service` but you still get `NoSuchBeanDefinitionException`, or your controller's endpoints return 404.

**Cause.** `@SpringBootApplication` enables component scanning of the package it lives in *and all sub-packages*. If a bean lives in a sibling or parent package, it is never scanned.

**Fix.** Move the main application class to a root package that is a parent of everything (e.g. `com.example.app`), or add `@ComponentScan(basePackages = "...")` explicitly. The package-placement fix is strongly preferred; explicit scan paths drift over time.

### 1.7 `@SpringBootApplication` in the wrong package

This is the same root cause as 1.6 stated from the other direction. If your main class is in `com.example.app.web` but services live in `com.example.app.service`, the service package is a *sibling*, not a child, and will not be scanned. Put the main class one level up, in `com.example.app`.

### 1.8 Port already in use

**Symptom.**
```
***************************
APPLICATION FAILED TO START
***************************
Description:
Web server failed to start. Port 8080 was already in use.
Action:
Identify and stop the process that's listening on port 8080 or configure this application to listen on another port.
```

**Cause.** Another process (often a previous run that did not shut down) holds the port.

**Fix.** Kill the other process (`lsof -i :8080` then `kill`, or on Windows `netstat -ano | findstr :8080`), or change the port with `server.port=8081`. In tests use `webEnvironment = RANDOM_PORT`.

### 1.9 No active profile / wrong profile

**Symptom.** Beans or properties you expected are missing, or the wrong datasource is used. Logs say `No active profile set, falling back to default profiles: default`.

**Cause.** You relied on a profile (`@Profile("prod")` or `application-prod.yml`) but never activated it.

**Fix.** Set `spring.profiles.active=prod` via property, env var `SPRING_PROFILES_ACTIVE=prod`, or JVM arg `-Dspring.profiles.active=prod`. Confirm the line near the top of the log that lists active profiles.

### 1.10 `IllegalStateException: Failed to load ApplicationContext`

**Symptom.** Almost always seen in tests; the context cannot be built.

**Cause.** Any bean wiring or configuration failure during test bootstrap. The real cause is nested. Test slices that load a partial context (see 9.3) frequently trigger this when a required collaborator is absent.

**Fix.** Read the nested cause. In tests, ensure you provide `@MockitoBean`/`@MockBean` for collaborators the loaded slice needs, or widen/narrow the slice appropriately.

### 1.11 No main manifest attribute / multiple main classes

**Symptom.** `no main manifest attribute, in app.jar`, or the build fails because it cannot pick a main class.

**Cause.** The Spring Boot plugin could not determine a single `public static void main`. Either there are several, or none was found in the expected place.

**Fix.** Set the main class explicitly: in Maven `<properties><start-class>com.example.App</start-class></properties>` or the plugin's `mainClass`; in Gradle the `springBoot { mainClass = '...' }` block. Ensure you build with the Spring Boot plugin, not a plain jar task.

### 1.12 Auto-configuration not applied (missing starter)

**Symptom.** A feature you expect (JPA, web, security) simply is not there; no DataSource, no embedded server, endpoints missing.

**Cause.** The relevant `spring-boot-starter-*` is not on the classpath, so the matching auto-configuration's `@ConditionalOnClass` never matches.

**How to debug.** Run with `--debug` (or `debug=true`) to print the **Auto-configuration report**, which lists *positive* and *negative* matches and *why* each was or was not applied. This report is the single best tool for "why isn't this configured" questions.

**Fix.** Add the missing starter (e.g. `spring-boot-starter-data-jpa`, `spring-boot-starter-web`).

### 1.13 `BeanCurrentlyInCreationException` during startup

A lifecycle-level circular dependency. Covered in detail under 2.7.


---

## 2. Dependency Injection Errors

DI errors are the most common Spring Boot failures. They almost all reduce to one of three questions: *does the bean exist?* (2.1), *is there exactly one?* (2.2, 2.10), and *can it be wired without a cycle?* (2.7).

### 2.1 `NoSuchBeanDefinitionException` — no qualifying bean of type X

**Symptom.**
```
Field fooService in com.example.Bar required a bean of type
'com.example.FooService' that could not be found.
```
The failure analyzer adds an `Action:` line suggesting you define such a bean.

**Cause.** No bean of the requested type exists. Common reasons: the class lacks a stereotype annotation (2.4); it lives outside the scanned packages (1.6); it is conditionally excluded; or the needed starter/auto-config is absent.

**Fix.** Annotate the class (`@Service`, `@Component`, etc.) or declare an `@Bean` method; confirm package placement; confirm the dependency providing it is present. Run `--debug` to confirm whether the bean was a candidate at all.

### 2.2 `NoUniqueBeanDefinitionException` — multiple candidates

**Symptom.**
```
required a single bean, but 2 were found: fooServiceA, fooServiceB
```

**Cause.** More than one bean matches the injection type and Spring cannot choose.

**Fix.** Mark one as `@Primary`, or disambiguate at the injection point with `@Qualifier("fooServiceA")`. If you actually want them all, inject `List<FooService>` or `Map<String, FooService>`.

### 2.3 `UnsatisfiedDependencyException`

**Symptom.** `Error creating bean with name 'X': Unsatisfied dependency expressed through constructor parameter 0`.

**Cause.** A wrapper around 2.1 or 2.2: a dependency this bean needs could not be satisfied. The nested cause says whether it was "not found" or "not unique".

**Fix.** Resolve the nested cause using 2.1 or 2.2.

### 2.4 Missing stereotype annotation

**Symptom.** Bean "exists" in your mind but Spring reports `NoSuchBeanDefinitionException`.

**Cause.** A plain class with no `@Component`/`@Service`/`@Repository`/`@Controller`/`@RestController`/`@Configuration` is invisible to component scanning. Spring only manages beans it knows about.

**Fix.** Add the appropriate stereotype, or declare it as an `@Bean` in a config class. Note: these annotations are read at runtime, so omitting one never fails compilation; it fails at context refresh.

### 2.5 Missing `@Bean` method in a `@Configuration` class

**Symptom.** A third-party type (one you cannot annotate) is not injectable.

**Cause.** Classes you do not own cannot carry stereotypes, so you must declare them with an `@Bean` factory method inside a `@Configuration` class. If you forget, the type is unavailable.

**Fix.** Add a `@Bean` method returning the configured instance.

### 2.6 Field is null because the object was created with `new`

**Symptom.** A `NullPointerException` on an `@Autowired` field, even though wiring "looks" right.

**Cause.** You instantiated the class yourself with `new Foo()`. Spring only injects into beans it manages. A hand-constructed object never has its dependencies populated.

**Fix.** Inject the bean instead of constructing it. If you truly need to build instances at runtime with dependencies, use a factory bean or `ObjectProvider`/`ApplicationContext` lookup. Prefer constructor injection so an un-managed instance fails loudly at construction rather than silently with nulls.

### 2.7 Circular dependency

**Symptom.**
```
The dependencies of some of the beans in the application context form a cycle:
   fooService -> barService -> fooService
```
or `BeanCurrentlyInCreationException`.

**Cause.** A depends on B and B depends on A (possibly transitively). With constructor injection, neither can be built first. Spring 2.6+ disallows this by default.

**Fix.** Break the cycle — it is almost always a design smell. Options: extract the shared logic into a third bean; use `@Lazy` on one injection point to defer resolution; or, as a last resort, set `spring.main.allow-circular-references=true`. Redesigning is strongly preferred; the flag just postpones the pain.

### 2.8 `@Autowired` on a static or mismatched final field

**Symptom.** Static field stays null; or constructor injection complains about a `final` field never assigned.

**Cause.** Spring cannot inject into `static` fields. With `final` fields you must use constructor injection (the constructor assigns them); field/setter injection of a `final` field is impossible.

**Fix.** Make the field non-static, or move static configuration to a properly injected bean. Use constructor injection for `final` fields (Lombok's `@RequiredArgsConstructor` generates the constructor).

### 2.9 Prototype injected into a singleton

**Symptom.** You expect a fresh prototype instance each use but keep getting the same object.

**Cause.** A singleton's dependencies are injected once, at singleton creation. A prototype injected normally is resolved a single time and then frozen.

**Fix.** Use `ObjectProvider<T>` / `Provider<T>` and call `getObject()` per use, or use scoped-proxy (`@Scope(value = "prototype", proxyMode = TARGET_CLASS)`), or look it up from the context on demand.

### 2.10 `@Qualifier` / `@Primary` not resolving

**Symptom.** Wrong implementation injected, or still ambiguous despite a qualifier.

**Cause.** The `@Qualifier` value does not match the bean name/qualifier; or multiple beans are marked `@Primary`; or the qualifier is on the wrong element.

**Fix.** Ensure the qualifier string matches exactly (bean name defaults to the decapitalized class name). Have at most one `@Primary` per type.

### 2.11 Self-invocation defeats `@Async` / `@Transactional`

**Symptom.** A method annotated `@Transactional` or `@Async` behaves as if the annotation is absent — no transaction, runs synchronously.

**Cause.** These features work via a *proxy*. When a bean calls its own annotated method directly (`this.method()`), the call bypasses the proxy, so the advice never runs.

**Fix.** Move the annotated method to a separate bean and call it through that bean, or inject a reference to self (`@Lazy` self-injection / `AopContext.currentProxy()`). Restructuring into a separate collaborator is the cleaner fix.

### 2.12 Optional injection done wrong

**Symptom.** Startup fails because an *optional* collaborator is missing, or a missing optional bean throws an NPE.

**Cause.** You injected a bean that may not exist as if it were required.

**Fix.** Express optionality explicitly: `@Autowired(required = false)`, `Optional<T>`, or `ObjectProvider<T>` (then `getIfAvailable()`). This is the idiomatic way to depend on something that might be absent (e.g. only present under a certain profile).

---

## 3. Configuration and Properties Errors

Configuration errors are insidious because a typo in a property name never fails compilation and often does not fail startup either — the value is silently ignored and a default is used. Treat "the property had no effect" as a first-class bug class.

### 3.1 `Could not resolve placeholder`

**Symptom.**
```
java.lang.IllegalArgumentException: Could not resolve placeholder 'app.timeout' in value "${app.timeout}"
```
This surfaces as a `BeanCreationException` on the bean using the `@Value`.

**Cause.** A `@Value("${app.timeout}")` references a property that is not defined in any property source for the active profile.

**Fix.** Define the property, or supply a default in the expression: `@Value("${app.timeout:5000}")`. The default-value colon syntax is the most robust approach for non-mandatory settings. Verify the property exists for the *active profile*, not just the default one.

### 3.2 Property not bound to `@ConfigurationProperties`

**Symptom.** The fields of a `@ConfigurationProperties` class stay at their defaults.

**Cause.** The class is not registered, the prefix does not match, the field has no setter (for non-constructor binding), or relaxed binding still cannot map the key.

**Fix.** Register it (`@EnableConfigurationProperties(AppProps.class)` or annotate the class with `@Component`), confirm the `prefix`, and ensure setters exist or use constructor binding (`@ConstructorBinding`/immutable records in Boot 3). Add `spring-boot-configuration-processor` so the IDE validates keys.

### 3.3 Relaxed binding / naming mismatch

**Symptom.** `app.maxRetryCount` in code but `app.max-retry-count` in YAML — and it does not bind, or you are unsure which form is canonical.

**Cause.** Spring's relaxed binding maps kebab-case, camelCase, and `UNDERSCORE` forms to the same property, but environment variables must use `UPPER_SNAKE_CASE` and only certain forms are canonical. Mismatches sneak in when keys are slightly off.

**Fix.** Use kebab-case (`app.max-retry-count`) in `.yml`/`.properties` as the canonical form, and `APP_MAX_RETRY_COUNT` for env vars. Let `@ConfigurationProperties` handle the mapping; do not hand-write camelCase keys.

### 3.4 Type conversion failure

**Symptom.**
```
Failed to bind properties under 'app.timeout' to java.time.Duration
```

**Cause.** The string value cannot be converted to the target type (e.g. `"5s"` vs an int field, an invalid enum constant, a malformed `Duration`/`DataSize`).

**Fix.** Match the value to the type. Spring supports `Duration` (`5s`, `200ms`), `DataSize` (`10MB`), and enums (case-insensitive). For custom types, register a `Converter`.

### 3.5 YAML syntax / indentation errors

**Symptom.** Startup fails parsing YAML, or — worse — succeeds but a nested block is silently ignored because the indentation grouped it wrong.

**Cause.** YAML is whitespace-significant and forbids tabs. One misaligned space re-parents a whole subtree.

**Fix.** Use spaces only (configure the editor to insert spaces for `.yml`). Validate with a linter. When in doubt, the flat `.properties` format removes indentation ambiguity entirely.

### 3.6 YAML type pitfalls

**Symptom.** `on`/`off`/`yes`/`no` become booleans; `version: 1.20` drops the trailing zero; a value containing `:` breaks parsing; a leading-zero number is read as octal.

**Cause.** YAML's implicit typing is aggressive. The Norway problem (`NO` country code becoming `false`) is the classic example.

**Fix.** Quote any string value that could be misread: `country: "NO"`, `version: "1.20"`, `cron: "0 0 * * * *"`. Quoting strings defensively is cheaper than debugging a silent coercion.

### 3.7 Property precedence confusion

**Symptom.** A property you set is ignored because something with higher precedence overrides it.

**Cause.** Spring has a long, ordered list of property sources. Command-line args beat env vars beat profile-specific files beat `application.yml`, etc. People often edit the wrong source.

**Fix.** Remember the rough order (highest wins): command line > `SPRING_APPLICATION_JSON` > OS env / system properties > `application-{profile}` > `application` > defaults. Use the Actuator `/env` endpoint (or `--debug`) to see the *effective* value and which source supplied it.

### 3.8 Profile-specific properties not loading

**Symptom.** `application-prod.yml` seems ignored.

**Cause.** The profile is not active (see 1.9), the file is misnamed (it must be `application-{profile}.yml`, exact dash), or it is in the wrong location on the classpath.

**Fix.** Activate the profile and verify the filename. Confirm in the startup log line listing active profiles.

### 3.9 Wrong or missing `spring.config.import`

**Symptom.** Boot 2.4+ refuses to start: `Config data location 'configserver:' ... could not be found`, or external config (Vault, Config Server) is absent.

**Cause.** Externalized config now uses `spring.config.import` rather than bootstrap. A missing or misspelled import, or a required import not marked `optional:`, aborts startup.

**Fix.** Add `spring.config.import=optional:configserver:` (the `optional:` prefix lets the app start when the source is unavailable) and ensure the relevant client dependency is present.

### 3.10 Environment / `SPRING_APPLICATION_JSON` not understood

**Symptom.** A value injected via env var or JSON blob is not applied.

**Cause.** Wrong key casing for env vars, or malformed JSON in `SPRING_APPLICATION_JSON`.

**Fix.** Convert dotted keys to `UPPER_SNAKE_CASE` for env vars (`app.timeout` -> `APP_TIMEOUT`). Validate the JSON. Check `/env` to confirm it landed.

### 3.11 `@ConfigurationProperties` class not registered

Restated for emphasis (see 3.2): declaring the class is not enough. It must be discovered, via `@EnableConfigurationProperties`, a `@ConfigurationPropertiesScan`, or a stereotype. Forgetting registration yields a bean of all-default values with no error.

### 3.12 Secrets leaking into logs

**Symptom.** A password or token appears in plaintext in startup logs or an error message.

**Cause.** Logging the environment, printing a `@ConfigurationProperties` object whose `toString` includes secrets, or a failure analyzer echoing a resolved placeholder.

**Fix.** Never log full config objects. Mask sensitive fields, keep secrets out of `application.yml` (use env vars or a vault), and restrict the Actuator `/env` and `/configprops` endpoints, which can expose values. This is brutally important: a leaked secret in a log aggregator is a real incident, not a cosmetic bug.


---

## 4. Data Access and JPA / Hibernate Errors

JPA errors are notorious for huge traces because a single failure is wrapped repeatedly: Hibernate -> `PersistenceException` -> Spring's `JpaSystemException` -> the bean creation chain. The discipline is the same: find the deepest cause.

### 4.1 `Cannot determine embedded database driver class for database type NONE`

**Symptom.** The classic JPA-on-the-classpath-but-no-DB error, printed by the failure analyzer.

**Cause.** `spring-boot-starter-data-jpa` is present (so a DataSource auto-config runs) but no datasource URL/driver is configured and no embedded DB (H2/HSQLDB/Derby) is on the classpath.

**Fix.** Either configure a real datasource (`spring.datasource.url`, `username`, `password`, and the JDBC driver dependency) or add an embedded DB dependency for dev/test (`com.h2database:h2`). If you do not need JPA at all, remove the starter.

### 4.2 `Failed to configure a DataSource`

**Symptom.**
```
Failed to configure a DataSource: 'url' attribute is not specified
and no embedded datasource could be configured.
```

**Cause.** Same family as 4.1: the auto-configuration needs connection details it cannot find.

**Fix.** Provide `spring.datasource.url` (and driver), or exclude `DataSourceAutoConfiguration` if a datasource is genuinely not wanted: `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)`.

### 4.3 Wrong JDBC URL or driver mismatch

**Symptom.** `No suitable driver found for jdbc:...`, or dialect/connection errors.

**Cause.** Driver dependency missing, URL scheme not matching the driver, or wrong dialect for the database.

**Fix.** Match driver, URL, and (if you set it) dialect. Let Hibernate auto-detect the dialect unless you have a specific reason to override it.

### 4.4 Connection pool exhaustion

**Symptom.**
```
HikariPool-1 - Connection is not available, request timed out after 30000ms.
```

**Cause.** All pooled connections are checked out and never returned — usually a leak (connections not closed, or a long-running transaction holding a connection), or the pool is simply too small for the load.

**Fix.** Find the leak first: enable Hikari leak detection (`spring.datasource.hikari.leak-detection-threshold=20000`) to log stack traces of connections held too long. Ensure transactions are short and that you are not holding a connection across an external call. Only resize the pool (`maximum-pool-size`) after confirming there is no leak; a bigger pool just delays exhaustion if connections leak.

### 4.5 `JpaSystemException` / `PersistenceException` wrappers

**Symptom.** A generic JPA exception with the real message several `Caused by:` deep.

**Cause.** Spring translates Hibernate/JPA exceptions into its `DataAccessException` hierarchy. The wrapper is generic by design.

**Fix.** Read the deepest cause (often a SQL constraint message from the driver). Do not act on the wrapper class name alone.

### 4.6 `Not a managed type` / `Unknown entity`

**Symptom.**
```
java.lang.IllegalArgumentException: Not a managed type: class com.example.User
```

**Cause.** The class is not registered as an entity. Either it lacks `@Entity`, or it is outside the packages Hibernate scans for entities.

**Fix.** Add `@Entity`; ensure the entity package is under the main class package or add `@EntityScan(basePackages = "...")`. Same package-placement logic as component scanning (1.6).

### 4.7 Missing `@Entity`, `@Id`, or no-arg constructor

**Symptom.** `No identifier specified for entity`, or instantiation failures.

**Cause.** JPA requires every entity to have an `@Id` and a (at least package-private) no-arg constructor. Lombok's `@Data` without `@NoArgsConstructor` can remove it.

**Fix.** Add `@Id` (and a generation strategy if needed), and ensure a no-arg constructor exists. With Lombok, add `@NoArgsConstructor`.

### 4.8 `LazyInitializationException`

**Symptom.**
```
org.hibernate.LazyInitializationException: could not initialize proxy - no Session
```

**Cause.** A lazily-loaded association is accessed after the Hibernate session/transaction has closed — commonly when serializing an entity in the controller layer after the service transaction ended.

**Fix.** Fetch what you need *inside* the transaction: use a fetch join (`JOIN FETCH`), an entity graph, or map to a DTO within the transactional boundary. Avoid `spring.jpa.open-in-view=true` as a "fix" — it papers over the boundary problem and can cause connection-holding under load. Returning DTOs from the service layer is the cleanest, most defensible fix.

### 4.9 N+1 query problem

**Symptom.** Not an exception — a performance cliff. One query for the parents, then one extra query per child collection.

**Cause.** Lazy associations iterated in a loop, each triggering its own SELECT.

**Fix.** Use `JOIN FETCH`, `@EntityGraph`, or batch fetching (`@BatchSize` / `hibernate.default_batch_fetch_size`). Turn on `spring.jpa.show-sql` (dev only) or use a SQL profiler to *see* the repeated queries before optimizing.

### 4.10 `@Transactional` not applied

**Symptom.** Changes are not rolled back on error, or a write silently does not commit.

**Cause.** Self-invocation (2.11), a non-public method (Spring's proxy ignores non-public `@Transactional` by default), or the annotation on a class Spring does not proxy.

**Fix.** Make the method public, call it through the proxy (separate bean), and ensure transaction management is enabled (it is, by default, with the JPA starter).

### 4.11 `TransactionRequiredException`

**Symptom.** `Executing an update/delete query` or `no transaction is in progress` when performing a write.

**Cause.** A modifying JPA operation ran without an active transaction.

**Fix.** Annotate the service method `@Transactional`. For modifying `@Query` repository methods, add `@Modifying` and ensure a surrounding transaction.

### 4.12 `ddl-auto` surprises

**Symptom.** Tables vanished, data wiped between runs, or a column the code expects is missing.

**Cause.** `spring.jpa.hibernate.ddl-auto` controls schema generation. `create` and `create-drop` rebuild (and drop) the schema on startup. `update` makes additive guesses and never removes things, so drift accumulates.

**Fix.** Use `validate` (or `none`) in any shared or production environment and manage schema with Flyway/Liquibase migrations. Reserve `create-drop` for throwaway tests. Letting Hibernate own the production schema is a frequent root cause of "where did my data go".

### 4.13 `DataIntegrityViolationException`

**Symptom.** A write fails citing a constraint: unique, not-null, foreign key, or check.

**Cause.** The data violates a DB constraint. Spring wraps the driver's SQL exception.

**Fix.** Read the deepest cause for the exact constraint name. Validate input before persisting; map the violation to a meaningful API error rather than leaking the SQL message.

### 4.14 `ObjectOptimisticLockingFailureException`

**Symptom.** Concurrent update fails with a version-mismatch error.

**Cause.** An `@Version` field detected that another transaction modified the row since you read it.

**Fix.** This is optimistic locking working as designed. Catch it and retry, or surface a "please reload" message to the user. Do not remove `@Version` to "fix" it — you would trade a visible conflict for silent lost updates.

### 4.15 Detached / stale entity

**Symptom.** `detached entity passed to persist`, or `StaleObjectStateException`.

**Cause.** Calling `persist` on an entity that already has an ID (it is detached, not new), or saving an entity whose version is out of date.

**Fix.** Use `save`/`merge` for detached entities, `persist` only for new ones. Re-read before updating when working across requests.

### 4.16 `MappingException`

**Symptom.** Startup-time mapping failures: `Could not determine type`, `mappedBy reference an unknown target`, broken relationship.

**Cause.** A misconfigured association — `mappedBy` pointing at a non-existent field, a missing `@JoinColumn`, or an unmapped type.

**Fix.** Verify both sides of the relationship. For bidirectional ones, the owning side has the FK and the inverse side uses `mappedBy = "owningField"`.

### 4.17 Repository method name not parseable

**Symptom.**
```
No property 'xyz' found for type 'User'; did you mean ...
```
at startup.

**Cause.** Spring Data derives queries from method names. A name that does not map to entity properties cannot be parsed.

**Fix.** Correct the property path in the method name, or write an explicit `@Query`. The error helpfully suggests near-matches.

### 4.18 `InvalidDataAccessApiUsageException`

**Symptom.** A malformed JPQL/native query or misuse of the API.

**Cause.** Syntax error in `@Query`, wrong parameter binding, or calling an API in an unsupported way.

**Fix.** Read the nested cause; fix the query syntax or parameter names (`:name` must match `@Param("name")`).

### 4.19 Flyway / Liquibase migration failure

**Symptom.**
```
Validate failed: Migration checksum mismatch for migration version 2
```
or out-of-order / failed migration.

**Cause.** An already-applied migration file was edited (checksum changed), migrations are out of order, or a migration SQL statement failed.

**Fix.** Never edit an applied migration — add a new one. If a checksum drift is intentional and safe, `flyway repair` realigns the history table. Fix failing SQL and re-run. Keep migrations forward-only in shared environments.

### 4.20 Naming strategy surprises

**Symptom.** Hibernate looks for `user_name` but the column is `username`, or table `UserAccount` vs `user_account`.

**Cause.** Hibernate's implicit + physical naming strategies translate Java names to DB identifiers (Spring Boot defaults to snake_case physical naming).

**Fix.** Either align the DB schema to the strategy, or map explicitly with `@Table(name=...)` and `@Column(name=...)`, or override the naming strategy. Be explicit when integrating with an existing schema you do not control.


---

## 5. Web, REST, and MVC Errors

These surface as HTTP status codes returned to clients rather than as startup crashes. The trick is mapping the status code back to the Spring exception that produced it.

### 5.1 404 `NoHandlerFoundException`

**Symptom.** A request returns 404 although you "wrote the endpoint".

**Cause.** Wrong path, wrong `server.servlet.context-path`, controller not scanned (1.6), or a typo in `@RequestMapping`. By default Spring does not even throw `NoHandlerFoundException`; it serves a default 404 page, which hides the cause.

**Fix.** Verify the exact path including context path. Confirm the controller bean is registered (it appears in the mapping list logged at DEBUG for `org.springframework.web`). Set `spring.mvc.throw-exception-if-no-handler-found=true` while debugging to get the explicit exception.

### 5.2 405 Method Not Allowed

**Symptom.** The path exists but the HTTP verb is rejected.

**Cause.** `@GetMapping` where the client sent POST, etc.

**Fix.** Match the mapping annotation to the verb the client uses.

### 5.3 415 Unsupported Media Type / 406 Not Acceptable

**Symptom.** 415 on a request body, or 406 on a response.

**Cause.** 415: the request `Content-Type` does not match what the handler `consumes` (commonly sending JSON without `Content-Type: application/json`). 406: the client's `Accept` header cannot be satisfied by any configured converter.

**Fix.** Send the correct `Content-Type`/`Accept`. Ensure the right message converter is present (Jackson for JSON ships with `spring-boot-starter-web`).

### 5.4 400 Bad Request on `@RequestBody`

**Symptom.** The body fails to bind, returning 400.

**Cause.** JSON does not match the target type, a required field is absent, or the body is empty. Often paired with 7.x serialization errors.

**Fix.** Validate the JSON shape against the DTO. Enable `DEBUG` for `org.springframework.web.servlet.mvc` to see the binding failure detail.

### 5.5 `MethodArgumentNotValidException` (Bean Validation)

**Symptom.** A `@Valid @RequestBody` fails validation; default response is 400 with field errors.

**Cause.** Constraints (`@NotNull`, `@Size`, `@Email`, etc.) were violated.

**Fix.** This is validation working. Handle it in a `@RestControllerAdvice` with `@ExceptionHandler(MethodArgumentNotValidException.class)` to return a clean error payload. Ensure `spring-boot-starter-validation` is present (it is no longer transitive in Boot 3) or the annotations are silently ignored.

### 5.6 Missing parameter / path variable

**Symptom.** `MissingServletRequestParameterException` or `MissingPathVariableException`.

**Cause.** A required `@RequestParam`/`@PathVariable` was absent, or the variable name does not match the URI template.

**Fix.** Make the parameter `required = false` with a default if optional, or correct the name so it matches the `{placeholder}` in the mapping.

### 5.7 `HttpMessageNotReadableException`

**Symptom.** 400 with "JSON parse error".

**Cause.** Malformed JSON, a trailing comma, wrong types in the payload, or an empty body where one is required.

**Fix.** Fix the client payload. Surface a friendly message via `@RestControllerAdvice` rather than the raw parser error.

### 5.8 Ambiguous mapping

**Symptom.**
```
Ambiguous mapping. Cannot map 'fooController#bar' method ... There is already 'bazController#qux' mapped.
```
Fails at startup.

**Cause.** Two handler methods map to the same path + verb combination.

**Fix.** Make the mappings distinct (path, verb, or `params`/`headers` conditions).

### 5.9 CORS errors

**Symptom.** Browser console shows the request blocked by CORS policy; the server may show nothing because the preflight `OPTIONS` was rejected before your handler.

**Cause.** No CORS configuration allowing the origin/method/headers, or Security blocking the preflight (6.8).

**Fix.** Configure CORS globally (`WebMvcConfigurer#addCorsMappings`) or per-controller (`@CrossOrigin`). With Spring Security, also enable `.cors()` so the security chain delegates to your CORS config. Remember CORS is enforced by the *browser*; tools like curl will not reproduce it.

### 5.10 Filter / interceptor ordering

**Symptom.** A filter runs too early/late, or not at all.

**Cause.** Filter order is undefined unless you set it; interceptors run in registration order; Security filters sit at a specific point in the chain.

**Fix.** Set explicit order with `@Order` / `FilterRegistrationBean#setOrder`. Be aware Security's filter chain is separate and ordered by `SecurityFilterChain` config.

### 5.11 `@ControllerAdvice` not catching exceptions

**Symptom.** A global exception handler is ignored.

**Cause.** The advice class is not scanned, the `@ExceptionHandler` signature does not match the thrown type, or the exception is thrown outside the dispatcher (e.g. in a filter, before the handler).

**Fix.** Ensure the advice is a scanned bean, match the exception type (or a supertype), and remember exceptions thrown in filters are *not* seen by `@ControllerAdvice` — handle those in the filter or an error controller.

### 5.12 Static resources swallowing routes

**Symptom.** A route returns a static resource or 404 unexpectedly.

**Cause.** Overlapping paths between static resource handlers and controller mappings, or a catch-all forwarding for an SPA intercepting API paths.

**Fix.** Keep API routes under a distinct prefix (e.g. `/api/**`) and scope the SPA fallback to non-API paths.

### 5.13 Servlet vs WebFlux mismatch

**Symptom.** Blocking behavior, thread starvation, or `IllegalStateException` about blocking on a reactive thread.

**Cause.** Mixing blocking JDBC/`RestTemplate` calls inside a reactive (WebFlux) pipeline, or having both `spring-boot-starter-web` and `spring-boot-starter-webflux` so Boot picks the wrong server type.

**Fix.** Pick one model. If reactive, use non-blocking clients (`WebClient`, R2DBC) and offload unavoidable blocking work to a bounded scheduler. Do not put `starter-web` and `starter-webflux` together unless you know exactly why.

---

## 6. Security Errors (Spring Security)

Spring Security is opinionated: adding the starter locks everything down by default. Many "errors" are the framework doing exactly that.

### 6.1 401 vs 403

**Symptom.** Confusion about which you are getting.

**Cause.** 401 Unauthorized = not authenticated (no/invalid credentials). 403 Forbidden = authenticated but not authorized for this resource.

**Fix.** Diagnose accordingly: 401 -> fix authentication (token/credentials); 403 -> fix authorities/roles or the access rules.

### 6.2 Unexpected login page / generated password

**Symptom.** Adding `spring-boot-starter-security` suddenly protects every endpoint; the log prints `Using generated security password: <uuid>`.

**Cause.** Default auto-configuration secures all requests and creates a single user `user` with a random password printed at startup.

**Fix.** Define a `SecurityFilterChain` bean that declares your own authorization rules and user source. Use the generated password only for a quick local check.

### 6.3 CSRF blocking writes

**Symptom.** GET works; POST/PUT/DELETE return 403 with no obvious reason.

**Cause.** CSRF protection is on by default and rejects state-changing requests without a valid token. Stateless APIs hit this constantly.

**Fix.** For a token-based stateless API, disable CSRF (`http.csrf(csrf -> csrf.disable())`) — this is appropriate when you are not using cookie-based sessions. For browser/session apps, keep CSRF on and send the token. Do not blanket-disable CSRF on a cookie-session app.

### 6.4 `SecurityFilterChain` config not applied

**Symptom.** Your security rules are ignored; old tutorials' approach does not compile.

**Cause.** `WebSecurityConfigurerAdapter` was removed in Spring Security 6 / Boot 3. Config must now be a `SecurityFilterChain` (and `WebSecurityCustomizer`) bean.

**Fix.** Migrate to the component-based model: expose a `@Bean SecurityFilterChain`. Ensure the config class is scanned.

### 6.5 Password encoder mismatch

**Symptom.**
```
java.lang.IllegalArgumentException: There is no PasswordEncoder mapped for the id "null"
```

**Cause.** Stored passwords have no `{bcrypt}`-style prefix and you are using `DelegatingPasswordEncoder`, or raw passwords are compared against an encoder.

**Fix.** Encode stored passwords with a real `PasswordEncoder` (`BCryptPasswordEncoder`), and ensure new passwords are encoded on write. Never store plaintext. For legacy data, migrate on next successful login.

### 6.6 JWT / OAuth2 validation failures

**Symptom.** 401 with messages about expired token, invalid signature, issuer mismatch, or audience.

**Cause.** Clock skew (expired), wrong signing key / JWKS URL, mismatched `issuer-uri`, or missing scopes.

**Fix.** Verify `spring.security.oauth2.resourceserver.jwt.issuer-uri`/`jwk-set-uri`, allow small clock skew, and confirm the token's claims. Decode the JWT (header/payload) to inspect `exp`, `iss`, `aud` before guessing.

### 6.7 Method security not enforced

**Symptom.** `@PreAuthorize`/`@PostAuthorize` annotations are ignored.

**Cause.** Method security is not enabled.

**Fix.** Add `@EnableMethodSecurity` (Boot 3) on a config class. Confirm the annotated bean is a Spring-managed proxy (self-invocation defeats it, like 2.11).

### 6.8 CORS + Security preflight interaction

**Symptom.** Preflight `OPTIONS` returns 401/403 before reaching your CORS config.

**Cause.** The security filter chain runs before MVC CORS handling and rejects the unauthenticated `OPTIONS`.

**Fix.** Enable `.cors()` in the `SecurityFilterChain` so Security defers to your `CorsConfigurationSource`, and permit `OPTIONS` preflight. This is the usual missing piece when CORS "works without security but breaks with it".


---

## 7. Serialization / Jackson Errors

Spring Boot uses Jackson for JSON by default. Most serialization errors are about *constructing* objects from JSON (deserialization) or *writing* them (serialization) when the type is not Jackson-friendly.

### 7.1 `HttpMessageNotWritableException` — no serializer found

**Symptom.**
```
No serializer found for class ... and no properties discovered to create BeanSerializer
```

**Cause.** Jackson found an object with no public getters/fields to serialize (e.g. an empty bean, or a proxy with no accessible state).

**Fix.** Add getters (or fields Jackson can see), or configure `FAIL_ON_EMPTY_BEANS=false` if returning genuinely empty objects is intended. Prefer DTOs with explicit fields.

### 7.2 Infinite recursion on bidirectional relationships

**Symptom.**
```
Infinite recursion (StackOverflowError) ... through reference chain: Parent -> Child -> Parent -> ...
```

**Cause.** Serializing a JPA bidirectional association where each side references the other; Jackson loops forever.

**Fix.** Break the cycle: `@JsonManagedReference`/`@JsonBackReference`, `@JsonIgnore` on one side, or — best — serialize DTOs, not entities, so the API shape is decoupled from the persistence graph.

### 7.3 `UnrecognizedPropertyException`

**Symptom.** Deserialization fails on an unknown JSON field.

**Cause.** The incoming JSON has a property the target class does not declare, and `FAIL_ON_UNKNOWN_PROPERTIES` is on (the default).

**Fix.** Remove the stray field, add it to the DTO, or relax with `@JsonIgnoreProperties(ignoreUnknown = true)` / the global `spring.jackson.deserialization.fail-on-unknown-properties=false`. Be deliberate: silently ignoring unknown fields can hide client/contract mismatches.

### 7.4 `InvalidDefinitionException` — cannot construct instance

**Symptom.** `Cannot construct instance of ...: no Creators, like default constructor, exist`.

**Cause.** The target type has no default constructor and no `@JsonCreator`, so Jackson cannot build it.

**Fix.** Add a no-arg constructor, annotate a constructor with `@JsonCreator` + `@JsonProperty` params, or use a record (Jackson supports records). Java records and Lombok `@AllArgsConstructor` + `@JsonCreator` both work.

### 7.5 Date/time serialization

**Symptom.** `LocalDate`/`LocalDateTime` serialized as an array of numbers, or "cannot deserialize java.time", or wrong time zone.

**Cause.** The JSR-310 module is missing or `WRITE_DATES_AS_TIMESTAMPS` is on; time zone defaults differ between environments.

**Fix.** `spring-boot-starter-web` registers the JSR-310 module automatically; if you build a custom `ObjectMapper`, register `JavaTimeModule` and disable timestamp output. Set an explicit application time zone and store instants in UTC to avoid environment drift.

### 7.6 Annotation / naming-strategy mismatches

**Symptom.** A field is missing from JSON, or appears under the wrong name.

**Cause.** `@JsonIgnore` hiding a needed field, `@JsonProperty` renaming, or a global `PropertyNamingStrategy` (e.g. snake_case) the client does not expect.

**Fix.** Decide on one naming strategy for the API and apply it consistently (`spring.jackson.property-naming-strategy=SNAKE_CASE` if that is your contract). Audit `@JsonIgnore` usage.

### 7.7 Hibernate lazy proxy leaking into JSON

**Symptom.** Serialization fails or emits `ByteBuddyInterceptor`/`_persistence`-style junk, or triggers 4.8.

**Cause.** A lazy proxy or uninitialized association is handed to Jackson.

**Fix.** Serialize DTOs, not entities (the recurring theme). If you must serialize entities, the `jackson-datatype-hibernate` module can handle proxies, but DTOs remain the cleaner answer.

---

## 8. OpenAPI / Swagger / springdoc Errors

For Spring Boot 3, use **springdoc-openapi** (springfox is unmaintained and incompatible). Errors split into "the live generated spec/UI is wrong" and "an external `.yaml`/`.json` spec fails to parse during codegen".

### 8.1 Swagger UI 404 / not loading

**Symptom.** `/swagger-ui.html` or `/swagger-ui/index.html` returns 404.

**Cause.** Missing `springdoc-openapi-starter-webmvc-ui` dependency, wrong URL for the Boot/springdoc version, a custom context path not accounted for, or Security blocking the UI paths.

**Fix.** Add the correct springdoc UI starter for your Boot version. Permit `/swagger-ui/**`, `/v3/api-docs/**` in Security. Account for context path. The springdoc version *must* match the Boot major version (8.6).

### 8.2 Generated spec missing endpoints or schemas

**Symptom.** The `/v3/api-docs` output omits controllers or models.

**Cause.** Controllers outside the scanned packages, endpoints not annotated in a way springdoc detects, or DTOs only referenced in ways the scanner cannot infer.

**Fix.** Ensure controllers are scanned; add `@Operation`/`@Schema` where inference is insufficient; expose request/response types explicitly in method signatures rather than via generic `Object`/`ResponseEntity<?>`.

### 8.3 OpenAPI spec fails to parse (codegen)

**Symptom.** `openapi-generator`/`swagger-codegen` aborts: invalid spec, unsupported version, schema error.

**Cause.** YAML indentation issues (same family as 3.5), wrong `openapi:` version field, or a schema that violates the spec.

**Fix.** Validate the spec with an online/CLI validator before generating. Confirm the `openapi: 3.0.x`/`3.1.x` version matches generator support. Treat the spec file with the same YAML caution as `application.yml`.

### 8.4 `$ref` resolution failures

**Symptom.** `Could not resolve reference` / `$ref` not found in a multi-file spec.

**Cause.** Relative path wrong, the referenced component name misspelled, or the bundler not following external file refs.

**Fix.** Bundle multi-file specs into one before generation, or verify each `$ref` path resolves relative to the file it appears in. Component names are case-sensitive.

### 8.5 Generated code does not match schema

**Symptom.** Generated models miss fields, use wrong types, or required/nullable is off.

**Cause.** Schema lacks `required`, `nullable`, `format`, or uses constructs the chosen generator maps differently; generator config (e.g. `useOptional`, date library) not set.

**Fix.** Make the schema explicit (`required`, `format: date-time`, `nullable`). Pin generator options. Regenerate; never hand-edit generated files (they get overwritten).

### 8.6 Version conflicts

**Symptom.** Startup or UI breakage after upgrading Boot; springfox classes not found.

**Cause.** springfox does not support Boot 3 / Jakarta namespace; or a springdoc version mismatched to Boot.

**Fix.** Remove springfox entirely on Boot 3. Use the springdoc version aligned with your Boot minor version. This is a hard compatibility constraint, not a preference.

### 8.7 "Authorize" button / security scheme missing

**Symptom.** Swagger UI shows no auth option for a secured API.

**Cause.** No `SecurityScheme` defined in the OpenAPI config.

**Fix.** Declare a `@SecurityScheme` (or via `OpenAPI` bean) for bearer/JWT/basic and reference it on operations, so the UI renders the Authorize control.

---

## 9. Testing Errors

Tests fail for two distinct reasons: the *context* they bootstrap is wrong (slices, mocks), or the *assertions/timing* are wrong. Most of the painful ones are the former.

### 9.1 `Failed to load ApplicationContext` in tests

**Symptom.** The most common test failure; nothing runs because the context will not build.

**Cause.** Same as 1.10 — a wiring/config failure during test bootstrap. With slices, a bean the slice loads needs a collaborator that the slice does not provide.

**Fix.** Read the nested cause. Provide missing collaborators via `@MockitoBean`, narrow to the right slice, or use a full `@SpringBootTest` if the test genuinely needs the whole context.

### 9.2 `@MockBean`/`@MockitoBean` not replacing the real bean

**Symptom.** The real implementation runs instead of your mock.

**Cause.** The mock type does not match the injected type, the bean is created before the mock is registered, or you used a plain Mockito `@Mock` (which does not touch the Spring context).

**Fix.** Use `@MockitoBean` (Boot 3.4+; `@MockBean` is deprecated) so Spring swaps the bean in the context. Match the exact type/qualifier. Plain `@Mock` only works for non-Spring unit tests.

### 9.3 Test slice loads too much or too little

**Symptom.** `@WebMvcTest` cannot find a service bean; or `@DataJpaTest` unexpectedly needs web beans; or a "unit" test boots the whole app and is slow.

**Cause.** Slice annotations load only a subset of the context: `@WebMvcTest` loads MVC + controllers but not services/repositories; `@DataJpaTest` loads JPA but not controllers. Anything outside the slice must be mocked.

**Fix.** Match the slice to what you are testing and mock the rest. Use `@SpringBootTest` only for true integration tests. Choosing the narrowest slice that fits keeps tests fast and failures legible.

### 9.4 `@SpringBootTest` webEnvironment confusion

**Symptom.** `TestRestTemplate`/`WebTestClient` cannot connect, or port clashes.

**Cause.** Default `webEnvironment = MOCK` does not start a real server, so a real HTTP client has nothing to hit; `DEFINED_PORT` can clash (1.8).

**Fix.** Use `webEnvironment = RANDOM_PORT` with `TestRestTemplate`/`WebTestClient` for real HTTP tests; use `MOCK` + `MockMvc` for servlet-level tests without a server.

### 9.5 Context/state leaking between tests

**Symptom.** Tests pass alone but fail together, or vice versa.

**Cause.** Shared mutable state, cached context reused across classes, or a test that mutated a bean/DB without cleanup.

**Fix.** Keep tests independent; reset state in `@AfterEach`; use `@DirtiesContext` sparingly when a test truly corrupts the context (it forces a slow rebuild). Prefer transactional rollback or per-test data setup over `@DirtiesContext`.

### 9.6 Testcontainers not starting

**Symptom.** `Could not find a valid Docker environment`, or image pull failures.

**Cause.** Docker not running/available on the machine or CI runner, wrong image tag, or network restrictions pulling the image.

**Fix.** Ensure Docker is available in CI; pin a known image tag; pre-pull images in restricted networks. Use `@ServiceConnection` (Boot 3.1+) to auto-wire container connection details.

### 9.7 H2-vs-real-DB dialect differences

**Symptom.** Tests pass on H2 but fail on the real database (or the reverse).

**Cause.** H2 does not perfectly emulate Postgres/MySQL/Oracle SQL, functions, or constraint behavior.

**Fix.** Test against the real engine via Testcontainers for anything SQL-dialect-sensitive. Reserve H2 for fast, dialect-agnostic repository tests. Relying on H2 to validate Postgres-specific SQL is a frequent source of false confidence.

### 9.8 `@Transactional` test rollback hides behavior

**Symptom.** A test passes but the same code misbehaves in production; or flush-timing bugs are masked.

**Cause.** `@Transactional` tests roll back and may not flush, so constraint violations and ID generation that happen at commit are never exercised.

**Fix.** For persistence-critical tests, force a flush (`entityManager.flush()`) or test without the rollback wrapper so commit-time behavior is real.

### 9.9 Mockito `UnnecessaryStubbingException`

**Symptom.** A test fails citing an unused stub under strict stubs.

**Cause.** You stubbed a call the code path never makes (often after refactoring).

**Fix.** Remove the dead stub, or use `lenient()` for a genuinely shared stub. The error is usually pointing at a real mismatch between the test's assumptions and the code.

### 9.10 Flaky tests (time/order/async)

**Symptom.** Intermittent failures with no code change.

**Cause.** Dependence on wall-clock time, test execution order, or unsynchronized async results.

**Fix.** Inject a fixed `Clock`; never assume test ordering; await async results deterministically (`Awaitility`) instead of `Thread.sleep`. Flakiness is a real defect in the test, not bad luck — fix the nondeterminism.


---

## 10. Build, Packaging, and Dependency Errors

These often look like runtime errors but originate in the build: a wrong version, a missing dependency, a misconfigured plugin.

### 10.1 Version conflicts: `NoSuchMethodError` / `NoClassDefFoundError`

**Symptom.** Compiles fine, then at runtime: `NoSuchMethodError`, `NoClassDefFoundError`, or `AbstractMethodError`.

**Cause.** Two versions of the same library on the classpath; the wrong one wins and lacks a method the code expects. Classic transitive-dependency hell.

**Fix.** Inspect the dependency tree (`mvn dependency:tree`, `gradle dependencies`) to find the duplicate. Let the Spring Boot BOM manage versions (10.2); override only deliberately. Exclude the unwanted transitive version.

### 10.2 Dependency convergence and the BOM

**Symptom.** Unpredictable versions, or you pinned a version that broke compatibility.

**Cause.** Not using `spring-boot-dependencies` BOM (or `spring-boot-starter-parent`), so versions are unmanaged and diverge.

**Fix.** Use the parent/BOM and *omit* explicit versions for managed dependencies. This is the single most effective way to avoid the conflicts in 10.1. Override a managed version only with a clear reason and a note.

### 10.3 `ClassNotFoundException` at runtime

**Symptom.** A class present at compile time is missing at runtime.

**Cause.** Wrong scope: a dependency marked `provided`, `optional`, or `test` is not packaged into the runtime artifact.

**Fix.** Use the correct scope. Runtime-needed libraries must be `compile`/`implementation`, not `provided`/`compileOnly` (unless the container truly provides them).

### 10.4 Fat jar problems

**Symptom.** `no main manifest attribute, in app.jar` (1.11), or `ClassNotFoundException` for a nested-jar class when running with a plain `java -cp`.

**Cause.** The jar was built without the Spring Boot plugin's repackaging, or the nested-jar layout is being loaded by a tool that does not understand it.

**Fix.** Build with the Spring Boot Maven/Gradle plugin so it produces an executable, properly-manifested fat jar. Run it with `java -jar app.jar`, not by manually setting a classpath into the nested jars.

### 10.5 Lombok not generating code

**Symptom.** `cannot find symbol` for getters/builders that Lombok should generate, or fields that are null because no constructor was generated.

**Cause.** Annotation processing disabled in the IDE/build, the Lombok plugin missing, or `@Data` removing a no-arg constructor JPA needs (4.7).

**Fix.** Enable annotation processing; install the Lombok IDE plugin; declare Lombok as an annotation processor (`annotationProcessor` in Gradle / proper `provided`+processor in Maven). Add `@NoArgsConstructor` where JPA needs it.

### 10.6 Java version mismatch

**Symptom.** `UnsupportedClassVersionError: ... has been compiled by a more recent version of the Java Runtime`.

**Cause.** Compiled with a newer JDK than the runtime JRE.

**Fix.** Align `sourceCompatibility`/`<java.version>` with the runtime JDK. Verify with `java -version` on the deploy target. This is purely a version-alignment fix, not a code issue.

### 10.7 Duplicate classes / split packages

**Symptom.** `SecurityException: class's signer information does not match`, sealing violations, or the wrong class loaded.

**Cause.** The same package spread across two jars (split package), or duplicate classes from shaded/relocated dependencies.

**Fix.** Remove the duplicate jar; avoid shading libraries that are already managed; check the dependency tree for two artifacts providing the same package.

### 10.8 GraalVM native image reflection/proxy failures

**Symptom.** Works on the JVM, fails as a native image: `ClassNotFoundException`, missing reflective access, proxy not found at runtime.

**Cause.** Native image needs ahead-of-time hints for reflection, proxies, and resources. Code that reflects dynamically without registration breaks.

**Fix.** Use Spring's AOT processing and `RuntimeHints`/`@RegisterReflectionForBinding`; provide reachability metadata for libraries that need it. Test the native build in CI, since these failures never appear on the regular JVM.

---

## 11. Runtime, Performance, and Operational Errors

The application started fine but misbehaves under load or over time.

### 11.1 `OutOfMemoryError`

**Symptom.** `java.lang.OutOfMemoryError: Java heap space` (or `Metaspace`, or `Direct buffer memory`).

**Cause.** Heap: a leak (caches without eviction, unbounded collections, retained sessions) or simply too small a heap for the workload. Metaspace: too many loaded classes (often hot-reloading or dynamic class generation). Direct memory: NIO buffers not released.

**Fix.** Capture a heap dump (`-XX:+HeapDumpOnOutOfMemoryError`) and analyze with a profiler to find what retains memory. Bound your caches. Size the heap to the workload. Do not just raise `-Xmx` before confirming it is sizing and not a leak — a leak will exhaust any heap eventually.

### 11.2 Thread pool exhaustion / blocked threads

**Symptom.** Requests queue and time out; the app appears hung; thread dumps show many threads `BLOCKED`/`WAITING`.

**Cause.** Blocking calls (slow DB, external HTTP) holding all worker threads, deadlocks, or an undersized executor.

**Fix.** Take a thread dump (`jstack`) and look for the common blocking point. Add timeouts to every external call, isolate slow work onto separate bounded pools, and fix deadlocks. Tune pool sizes only after identifying the bottleneck.

### 11.3 Slow startup

**Symptom.** The app takes a long time to become ready.

**Cause.** Excessive component scanning, eager initialization of heavy beans, blocking work in `@PostConstruct`, or waiting on a slow external resource at boot.

**Fix.** Narrow component scanning, defer heavy beans with `@Lazy`, move slow work off the startup path, and add timeouts/retries to boot-time dependencies. Consider AOT/native for cold-start-sensitive workloads.

### 11.4 Actuator endpoint exposure / health failures

**Symptom.** `/actuator/health` returns DOWN, endpoints are missing, or sensitive endpoints are exposed.

**Cause.** A health indicator (DB, disk, broker) reporting DOWN; endpoints not exposed (`management.endpoints.web.exposure.include`); or too much exposed publicly.

**Fix.** Read the health detail to find the failing component. Expose only the endpoints you need, and secure them. Treat broad exposure of `/env`, `/configprops`, `/heapdump` as a security risk (3.12).

### 11.5 Logging misconfiguration

**Symptom.** No log output, duplicated lines, or wrong levels.

**Cause.** Two logging backends on the classpath (e.g. Logback and Log4j2 both present), a misconfigured `logback-spring.xml`, or wrong `logging.level.*`.

**Fix.** Use exactly one logging backend (Boot defaults to Logback; exclude others). Set levels via `logging.level.<logger>=DEBUG`. For targeted debugging, raise the level only for the relevant package (e.g. `org.springframework.web`, `org.hibernate.SQL`) rather than root, to avoid drowning in noise.

### 11.6 Graceful shutdown and resource leaks

**Symptom.** In-flight requests dropped on shutdown, or resources (files, connections) not released.

**Cause.** No graceful shutdown configured, or beans not releasing resources in `@PreDestroy`.

**Fix.** Enable `server.shutdown=graceful` with a `spring.lifecycle.timeout-per-shutdown-phase`. Release resources in `@PreDestroy`/`DisposableBean`.

### 11.7 Clock, time zone, locale surprises

**Symptom.** Times off by hours, dates formatted unexpectedly, parsing failing in some locales.

**Cause.** Reliance on the JVM default time zone/locale, which differs across environments.

**Fix.** Store and compute in UTC; set an explicit application time zone; pass an explicit `Locale` to formatters. Inject a `Clock` so time is testable (9.10).

---

## Appendix A: A Repeatable Debugging Procedure

When anything fails, work this checklist in order rather than guessing:

1. **Find the banner.** Search the log for `APPLICATION FAILED TO START` and the asterisk rows. Read `Description` and `Action`.
2. **Find the deepest `Caused by:`.** That is the root, not the top of the trace.
3. **Identify the phase.** Startup, DI, config, data, web, security, serialization, test, build, or runtime — that tells you which section above applies.
4. **Turn on the right visibility.** `--debug` for the auto-configuration report; `logging.level.org.springframework.web=DEBUG` for MVC mapping/binding; `spring.jpa.show-sql=true` (dev) for queries; Actuator `/env` and `/configprops` for effective configuration; a thread dump for hangs; a heap dump for memory.
5. **Reproduce in isolation.** A failing slice test or a minimal main method narrows the surface dramatically.
6. **Change one thing at a time.** Spring's cascading errors make multi-change debugging unreliable.

## Appendix B: The Highest-Value Diagnostic Switches

| Goal | Switch |
|---|---|
| See why auto-config did/didn't apply | run with `--debug` (auto-configuration report) |
| See effective property values and their source | Actuator `/actuator/env`, `/actuator/configprops` |
| See request mappings and binding | `logging.level.org.springframework.web=DEBUG` |
| See SQL Hibernate runs | `spring.jpa.show-sql=true` (+ `format_sql`), `logging.level.org.hibernate.SQL=DEBUG` |
| See bind parameters | `logging.level.org.hibernate.orm.jdbc.bind=TRACE` |
| Catch unmapped routes explicitly | `spring.mvc.throw-exception-if-no-handler-found=true` |
| Detect connection leaks | `spring.datasource.hikari.leak-detection-threshold=20000` |
| Heap dump on OOM | JVM flag `-XX:+HeapDumpOnOutOfMemoryError` |
| Thread dump on hang | `jstack <pid>` |

## Appendix C: Reading a Cascading Stack Trace (worked mental model)

A typical wiring failure reads, top to bottom: `UnsatisfiedDependencyException` on the controller, then `BeanCreationException` on the service, then `NoSuchBeanDefinitionException` for the repository. Beginners read the top (controller) and start editing the controller. The *real* fix is at the bottom: the repository bean does not exist — perhaps the package is not scanned, or a starter is missing. Always let the chain lead you down to the last cause, fix that, and the upper layers resolve themselves.

---

*This guide intentionally errs toward breadth. For any single error, the fastest path is still: find the banner, find the deepest cause, identify the phase, then jump to the matching section above.*

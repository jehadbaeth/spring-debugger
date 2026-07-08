# Spring Boot Conventions (GMS Service Modules)

This page documents the Spring Boot conventions followed during the development process in GMS
Service Modules.

## Overview

Our microservices use a standard Spring Boot template with a layered architecture separating
concerns into three tiers:

- **REST API Service**: the HTTP boundary layer handling DTO transformation and syntactic validation
- **Business Service**: the domain logic layer handling semantic validation and repository interaction
- **Entity/Model layer**: the persistence layer with strict field constraints

DTOs reside at the API boundary, Entities/Models reside in the domain, and the API Service bridges
between them.

The template has the following rough folder structure:

### Package structure

```
com.dlr.{service-name}
├── config/             # Spring @Configuration classes, beans
├── idle_mode/          # Idle mode logic
├── rest_api/
│   ├── controller/     # @RestController classes
│   └── advice/         # @RestControllerAdvice, exception handlers
├── service/            # @Service classes, business logic
├── repository/         # @Repository interfaces (Spring Data)
├── model/
│   ├── entity/         # JPA @Entity classes
│   └── dto/            # Request/Response records or classes
├── mapper/             # MapStruct @Mapper interfaces
├── event/              # Application event records
├── exception/          # Custom exception classes
└── utils/              # Stateless utility classes
```

### Auxiliary files

```
src/
├── main/
│   ├── java/com/dlr/{service-name}/
│   └── resources/
│       ├── application.yml
│       ├── application-local.yml
│       ├── application-test.yml
│       ├── db/migration/          # liquibase migration scripts
│       ├── logback.xml
│       ├── message.properties
│       └── openapi.yaml
└── test/
    └── java/com/dlr/{service-name}/
└── build.gradle
```

## 🟦 rest_api/

**Purpose:** The REST API Service resides in the `rest_api` package at the HTTP boundary of the
application. It implements interfaces from the OpenAPI specification and is invoked by the
generated `RestController`. You never write controller logic manually; the controller is
auto-generated boilerplate.

API Service validation is syntactic, checking the structure, format, and consistency of incoming
data against the API contract. It usually occurs by:

- OpenAPI Specification (constraints in `openapi.yaml`) which runs before the service fires,
  enabling early malformed request detection.
- Constraints not expressible in `openapi.yml` are checked explicitly.

Include:

- REST controllers (`@RestController`)
- API request/response DTOs
- API mappers (entity ↔ DTO)
- Validation logic (`@Valid`, constraint annotations)
- Swagger/OpenAPI annotations if used

Structure example:

```
rest_api/
 ├─ controller/
 ├─ dto/
 └─ mapper/
```

## 🟩 service/

**Purpose:** Business Service. This layer handles domain logic, semantic validation, and
repository interaction. Services typically receive calls from the REST API Service or a
Kafka/RabbitMQ message consumer. It works exclusively with domain model/entity types and never
uses DTOs directly.

- `@Transactional` belongs on service methods only. Never use it on controllers or repositories.
- Use `@Transactional(readOnly = true)` for all read operations.
- Entities must not be returned from service methods. Always map to DTOs before returning.
- Semantic validation occurs in this layer.

**Note:** The difference between syntactic validation (API layer: is this a valid UUID? is the
string non-empty?) and semantic validation (service layer: does this date range make sense? is
this altitude within GEO's range?).

Include:

- Service interfaces & implementations
- Business logic methods
- Calls to repositories
- Calls to Kafka producers
- Calls to external services
- Everything using the `@Service` keyword from Spring

**Naming Convention:** Files shall be named with `Service`.

```
service/
 ├─ OnevsOneScreeningService.java
 ├─ FGIdentificationService.java
```

## 🟫 repository/

**Purpose:** Entity/Model layer, the persistence layer, with strict field constraints. Utilizes
Spring JPA to access the DB.

Include:

- `JpaRepository` interfaces
- Complex query methods
- Custom repository interfaces & implementations

Structure example:

```
repository/
 ├─ SatelliteRepository.java
 └─ custom/
```

## 🟨 model/

**Purpose:** Application domain model. This package serves as the glue between the layers. With
mappers, these POJOs act as data contracts between layers. Entities facilitate calls between the
service layer and repositories. Entities typically use Spring JPA annotations.

When creating entities, all `String` fields must be bounded. To enforce this or other constraints
requiring user input editing, use one of the following methods based on your needs:

- Changes enforced in the persistence layer use `AttributeConverter`. It intercepts the value at
  the JPA level before it reaches the database. This flow operates independently from the rest of
  the code.
- Another approach is to create a custom `@NullIfBlank` annotation and use it like Bean validation.

Include:

- JPA entities (PostgreSQL)
- Domain objects
- Value objects (when needed)
- Enumerations

Structure example (can remain top level):

```
model/entity/
model/dto/
model/enums/
model/valueobject/ (only when needed)
```

DTOs can go here or in `rest_api/dto`, depending on preference:

- If DTOs are purely API-oriented → put in `rest_api/dto/`
- If DTOs are shared with Kafka or services → keep in `model/dto/`

If data is untrustworthy, such as input from external sources (e.g., user input or library data),
parse it first into an intermediate value object instead of validating it directly in the service
layer. This keeps the object valid. Since the type is safe, further downstream validation is
usually unnecessary.

**Note:** Never pass a DTO into the business service, and never return an Entity to the RestAPI
controller.

## 🟦 MySpringBootApplication.java

**Purpose:** Main entry point of your Spring Boot application.

Typical content:

- `@SpringBootApplication`
- ComponentScan definitions (if needed)
- Initializers / startup logs

## 🟦 Constants.java

**Purpose:** Keep application-wide static constants.

Typical examples:

- Common string constants
- Physical constants, not given by external model files
- Unit/format constants

Avoid mixing configuration here — config goes into `application.properties`.

## 📂 Functional Folders

### 🟥 exception/

**Purpose:** Custom, domain-specific exceptions and their handlers. Some common exceptions might
arise from the common package.

Include:

- Custom exception classes (`InvalidStateException`, `ResourceNotFoundException`)
- Global exception handler with `@ControllerAdvice`
- Common API error response model (e.g., `ErrorResponse`)

### 🟩 idle_mode/

**Purpose:** Project-specific logic for idle/standby modes.

This is for being able to deploy services without them running any processing. The main use case
for the idle mode is to shut down the system. Microservices remain alive to keep continuing
on-going events/workflows but without starting to trigger any new processes.

### 🟧 messages/

**Purpose:** Kafka-related message definitions & serialization models.

Include:

- Kafka consumer and producer configurations
- Kafka payload classes (DTOs)
- Message mappers (domain ↔ Kafka DTO)

Do NOT include:

- Kafka listeners (part of the services)
- Business logic (→ place under `service/`)

### 🟪 process/

**Purpose:** Kafka listeners and message processing workflows.

This folder is ideal for:

- `@KafkaListener` classes
- Inbound message handlers
- Orchestration workflows between Kafka and services
- Event-processing pipelines

**Golden rule:** Put message consumption logic here, not in `service/`.

```
process/
 ├─ KafkaEventListener.java
 ├─ MessageProcessor.java
 └─ inbound/
```

### 🟫 tools/

**Purpose:** Generic utilities and reusable components. Scientific core algorithms.

Include:

- General helpers
- Reusable components
- Scientific core algorithms

### 🟪 utils/

**Purpose:** Low-level utility functions for reuse across tools.

Ideally, code in Utils (and likely in tools) should follow these rules:

- Functions must be stateless, with no instance variables or injected Spring beans.
- Functions must be pure, producing the same output for the same input every time.
- Functions should be generic and reusable, without dependencies, callable in any context.
- Functions violating these rules likely belong in a Service.
- Utils functions are typically static, eliminating the need to instantiate their class. Since
  Lombok is used in Okapi's services, annotating a class with `@UtilityClass` makes it final and
  creates a private empty constructor.

Examples:

- Date/time helpers
- Geometry/math utilities
- JSON/YAML parsing helpers
- Retry/polling utilities

Avoid placing:

- Business logic
- Database queries
- REST logic

## Using OpenAPI

Define the interface contract in `openapi.yml` before writing any controller code. The spec is the
source of truth.

## Dependency Injection

Constructor injection is required. Field injection (`@Autowired` on fields) is forbidden.
Constructor injection makes dependencies explicit, enables immutability, and makes unit testing
trivial (no Spring context required in tests).

## Validation

The API boundary (`rest_api`) is our first line of defense.

Java Bean Validation (JSR 380/303): annotate DTO fields with the relevant validation annotation
and add an error message if needed.

Example:

```java
@NotBlank(message = "satalliteId must not be blank")
String satalliteId,
```

Common validation annotations: `@NotNull`, `@Size`, `@Min`, `@Max`, `@Email`, `@NotBlank`,
`@UniqueElements`.

Annotate `@RequestBody` and `@PathVariable` parameters with `@Valid` to trigger DTO bean
validation:

```java
public ResponseEntity<?> create(@Valid @RequestBody SatelliteDto dto)
```

### Custom Constraints

When we need to go beyond the default annotations a custom constraint annotation can be created.

Step 1: create an interface representing the annotation.

```java
@Documented
@Constraint(validatedBy = IsoCountryCodeValidator.class)
@Target({ FIELD, PARAMETER })
@Retention(RUNTIME)
public @interface IsoCountryCode {
    String message() default "Must be a valid ISO 3166-1 alpha-2 country code";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

Step 2: implement the actual validation logic.

```java
public class IsoCountryCodeValidator
        implements ConstraintValidator<IsoCountryCode, String> {

    private static final Set<String> CODES = Set.of(Locale.getISOCountries());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        return value == null || CODES.contains(value.toUpperCase());
    }
}
```

Step 3: use the annotation where relevant.

## Creating Rest API Controllers

- Controllers only call service methods. No business logic, no repository calls.
- Always return `ResponseEntity` for explicit status control.
- Use `@Validated` at the class level to enable constraint validation on path/query params.

## API Versioning

All public endpoints must be versioned using URL path versioning. When a breaking change is
required, introduce a new version and maintain the old one during a deprecation window.

```
GET  /api/v2/propagation     ← new version
GET  /api/v1/propagation     ← maintained during deprecation window
```

**Note:** Removing, renaming a field, changing a field data type, or changing HTTP status code are
all considered BREAKING changes. Adding optional fields, adding a response field are considered
backward compatible and non-breaking.

## Logging

- Use `LoggerClient` until the Logging ADR is approved by DLR.
- Do not log requests (e.g., request received/completed) as the framework already logs them.
- Use an appropriate log level based on invocation frequency (ERROR, WARN, INFO, DEBUG, TRACE).
- Focus log messages on the current context; avoid referencing events three levels down.
- Log arguments (e.g., `log.info("id: {}", id)`).
- Do not log errors that are already being thrown.
- Never expose the stack trace.
- Never use `System.out` for logging; use `log.debug()` instead.

## Exception Handling

Layers throw typed exceptions; a central handler catches and maps them to HTTP. Neither the
Service API nor the Rest API layer know HTTP status codes. They throw domain-specific exceptions,
and the `@RestControllerAdvice` handles catching.

The Rest API layer handles syntactic validations, and the Service layer handles semantic
validations, throwing errors of their respective types. Bean validations (using `@Valid`
annotations and OpenAPI constraints) are handled by Spring as `MethodArgumentNotValidException`.

All custom exceptions should extend a unified structure. The rough shape of this parent exception:

```java
public abstract class ApplicationException extends RuntimeException {
    private final String errorCode;

    protected ApplicationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    public String getErrorCode() { return errorCode; }
}
```

## Null Safety & Optional

- Use `Optional` as return type for "might not exist" queries.
- Never use `Optional` as a method parameter.
- Never store `Optional` in a field.
- Unwrap with `orElseThrow` in the service layer.
- Use `.map()` / `.filter()` — never call `.get()` directly.

**Note:** We always prefer catching errors as early as possible, pushing detection into the
compilation phase where we can. Annotate public method parameters and return types with `@NonNull`
or `@Nullable`. This enables static analysis (IntelliJ, SpotBugs) to catch null pointer exceptions
at compile time.

## Mapping Objects DTO ↔ Entity

Perform all mapping using MapStruct — a Java annotation processor that generates type-safe,
efficient bean mapper code at compile time.

```groovy
// build.gradle
dependencies {
    implementation 'org.mapstruct:mapstruct:1.5.5.Final'

    // If using Lombok — order matters!
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
}
```

## Date and Time Handling

- Use `java.time.Instant` for internal logic, database storage, and API contracts. `Instant`
  represents a precise point on the UTC timeline, avoiding timezone ambiguity in `LocalDateTime`.
- Delegate all conversions to `TimeUtils` to enforce UTC:

```java
public final class TimeUtils {
    private static final ZoneOffset UTC_OFFSET = ZoneOffset.UTC;

    public static Optional<Instant> toInstant(LocalDateTime ldt) {
        return Optional.ofNullable(ldt).map(ld -> ld.toInstant(UTC_OFFSET));
    }

    public static Optional<LocalDateTime> fromInstant(Instant instant) {
        return Optional.ofNullable(instant).map(i -> LocalDateTime.ofInstant(i, UTC_OFFSET));
    }
}
```

- When defining DTOs with OpenAPI: use `type: string` with `format: date-time` to enforce ISO-8601
  UTC strings.
- When mapping DTOs ↔ Entity using MapStruct: avoid implicit conversions. Define explicit default
  methods in mapper interfaces that delegate to `TimeUtils` to prevent using the JVM system default
  timezone.

## Spring profiles

Not really relevant yet since there is no dedicated staging or prod infra AFAIK.

## Missing points

Health API and actuators, Async calls, Kafka, Code style and formatting (Spotless integration).

## ⭐ Recommended Summary Table

| Folder | What Goes Inside | Notes |
|---|---|---|
| `exception/` | Custom exceptions + global handler | API-friendly errors |
| `idle_mode/` | Idle-state management logic | Project-specific |
| `messages/` | Kafka DTOs, message schemas | No listeners here |
| `model/` | Entities, domain models, enums | Pure domain |
| `process/` | Kafka listeners & processing flows | Event-driven logic |
| `repository/` | Spring Data repositories | Data access layer |
| `rest_api/` | REST controllers + API DTOs | HTTP interface |
| `service/` | Business logic | The heart of the app |
| `tools/` | Scientific Core Algorithms | Use subpackages to group functionalities |
| `utils/` | Reusable utilities | Simple static methods in Utils classes |

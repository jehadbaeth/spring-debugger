# Integration Test Structure & Metadata (GMS SM / SensSched, Robot Framework)

Source convention document for the Robot Framework convention rules in this plugin. The enforceable
rules extracted from it are tracked in [CONVENTIONS-PLAN.md](../CONVENTIONS-PLAN.md) and implemented
against `conventions.yaml`.

Nightly integration test report:
https://gms-service-module.pages.gitlab.dlr.de/senssched-service/report.html

## Test IDs

The structure for test case IDs shall be `T-<Scope>-<Numeric ID>`, where:

- `<Scope>` is either `SYS` for system integration test cases, or `ST`, `CA`, `FG`, `RE` for tests on
  macroservice level.
- `<Numeric ID>` is a four digit number with leading zeroes, unique within the scope (as with
  requirement IDs).

Examples: `T-SYS-0001`, `T-FG-0004`, `T-ST-0004`, `T-ST-0016`.

## Definitions

- **test case**: an abstract description of a test (title, description, pass-fail criteria).
  Implemented as a Robot suite file with test metadata, not necessarily the step implementation.
- **test procedure**: a concrete implementation of a test case into test steps. Implemented as Robot
  test cases within a suite (robot file).
- **test step**: a concrete step during execution, implemented as Robot test cases; comprises one or
  several Robot keywords.

## Project Structure

```
tests/
├── resources/    # Reusable keywords (keywords.robot)
├── tests/        # Test cases grouped into suites (example_suite.robot)
├── variables/    # Configuration variables (config_variables.robot)
├── results/      # Auto-generated test results (created during runs)
├── testdata/     # Test files for testing (JSON)
```

- **Test Suites (tests/)** contain test cases within robot files that validate specific aspects, each
  with a clear Pass-Fail criterion.
- **Reusable Keywords (resources/)** summarise common actions and checks.
- **Configuration Variables (variables/)** store environment-specific or reusable data.
- **Results (results/)** are auto-generated during runs.
- **Test Data**: files used for test procedures, mostly JSON.

### Test Case Grouping (ST service, under `/tests`)

```
/tests/catalogue-optimization/
/tests/if/api/
/tests/if/eusst-db/
/tests/if/gestra/
/tests/if/glaras/
/tests/if/kafka/
/tests/miscellaneous/
/tests/performance/
/tests/senssched/
```

New folders can be defined if and where appropriate.

## Metadata in Robot Framework

The most important metadata elements are: **Test ID**, **Test Description**, **Requirement ID**,
**Pass-Fail Criteria**.

### 1. `[Documentation]` tag

A clear, concise description of a test's purpose. Included in `report.html` and `log.html`.

```robotframework
[Documentation]
Test Description: ${TEST_DESCRIPTION}
```

### 2. `[Tags]` for categorization

Support structured organization and targeted filtering, and associate tests with requirements.

```robotframework
[Tags]    smoke    login    REQ-XYZ-LOGIN
```

### 3. Test Case-Level Metadata `[Metadata]`

Defined at suite level to describe the overall context. A structured way to document Test ID, Test
Description, and Pass-Fail criteria directly in the suite.

```robotframework
*** Settings ***
Metadata    Test Level           Integration
Metadata    System Under Test    API endpoint
Metadata    Test ID              T-API-1909
Metadata    Test Description     This test...
Metadata    Pass Fail Criteria   This test fails if ...
```

## Setup and Teardown

- **`[Setup]`** runs before a test case (log environment info, prepare test data/system state).
  Example: `[Setup]    Log Environment Info`
- **`[Teardown]`** runs after a test finishes regardless of result (clean up resources, finalize
  reports, log metadata). Example: `[Teardown]    Close All Sessions`

## Documenting Test Steps

The `[Documentation]` in each test case should log detailed information about each test step. The
step number, title, step description, and an observation have to be specified for each step.

Full example:

```robotframework
*** Settings ***
Library           RequestsLibrary
Library           OperatingSystem
Library           String
Library           Process
Resource          ../resources/petstore_keywords.robot
Metadata          Test Level           Integration
Metadata          System Under Test    API endpoint
Metadata          Test ID              T-ID-007
Metadata          Pass-Fail Criteria   Each endpoint must return a valid HTTP status.

*** Variables ***
${BASE_URL}       http://localhost:8083
${SUCCESS_FILE}   logs/successful_endpoints.txt
${FAILURE_FILE}   logs/failed_endpoints.txt

*** Test Cases ***
Test First Petstore Endpoint
    [Tags]             REQ-Test-123
    [Documentation]    Test Description
    ...                Passfail
    Log    Starting testcase [...]
    Keyword  ${variable1} ${...} ...

Test second Petstore Endpoint
    [Tags]             REQ-Test-124
    [Documentation]    Test Description
    ...                Passfail
    Log    Starting testcase [...]
    Keyword  ${variable1} ${...} ...
```

## Test Reports & SVR Documentation

Each test run generates `output.xml` (detailed results and metadata), `report.html` (visual report),
and `log.html` (detailed execution log). These are the foundation of the Software Verification
Report (SVR).

## Test Data

- Inserted via REST APIs; data stored as JSON files in the repository.
- Naming convention: `<method>_<service>_<endpoint>.json`. May contain a single JSON object or a
  list.
- Each test data set has an identifier `TD-ST-<NNN>`. Two types: **base data** (generic, rarely
  changes, e.g. `TD-ST-001`) and **test-specific data** (per-test customization).
- Rule: modifying test data for one test must not break other tests. Organized under `testdata/`:

```
testdata/
├── TD-ST-001/   # base data for GMS, SensSched services
├── TD-ST-002/   # test-case-specific data (additional JSON files)
```

- Applied in two phases: **setup** (insert via REST) and **teardown** (clean up, may truncate tables
  and restart services).

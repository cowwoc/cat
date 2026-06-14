# Requirements.java API Conventions

## Overview

Use [requirements.java](https://github.com/cowwoc/requirements.java) for all validation needs.
Version: 13.2 (or later)

When requirements.java already provides validator for check, use validator API instead of manual
`if (...) throw ...` blocks.

## Entry Points

Three validators for different contexts:

| Method | Use Case | Exception Type |
|--------|----------|----------------|
| `requireThat(value, name)` | Method preconditions (public API) | `IllegalArgumentException` |
| `that(value, name)` | Class invariants, postconditions, private methods | `AssertionError` |
| `checkIf(value, name)` | Multiple failures, custom error handling | Configurable |

Library docs describe these three validator families directly:
- `requireThat()` for method preconditions
- `that()` / `assert that(...).elseThrow()` for class invariants and postconditions
- `checkIf()` for collecting multiple validation failures

## Prefer Validator API Over Manual if-throw

If requirements.java exposes validator for target type/constraint, prefer it over handwritten
conditionals.

```java
// Good
requireThat(name, "name").isNotBlank();
requireThat(timeout, "timeout").isGreaterThan(Duration.ZERO);
requireThat(path, "path").isRegularFile();

// Avoid
if (name == null || name.isBlank())
  throw new IllegalArgumentException("name must not be blank");
if (timeout == null || timeout.compareTo(Duration.ZERO) <= 0)
  throw new IllegalArgumentException("timeout must be greater than zero");
if (!Files.isRegularFile(path))
  throw new IllegalArgumentException("path must be a regular file");
```

Use manual `if`/`throw` only when at least one is true:
- requirements.java has no validator for needed constraint
- validation depends on multi-value/domain rule not expressible cleanly with validator chaining
- thrown exception type must differ from validator family contract and adapter method is not justified

Even then, prefer combining validator API with narrow manual logic instead of replacing validator API
entirely.

## Constructor Validation

Always validate constructor arguments:

```java
public Config(String name, int timeout)
{
  requireThat(name, "name").isNotBlank();
  requireThat(timeout, "timeout").isPositive();
  this.name = name;
  this.timeout = timeout;
}
```

For `Comparable` domain types, prefer fluent comparison validators over manual conditionals. For example,
treat `Duration` as a `Comparable` and compare it against `Duration.ZERO`:

```java
public Runner(Duration timeout)
{
  requireThat(timeout, "timeout").isNotNull();
  requireThat(timeout, "timeout").isGreaterThan(Duration.ZERO);
  this.timeout = timeout;
}
```

## Method Preconditions

Validate public method parameters:

```java
public void process(String input)
{
  requireThat(input, "input").isNotNull();
  // ...
}
```

Do not rewrite ordinary preconditions as manual guards when validator exists:

```java
// Good
requireThat(input, "input").isNotNull();

// Avoid
if (input == null)
  throw new IllegalArgumentException("input may not be null");
```

## Internal Validation (Asserts)

For internal code (private methods, enum constructors, class invariants), use `assert that()`:

```java
// Enum constructor - only runs when asserts enabled
TerminalType(String jsonKey)
{
  assert that(jsonKey, "jsonKey").isNotBlank().elseThrow();
  this.jsonKey = jsonKey;
}

// Private method validation
private void processInternal(String data)
{
  assert that(data, "data").isNotNull().elseThrow();
  // ...
}
```

This validates during development (with `-ea` flag) but has zero engine cost in production.

## Parameter Naming

**Always provide explicit parameter names** - do not rely on defaults:

```java
// Good
requireThat(value, "value").isNotNull();

// Avoid
requireThat(value).isNotNull();  // Missing name
```

## Chaining Validations

Reuse the same validator across multiple assertions on the same value:

```java
requireThat(count, "count").isNotNegative().isLessThan(100);

// Good - single validator, chained calls
requireThat(result, "result").contains("task1").contains("task2").contains("task3");

// Avoid - redundant validator creation
requireThat(result, "result").contains("task1");
requireThat(result, "result").contains("task2");
requireThat(result, "result").contains("task3");
```

Use property navigation to validate derived values without creating a new validator:

```java
// Good - navigate to length via the validator
requireThat(result, "result").length().isGreaterThan(0);
requireThat(args, "args").length().isEqualTo(0);

// Avoid - extracting the property manually
requireThat(result.length(), "result.length").isGreaterThan(0);
requireThat(args.length, "args.length").isEqualTo(0);
```

**Implicit null checks:** Most validation methods throw `NullPointerException` if the value is null, making an
explicit `isNotNull()` call redundant. Do not chain `isNotNull()` before these methods:

- **String:** `isEmpty`, `isNotEmpty`, `isBlank`, `isNotBlank`, `contains`, `doesNotContain`, `startsWith`,
  `doesNotStartWith`, `endsWith`, `doesNotEndWith`, `matches`, `isTrimmed`, `isStripped`,
  `doesNotContainWhitespace`, `length`
- **Collection:** `isEmpty`, `isNotEmpty`, `contains`, `doesNotContain`, `containsExactly`,
  `doesNotContainExactly`, `containsAny`, `doesNotContainAny`, `containsAll`, `doesNotContainAll`,
  `doesNotContainDuplicates`, `size`
- **Comparable:** `isLessThan`, `isLessThanOrEqualTo`, `isGreaterThan`, `isGreaterThanOrEqualTo`, `isBetween`
- **Object:** `isInstanceOf`, `isNotInstanceOf`

**Methods that do NOT imply null checks** (use explicit `isNotNull()` if needed):
- `isEqualTo`, `isNotEqualTo`

```java
// Good - contains() implies isNotNull()
requireThat(result, "result").contains("my-task");

// Avoid - redundant null check
requireThat(result, "result").isNotNull().contains("my-task");

// Good - isEqualTo does NOT imply isNotNull, so include it when needed
requireThat(result, "result").isNotNull().isEqualTo(expected);
```

Use `and()` for validating multiple values together:

```java
checkIf(start, "start").isNotNegative().
  and(checkIf(end, "end").isGreaterThan(start));
```

## Test Assertions

**Prefer requirements.java over TestNG asserts** in tests:

```java
// Good - clear error messages, consistent API
requireThat(result, "result").isEqualTo(expected);

// Avoid - less informative
assertEquals(result, expected);
```

Use typed validators directly instead of wrapping boolean `Files` checks:

```java
// Good - clear path-specific failure message
requireThat(path, "path").isRegularFile();

// Avoid - collapses path context into a boolean
requireThat(Files.isRegularFile(path), "path").isTrue();
```

Same rule for non-filesystem validation:

```java
// Good
requireThat(count, "count").isNotNegative();

// Avoid
if (count < 0)
  throw new IllegalArgumentException("count must be >= 0");
```

## Error Collection (Web Services)

For collecting multiple failures without throwing:

```java
List<String> failures = checkIf(value, "value").
  isNotNull().
  elseGetFailures();
if (!failures.isEmpty())
{
  return failures;
}
```

## Common Validation Methods

| Method | Description |
|--------|-------------|
| `isNull()` / `isNotNull()` | Null checks |
| `isEmpty()` / `isNotEmpty()` | Collection/string emptiness |
| `isBlank()` / `isNotBlank()` | String whitespace checks |
| `isPositive()` / `isNotNegative()` | Number sign checks |
| `isGreaterThan(v)` / `isLessThan(v)` | Comparisons, including `Comparable` values like `Duration` |
| `isEqualTo(v)` / `isNotEqualTo(v)` | Equality |
| `contains(v)` / `doesNotContain(v)` | Collection membership |
| `matches(regex)` | String pattern matching |
| `length()` | Navigate to string/collection length |
| `size()` | Navigate to collection size |

If needed constraint appears in these validator families or in type-specific validators from
requirements.java packages, use library API first.

## Jackson (JsonNode) Validation

Use the `requirements-jackson` module for validating Jackson `JsonNode` objects. Access its validators through
`DefaultJacksonValidators`:

```java
import io.github.cowwoc.requirements13.jackson.DefaultJacksonValidators;
```

Before manual `JsonNode.path(...).isX()/asX()` validation, search for existing
`DefaultJacksonValidators` usage in repo.

Discovery command:

```bash
rg -n "DefaultJacksonValidators|\\.property\\(|\\.isString\\(|\\.isObject\\(|\\.isArray\\(|\\.isNumber\\(" client
```

Canonical static import:

```java
import static io.github.cowwoc.requirements13.jackson.DefaultJacksonValidators.requireThat;
```

### Property Navigation

Navigate into JSON object properties and validate their types:

```java
// Validate a property exists and is a string
String issueId = requireThat(root, "root").
  property("issue_id").isString().getValue().asString();
requireThat(issueId, "root.issue_id").isNotBlank();

// Validate a property is an object
requireThat(root, "root").
  property("config").isObject();

// Validate a property is an array
requireThat(root, "root").
  property("items").isArray();
```

### Common Jackson Patterns

Prefer these patterns over manual `path(...).isX()/asX()` checks when the validator API can
express the requirement:

```java
// Required object root
requireThat(root, "root").isObject();

// Required property string
String issueId = requireThat(root, "root").
  property("issue_id").isString().getValue().asString();

// Required array
requireThat(root, "root").
  property("items").isArray();

// Required number
double score = requireThat(root, "root").
  property("score").isNumber().getValue().asDouble();

// Nested property extraction
String modelId = requireThat(root, "root").
  property("config").isObject().
  property("model_id").isString().getValue().asString();
```

Avoid manual pattern when validator covers it:

```java
// Avoid
JsonNode config = root.path("config");
if (!config.isObject())
  throw new IllegalArgumentException("config must be an object");
String modelId = config.path("model_id").asString("");
if (modelId.isBlank())
  throw new IllegalArgumentException("config.model_id must be a non-blank string");
```

### Available Type Checks

| Method | Description |
|--------|-------------|
| `isString()` | Validates node is a string |
| `isNumber()` | Validates node is a number |
| `isBoolean()` | Validates node is a boolean |
| `isArray()` | Validates node is an array |
| `isObject()` | Validates node is an object |
| `isMissing()` | Validates node is missing |
| `isValue()` | Validates node is a value (string, number, boolean, null) |
| `isContainer()` | Validates node is a container (array or object) |
| `property(name)` | Navigate to a named property |
| `size()` | Navigate to node size |

## Import

```java
// Java validators (default for most validation)
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.checkIf;

// Jackson validators (for JsonNode validation)
import static io.github.cowwoc.requirements13.jackson.DefaultJacksonValidators.requireThat;
```

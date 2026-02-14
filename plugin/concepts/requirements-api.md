<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Requirements.java API Conventions

## Overview

Use [requirements.java](https://github.com/cowwoc/requirements.java) for all validation needs.
Version: 13.2 (or later)

## Entry Points

Three validators for different contexts:

| Method | Use Case | Exception Type |
|--------|----------|----------------|
| `requireThat(value, name)` | Method preconditions (public API) | `IllegalArgumentException` |
| `that(value, name)` | Class invariants, postconditions, private methods | `AssertionError` |
| `checkIf(value, name)` | Multiple failures, custom error handling | Configurable |

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

## Method Preconditions

Validate public method parameters:

```java
public void process(String input)
{
  requireThat(input, "input").isNotNull();
  // ...
}
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

This validates during development (with `-ea` flag) but has zero runtime cost in production.

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

// Avoid - extracting the property manually
requireThat(result, "result").isNotNull();
requireThat(result.length(), "length").isGreaterThan(0);
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
checkIf(start, "start").isNotNegative()
  .and(checkIf(end, "end").isGreaterThan(start));
```

## Test Assertions

**Prefer requirements.java over TestNG asserts** in tests:

```java
// Good - clear error messages, consistent API
requireThat(result, "result").isEqualTo(expected);

// Avoid - less informative
assertEquals(result, expected);
```

## Error Collection (Web Services)

For collecting multiple failures without throwing:

```java
List<String> failures = checkIf(value, "value")
  .isNotNull()
  .elseGetFailures();
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
| `isGreaterThan(v)` / `isLessThan(v)` | Comparisons |
| `isEqualTo(v)` / `isNotEqualTo(v)` | Equality |
| `contains(v)` / `doesNotContain(v)` | Collection membership |
| `matches(regex)` | String pattern matching |
| `length()` | Navigate to string/collection length |
| `size()` | Navigate to collection size |

## Import

```java
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.checkIf;
```

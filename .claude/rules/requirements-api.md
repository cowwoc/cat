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

Use method chaining for multiple conditions on same value:

```java
requireThat(count, "count").isNotNegative().isLessThan(100);
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

## Import

```java
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.checkIf;
```

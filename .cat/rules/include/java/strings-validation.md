## String Handling

### Text Blocks for Static JSON
Use text blocks for static JSON strings (error messages, usage output) instead of manual escaping:

```java
// Good - text block, readable
System.err.println("""
  {
    "status": "error",
    "message": "Usage: git-squash <base> <last> <msg-file> [branch]"
  }""");

// Good - dynamic values with String.formatted()
System.err.println("""
  {
    "status": "error",
    "message": "%s"
  }""".formatted(e.getMessage().replace("\"", "\\\"")));

// Avoid - manual escaping, hard to read
System.err.println("{\"status\": \"error\", \"message\": " +
  "\"Usage: git-squash <base> <last> <msg-file> [branch]\"}");
```

### Prefer isBlank() Over isEmpty() for External Strings
When checking whether a string from an external source (JSON fields, environment variables, CLI arguments, hook input)
is "empty", use `isBlank()` instead of `isEmpty()`. A whitespace-only string is semantically empty but `isEmpty()`
would miss it.

```java
// Good - catches "", " ", "\t" from external input
if (agentId.isBlank())
  return "";

// Avoid - misses whitespace-only strings from external sources
if (agentId.isEmpty())
  return "";
```

**Use `isEmpty()` only when:**
- You control the string's construction (e.g., `StringBuilder`, string concatenation)
- Whitespace-only is a valid, meaningful value
- Checking collection/map emptiness (`Map.isEmpty()`, `List.isEmpty()`)

### No Null Strings
Use `""` (empty string) instead of `null` for String values - both for return values and parameters:

```java
// Good - return empty string for no value
public String getSessionId()
{
  String value = data.get("session_id");
  if (value == null)
  {
    return "";
  }
  return value;
}

// Good - pass empty string, not null
handler.process("", context);  // No user prompt

// Avoid - returning null
public String getSessionId()
{
  return data.get("session_id");  // May return null
}

// Avoid - passing null
handler.process(null, context);  // Don't do this
```

**Rationale:**
- Eliminates null checks throughout codebase
- Prevents NullPointerException
- Empty string works naturally with `.isEmpty()` checks
- Consistent API - callers never need to handle null

**Validation:** Methods must validate String parameters are not null:
```java
public String process(String input)
{
  requireThat(input, "input").isNotNull();
  // ...
}
```

**Exception:** Use `null` only when the distinction between "not present" and "empty" is semantically important.

## Validation

### Constructor Validation
**Always validate constructor arguments** using requirements.java. This applies to both classes and records.

**Skip `isNotNull()` when the next statement would NPE anyway.** If a parameter is immediately dereferenced (method
call, field access), the null check adds no value — the NPE from dereferencing already surfaces the bug at the same
location. Only use `requireThat(x, "x").isNotNull()` when the parameter is stored without being dereferenced, or when
the dereferencing happens much later (making the NPE harder to trace).

```java
// Good - no explicit null check needed; scope.getProjectPath() would NPE on null
public BlockMainRebase(AgentScope scope)
{
  this.scope = scope;
  this.projectDir = scope.getProjectPath();
}

// Good - explicit null check needed; name is stored without dereferencing
public Config(String name, int timeout)
{
  requireThat(name, "name").isNotBlank();
  requireThat(timeout, "timeout").isPositive();
  this.name = name;
  this.timeout = timeout;
}
```

**Records MUST have compact constructors** with validation when parameters need validation. Do not declare a compact
constructor for records whose constructor does not read or write the record parameters (e.g., boolean-only or
primitive-only records with no constraints).

**MANDATORY checks for records:**
- String parameters: validate `.isNotNull()` (or `.isNotBlank()` if empty strings are invalid)
- Numeric parameters with constraints: validate `.isPositive()`, `.isGreaterThanOrEqualTo(0)`, etc.
- Object parameters that may be null: document this explicitly in the record's Javadoc

```java
// Class constructor
public Config(String name, int timeout)
{
  requireThat(name, "name").isNotBlank();
  requireThat(timeout, "timeout").isPositive();
  this.name = name;
  this.timeout = timeout;
}

// Good - record with compact constructor validation
public record Worktree(String path, String branch, String state)
{
  public Worktree
  {
    requireThat(path, "path").isNotBlank();
    requireThat(branch, "branch").isNotBlank();
    // state may be empty, but not null
    requireThat(state, "state").isNotNull();
  }
}

// Good - record with numeric validation
public record DiffStats(int filesChanged, int insertions, int deletions)
{
  public DiffStats
  {
    requireThat(filesChanged, "filesChanged").isGreaterThanOrEqualTo(0);
    requireThat(insertions, "insertions").isGreaterThanOrEqualTo(0);
    requireThat(deletions, "deletions").isGreaterThanOrEqualTo(0);
  }
}

// Good - Duration validation using Comparable support
public record Lock(String issueId, String session, Duration age)
{
  public Lock
  {
    requireThat(issueId, "issueId").isNotBlank();
    requireThat(session, "session").isNotBlank();
    requireThat(age, "age").isGreaterThanOrEqualTo(Duration.ZERO);
  }
}

// Good - no compact constructor needed (no validation to perform)
private record CheckResult(boolean statusInvoked, boolean hasBoxOutput)
{
}

// Avoid - empty compact constructor that does nothing
private record CheckResult(boolean statusInvoked, boolean hasBoxOutput)
{
  public CheckResult
  {
  }
}
```

### Skip Validation of Constructor Arguments Passed to Superclass

Do not validate constructor arguments that are already validated by a superclass constructor.
The superclass validation is sufficient; redundant checks clutter the code.

**Example - Bad (redundant validation):**
```java
public MySubclass(String name, Path projectPath)
{
  requireThat(name, "name").isNotBlank();           // Redundant - superclass validates this
  requireThat(projectPath, "projectPath").isAbsolute();  // Redundant - superclass validates
  super(name, projectPath);
}
```

**Example - Good (omit redundant checks):**
```java
public MySubclass(String name, Path projectPath)
{
  super(name, projectPath);  // Superclass validates both
}
```

**When to add validation:**
- Argument is used in the subclass before calling `super()` (rare)
- Argument has constraints specific to the subclass (not enforced by superclass)

### Method Preconditions
**Public methods:** Always validate parameters with `requireThat()` - throws `IllegalArgumentException`:

```java
public void process(String input)
{
  requireThat(input, "input").isNotNull();
  // ...
}
```

**Private methods:** Validation is optional. When needed, use `assert that()`:

```java
// Complex private method - validation helps debugging
private void processInternal(String input, int count)
{
  assert that(input, "input").isNotNull().elseThrow();
  assert that(count, "count").isPositive().elseThrow();
  // ...
}

// Simple private method - validation not required
private String formatLine(String content)
{
  return VERTICAL + " " + content;
}
```

**When to validate private methods:**
- Complex logic where invalid input causes subtle bugs
- Parameters with non-obvious constraints (e.g., must be positive)
- Methods called from multiple places within the class

**When validation is unnecessary:**
- Simple helpers that just format or transform data
- Parameters already validated by the calling public method
- Obvious failure modes (e.g., NPE on null dereference)

**Rationale:**
- Public methods are API boundaries - callers get clear exceptions
- Private methods are internal - assertions help debugging but aren't always needed
- Assertions can be disabled in production for performance

### Fail Fast - No Silent Fallbacks
**Throw exceptions for invalid required parameters** - never silently return fallback values:

```java
// Good - throw exception for invalid input
public String getOutput(Path projectRoot)
{
  requireThat(projectRoot, "projectRoot").isNotNull();
  // ... process normally
}

// Avoid - silent fallback hides bugs
public String getOutput(Path projectRoot)
{
  if (projectRoot == null)
    return null;  // Don't do this - caller may not expect null
  // ...
}
```

**Why:** Silent fallbacks mask programming errors. If a required parameter is invalid, the caller has a bug that should
be fixed, not worked around. Throwing an exception immediately surfaces the problem.

**Exception:** Optional parameters may have defaults, but document this clearly in Javadoc.

### Null Return for Errors
**Methods must NOT return null to signal error conditions** - throw a typed exception instead:

```java
// Good - throw exception for operation failure
public String getRawDiff(Path repoRoot) throws IOException
{
  ProcessResult result = runGit(repoRoot, "diff");
  if (result.exitCode() != 0)
    throw new IOException("git diff failed: " + result.stderr());
  return result.stdout();
}

// Avoid - null has multiple meanings; caller cannot distinguish I/O error from "no diff"
public String getRawDiff(Path repoRoot)
{
  ProcessResult result = runGit(repoRoot, "diff");
  if (result.exitCode() != 0)
    return null;  // Don't do this - caller may misinterpret null
  return result.stdout();
}
```

**Why:** When null can mean several different things (I/O error, process failure, size exceeded, etc.), callers cannot
distinguish between them. This leads to silent misinterpretation — the caller handles one meaning while the method
returns null for another reason.

**Acceptable null return:** Returning null is acceptable only when null has exactly ONE well-documented semantic meaning
(e.g., "value not present in map" or "no match found"). Document this meaning in Javadoc with `@return`.

### String Validation - Prefer isNotBlank()
When validating string parameters, prefer `isNotBlank()` over `isNotNull()` unless empty strings are valid:

```java
// Good - rejects null, empty "", and whitespace-only "  "
public String getConfig(String key)
{
  requireThat(key, "key").isNotBlank();
  // ...
}

// Avoid - allows empty string "" which is usually a bug
public String getConfig(String key)
{
  requireThat(key, "key").isNotNull();  // "" passes but is likely wrong
  // ...
}
```

**When to use each:**
- `isNotBlank()` - Most string parameters (names, keys, paths, identifiers)
- `isNotNull()` - Only when empty strings are valid input (user content, messages)

See `.cat/rules/common/requirements-api.md` for full API conventions.

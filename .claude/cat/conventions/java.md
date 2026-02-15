---
stakeholders: [design, architect]
---

# Java Conventions

## Build System

- **Build Tool:** Maven with Maven Wrapper (`./mvnw`)
- **Test Framework:** TestNG (not JUnit)
- **JSON Library:** Jackson 3.x with `JsonMapper`
- **Validation Library:** requirements.java 13.2+

## Code Style

### Braces
Use Allman style (opening brace on its own line):

```java
public class Example
{
  public void method()
  {
    if (condition)
    {
      // code
    }
  }
}
```

### Single-Statement Blocks
Omit braces for if/else/for/while with a single-statement body that fits on one visual line.
Add braces when the body spans multiple visual lines (e.g., string concatenation continuation):

```java
// Good - body is one visual line, no braces
if (value == null)
  return "";
for (int i = 0; i < 10; ++i)
  process(i);
if (branch == null || branch.isEmpty())
  throw new IOException("git branch --show-current returned no output in directory: " + directory);

// Good - body spans multiple visual lines, needs braces
if (!treeDiff.isEmpty())
{
  throw new IOException("Working tree doesn't match original HEAD! " +
    "Rollback: git reset --hard " + backupBranch);
}

// Good - multiple statements need braces
if (value == null)
{
  log("null value");
  return "";
}
```

### Increment/Decrement Operators
Prefer prefix increment/decrement (`++i`, `--i`) over compound assignment:

```java
// Good - prefix increment
for (int i = 0; i < 10; ++i)
++count;

// Avoid - compound assignment for simple increment
for (int i = 0; i < 10; i += 1)
count += 1;
```

### Indentation
- 2 spaces (not tabs)
- Continuation indent: 2 spaces

### Naming
Avoid abbreviated names - use full descriptive names:

```java
// Good - descriptive names
private static final int DESCRIPTION_WIDTH = 20;
private static final int TYPE_WIDTH = 10;

// Avoid - abbreviated names
private static final int COL_DESC = 20;
private static final int COL_TYPE = 10;
```

### Unused Parameters
Do not prefix method parameter names with underscores, even if unused. Do not add `@SuppressWarnings("UnusedVariable")`
for method parameters. Keep the original parameter name as-is:

```java
// Good - keep original name
public void visit(Path file, BasicFileAttributes attrs)
{
  // attrs not used, but keep the name
  Files.delete(file);
}

// Avoid - underscore prefix
public void visit(Path file, BasicFileAttributes _attrs)

// Avoid - suppression annotation
@SuppressWarnings("UnusedVariable")
public void visit(Path file, BasicFileAttributes attrs)
```

**Exception:** Unused catch parameters must use Java's unnamed variable `_` (required by checkstyle):

```java
// Good - unnamed catch variable
catch (IOException _)
{
  return fallbackValue;
}

// Avoid - named but unused catch variable
catch (IOException e)
{
  return fallbackValue;
}
```

### Field Initialization
Prefer inline initialization over constructor initialization when the value is constant:

```java
// Good - inline initialization
private final DisplayUtils display = new DisplayUtils();
private final JsonMapper mapper = JsonMapper.builder().build();

// Avoid - unnecessary constructor initialization
private final DisplayUtils display;

public Handler()
{
  this.display = new DisplayUtils();
}
```

Use constructor initialization only when:
- The field value depends on constructor parameters
- Complex initialization logic requires multiple statements

```java
// Good - depends on constructor parameter
private final Path configPath;

public Handler(Path pluginRoot)
{
  this.configPath = pluginRoot.resolve("config.json");
}
```

### Conditional Expressions
Use if/else statements instead of the ternary operator:

```java
// Good - if/else with empty string default
String command;
if (commandNode != null)
{
  command = commandNode.asString();
}
else
{
  command = "";
}

// Avoid - ternary operator
String command = commandNode != null ? commandNode.asString() : "";
```

### Switch Over Chained If-Else
When comparing the same variable against 3 or more constant values, use a `switch` statement instead of chained
if-else:

```java
// Good - switch for 3+ comparisons on same variable
switch (type)
{
  case "issue-complete" ->
  {
    processIssueComplete();
  }
  case "feedback-applied" ->
  {
    processFeedbackApplied();
  }
  default ->
  {
    System.err.println("Invalid type: " + type);
    System.exit(1);
  }
}

// Avoid - chained if-else on same variable
if (type.equals("issue-complete"))
{
  processIssueComplete();
}
else if (type.equals("feedback-applied"))
{
  processFeedbackApplied();
}
else
{
  System.err.println("Invalid type: " + type);
  System.exit(1);
}
```

**When if-else is still appropriate:**
- 2 or fewer branches (simple if/else)
- Conditions involve different variables or complex expressions
- Conditions are range-based (`x > 10`) rather than equality-based

### Early Returns (No Else After Return)
Do not use `else` after a conditional that always returns or throws:

```java
// Good - no else after return
public String process(String input)
{
  if (input == null)
    return "";
  return input.trim();
}

// Good - no else after throw
public void validate(String value)
{
  if (value == null)
    throw new NullPointerException("value");
  process(value);
}

// Avoid - unnecessary else
public String process(String input)
{
  if (input == null)
    return "";
  else  // Don't use else here
    return input.trim();
}
```

### JsonMapper Usage
- Use `JsonMapper` instead of `ObjectMapper` for JSON parsing
- Obtain the shared instance from `JvmScope.getJsonMapper()` — never call `JsonMapper.builder().build()` directly
- The shared instance is configured with pretty print (`SerializationFeature.INDENT_OUTPUT`)
- In production code, get the mapper from the `JvmScope` passed to your class
- In tests, create a `TestJvmScope` and call `scope.getJsonMapper()`
- In CLI `main()` methods, create a `MainJvmScope` and call `scope.getJsonMapper()`

### Unicode Characters
Use literal characters instead of unicode escapes:

```java
// Good - literal emoji (readable, no constant needed)
String header = "✅ Complete";
String status = "📁 Worktrees";

// Good - named constants for box-drawing (hard to distinguish visually)
String border = DisplayUtils.HORIZONTAL + DisplayUtils.HORIZONTAL;
String line = DisplayUtils.VERTICAL + " content";

// Avoid - unicode escapes (unreadable)
String header = "\u2705 Complete";
String border = "\u2500\u2500";
```

**Emojis and symbols:** Use literal characters directly - no constants needed.

**Box-drawing characters:** Define constants in `DisplayUtils` only to centralize the choice of box style (rounded `╭╮╯╰` vs sharp `┌┐┘└`). Otherwise, use characters inline.

**Comments:** Do not add comments showing the unicode escape sequence - they add no value since the character is already
visible:

```java
// Good - the character speaks for itself
private static final char FILLED_CIRCLE = '●';

// Avoid - redundant comment that doesn't improve readability
private static final char FILLED_CIRCLE = '●';  // \u25CF
```

### StringJoiner for Delimited Strings
Use `StringJoiner` instead of manual `StringBuilder` with delimiter logic:

```java
// Good - StringJoiner handles delimiters automatically
StringJoiner summary = new StringJoiner("|");
for (int i = 0; i < lineCount; ++i)
  summary.add(lines[i].trim());
return summary.toString();

// Avoid - manual delimiter tracking with StringBuilder
StringBuilder summary = new StringBuilder();
for (int i = 0; i < lineCount; ++i)
{
  if (i > 0)
    summary.append('|');
  summary.append(lines[i].trim());
}
return summary.toString();
```

### Multiline Strings
**Favor Java text blocks** (triple-quoted strings) over concatenated strings or strings containing `\n` characters:

```java
// Good - text block (readable, matches actual output)
Files.writeString(path, """
  # Configuration
  @config/settings.yaml
  # Notes
  @config/notes.txt
  """);

// Avoid - escape sequences (hard to read, error-prone)
Files.writeString(path, "# Configuration\n@config/settings.yaml\n# Notes\n@config/notes.txt\n");

// Avoid - concatenation with newlines
Files.writeString(path, "# Configuration\n" +
  "@config/settings.yaml\n" +
  "# Notes\n" +
  "@config/notes.txt\n");
```

Text blocks automatically include a newline at each line break. Use `\` at end of line to suppress unwanted newlines.

### String Comparison (Case-Sensitive)
Use `variable.equals("literal")` for standard comparisons. Only use `Objects.equals()` when the variable may be null:

```java
// Good - variable first, known non-null
if (toolName.equals("Skill"))
{
  // ...
}

// Good - use Objects.equals() only when variable may be null
if (Objects.equals(nullableValue, "expected"))
{
  // ...
}

// Avoid - awkward "Yoda condition" style
if ("Skill".equals(toolName))
{
  // ...
}
```

### String Comparison (Case-Insensitive)
Use `Strings.equalsIgnoreCase()` for null-safe case-insensitive comparison:

```java
import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;

// Good - null-safe, reads naturally
if (equalsIgnoreCase(toolName, "Bash"))
{
  // ...
}

// Avoid - awkward to read, literal must come first for null safety
if ("Bash".equalsIgnoreCase(toolName))
{
  // ...
}
```

## Documentation

### Javadoc Paragraphs
Place `<p>` on its own line with no other text. Do not use `</p>` closing tags. Empty lines in a Javadoc description
must contain `<p>` — never leave a bare `*` line between paragraphs:

```java
// Good - <p> on its own line, no empty lines around it, no closing tag
/**
 * Summary sentence.
 * <p>
 * Additional paragraph with more details about the class
 * or method behavior.
 * <p>
 * Another paragraph explaining edge cases.
 */

// Avoid - <p> inline with text
/**
 * <p>This paragraph has text on the same line as the tag.</p>
 */

// Avoid - empty line before or after <p>
/**
 * Summary.
 *
 * <p>
 *
 * Text here.
 */

// Avoid - closing </p> tag
/**
 * <p>
 * Text here.
 * </p>
 */
```

### Javadoc Requirements
- **All classes and records must have Javadoc** (public and non-public)
- **All methods must have Javadoc** (including interface methods and private methods)
- **All constructors must have Javadoc** (including record compact constructors)
- **All thrown exceptions must be documented with `@throws`** (including interface methods that expect implementations
  to validate parameters)
- Document parameters with `@param`
- Document return values with `@return`
- Do not duplicate constraint info in `@param` that is already in `@throws` (e.g., don't write "must not be null" if
  `@throws NullPointerException` documents it)
- **`@throws` must reference method parameter names** using `{@code paramName}` so readers can trace exception source
- **Parameter identifiers in `@throws` must ALWAYS use `{@code paramName}` syntax**
- **When multiple parameters are listed in `@throws`, use plural grammar** (e.g., "are null", not "is null")

```java
// Good - {@code} annotation and plural grammar
/**
 * @param filePath the path to the file
 * @param encoding the encoding to use
 * @throws NullPointerException if {@code filePath} or {@code encoding} are null
 */

// Good - single parameter with {@code}
/**
 * @param filePath the path to the file
 * @throws NullPointerException if {@code filePath} is null
 */

// Good - references parameter name for container
/**
 * @param args file paths to count tokens for
 * @throws NullPointerException if {@code args} contains a null element
 */

// Bad - missing {@code} annotation
/**
 * @throws NullPointerException if filePath or encoding are null
 */

// Bad - wrong grammar (singular "is" with multiple parameters)
/**
 * @throws NullPointerException if {@code filePath} or {@code encoding} is null
 */

// Bad - unclear where "file path" comes from
/**
 * @param args file paths to count tokens for
 * @throws NullPointerException if any file path is null
 */
```

```java
/**
 * Configuration settings for the application.
 */
public class Config
{
  /**
   * Creates a new configuration.
   *
   * @param name the configuration name
   * @param timeout the timeout in milliseconds
   * @throws NullPointerException if name is null
   * @throws IllegalArgumentException if timeout is not positive
   */
  public Config(String name, int timeout)
  {
    requireThat(name, "name").isNotNull();
    requireThat(timeout, "timeout").isPositive();
    this.name = name;
    this.timeout = timeout;
  }

  /**
   * Processes the input and returns the result.
   *
   * @param input the input string to process
   * @return the processed result
   * @throws IllegalArgumentException if input is null or blank
   * @throws IOException if processing fails
   */
  public String process(String input) throws IOException
  {
    requireThat(input, "input").isNotBlank();
    // ...
  }
}

/**
 * Context object passed to skill handlers.
 *
 * @param userPrompt the user's prompt text
 * @param sessionId the Claude session ID
 */
public record SkillContext(String userPrompt, String sessionId)
{
  /**
   * Creates a new skill context.
   *
   * @throws NullPointerException if any parameter is null
   */
  public SkillContext
  {
    requireThat(userPrompt, "userPrompt").isNotNull();
    requireThat(sessionId, "sessionId").isNotNull();
  }
}
```

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

### Prefer strip() Over trim()
Use `String.strip()` instead of `String.trim()`. Both remove leading/trailing whitespace, but `strip()` is
Unicode-aware (handles all Unicode whitespace characters), while `trim()` only handles ASCII whitespace (`<= U+0020`):

```java
// Good - Unicode-aware whitespace removal
String clean = input.strip();
String prefix = input.stripLeading();
String suffix = input.stripTrailing();

// Avoid - only handles ASCII whitespace
String clean = input.trim();
```

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

See `plugin/concepts/requirements-api.md` for full API conventions.

## Class Design

### Thread Safety Documentation
Only document classes that **are** thread-safe. Classes without thread-safety documentation are assumed to be
thread-unsafe (the default for most classes).

```java
// Good - document when thread-safe (unusual case)
/**
 * Immutable configuration holder.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class Config
{
  // ...
}

// Good - no thread-safety docs needed (assumed unsafe)
/**
 * Processes skill content with variable substitution.
 */
public final class SkillLoader
{
  // ...
}

// Avoid - documenting the default (thread-unsafe)
/**
 * Processes skill content.
 * <p>
 * <b>Thread Safety:</b> This class is NOT thread-safe.
 */
public final class SkillLoader
{
  // ...
}
```

### Service Access via Pouch Scopes (No Dependency Injection)
Do not use dependency injection frameworks (Spring, Guice, Dagger, etc.). Use [pouch](https://github.com/cowwoc/pouch)
scope-based ServiceLocators for inversion of control. Scopes are explicit objects passed through constructors that
provide access to shared services — no reflection, no annotations, no proxies, no config files. The dependency graph is
verified at compile-time.

**Scopes represent contexts where values remain constant.** `JvmScope` spans the application lifetime. Child scopes
(e.g., `RequestScope`) inherit from parent scopes, matching resource lifetimes. This prevents impossible configurations
like an HTTP request outliving its database connection.

```java
// Good - pouch scope passed through constructor
public final class GetRenderDiffOutput
{
  private final JvmScope scope;

  public GetRenderDiffOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  public String getOutput()
  {
    JsonMapper jsonMapper = scope.getJsonMapper();
    DisplayUtils display = scope.getDisplayUtils();
    // ...
  }
}

// Avoid - passing individual scope components
public final class GetRenderDiffOutput
{
  private final JsonMapper jsonMapper;
  private final DisplayUtils display;

  public GetRenderDiffOutput(JsonMapper jsonMapper, DisplayUtils display)
  {
    this.jsonMapper = jsonMapper;
    this.display = display;
  }
}
```

**Pass the scope, not its components.** If a constructor or method takes one or more parameters derived from a
ServiceLocator scope like `JvmScope`, pass the scope directly instead. The class should pull whatever it needs from
the scope internally. This keeps constructors stable when new dependencies are added and avoids proliferating scope
accessors through call chains.

**Scope implementations:**
- `MainJvmScope` — production use (in `main()` methods), reads environment configuration
- `TestJvmScope` — test use, accepts injectable paths: `new TestJvmScope(tempDir, tempDir)`

**Why pouch over DI frameworks:**
- No magic — explicit constructor wiring, fully debuggable code flow
- Compile-time dependency graph verification (no runtime surprises)
- Scope hierarchy enforces resource lifetime constraints
- Each test instantiates its own scope hierarchy, executing as if in a separate JVM
- `JvmScope` lifecycle management (via `try-with-resources`) handles cleanup

### main() in Business Logic Classes
Classes with testable business logic may include a `main()` method for CLI invocation. Do not extract `main()` into a
separate command class - this adds a file with no value. The pattern of constructor (used by tests) + `main()` (used by
`hook.sh` for CLI invocation) is standard and acceptable:

```java
// Good - business logic class with CLI entry point
public final class GetRenderDiffOutput
{
  public GetRenderDiffOutput(JvmScope scope) { ... }

  public String getOutput() { ... }  // Testable business logic

  public static void main(String[] args)  // CLI entry point via hook.sh
  {
    try (JvmScope scope = new MainJvmScope())
    {
      String output = new GetRenderDiffOutput(scope).getOutput();
      if (output != null)
        System.out.print(output);
    }
  }
}

// Avoid - separate class that just delegates to the real class
public final class RenderDiffCommand  // Don't create this
{
  public static void main(String[] args)
  {
    // Trivial delegation adds no value
    new GetRenderDiffOutput(new MainJvmScope()).getOutput();
  }
}
```

## Warnings Suppression

### @SuppressWarnings("unchecked")
**Avoid suppressing unchecked warnings if you can fix the underlying problem.**
If suppression is unavoidable, do it on a per-statement basis (not class or method level).

**Approach:**
1. **Fix the underlying problem** - Use proper generics, TypeReference, etc.
2. **Per-statement suppression** - Only if fix is not possible, suppress on the specific statement

```java
// Best: Fix with TypeReference (Jackson example)
private static final TypeReference<Map<String, Object>> MAP_TYPE =
  new TypeReference<>()
  {
  };

Map<String, Object> config = mapper.readValue(content, MAP_TYPE);

// Acceptable: Per-statement suppression when fix not possible
@SuppressWarnings("unchecked")
Map<String, Object> rawMap = (Map<String, Object>) untypedResult;

// Avoid: Class or method-level suppression
@SuppressWarnings("unchecked")  // Don't do this
private Map<String, Object> loadConfig()
{
  // Multiple lines where warning is hidden
}
```

## Exception Handling

### AssertionError vs IllegalStateException
Throw `AssertionError` when an internal assumption is violated — a condition that should never occur and is not
preventable by the caller. These represent programming errors or environment invariants.

Throw `IllegalStateException` only when the caller attempts to invoke a method that requires a certain object state, that
state is queryable, and the caller could have checked before calling.

```java
// Good - AssertionError for environment invariant (caller cannot prevent or query)
String envFile = System.getenv("CLAUDE_ENV_FILE");
if (envFile == null || envFile.isEmpty())
  throw new AssertionError("CLAUDE_ENV_FILE is not set");

// Good - IllegalStateException for preventable state violation (caller can query)
public void stop()
{
  if (!isRunning())
    throw new IllegalStateException("Cannot stop: server is not running");
  // ...
}
```

### Specific Exceptions
**Throw the most specific exception type possible** - never throw `Exception`:

```java
// Good
throw new IllegalArgumentException("Invalid input");
throw new IOException("File not found");

// Avoid
throw new Exception("Something went wrong");
```

### Test Exceptions
Tests should also throw specific exceptions:

```java
// Good
@Test
public void testInvalidInput() throws IOException
{
  // ...
}

// Avoid
@Test
public void testInvalidInput() throws Exception
{
  // ...
}
```

### Wrapping Checked Exceptions
Use `WrappedCheckedException.wrap()` from pouch when checked exceptions must be wrapped:

```java
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

// Good - use WrappedCheckedException.wrap()
try
{
  return new DisplayUtils();
}
catch (IOException e)
{
  throw WrappedCheckedException.wrap(e);
}

// Avoid - RuntimeException loses the checked exception type
try
{
  return new DisplayUtils();
}
catch (IOException e)
{
  throw new RuntimeException(e);  // Don't use this
}

// Avoid - using specific unchecked exception types
try
{
  return new DisplayUtils();
}
catch (IOException e)
{
  throw new UncheckedIOException(e);  // Don't use this
}
```

**Why:** `WrappedCheckedException` provides a consistent API for wrapping any checked exception
type, preserving the original exception as the cause. This avoids proliferation of different
unchecked wrapper types (`UncheckedIOException`, custom wrappers, etc.).

## Testing

### No Catch-and-Fail for Unexpected Exceptions
TestNG's `@Test` already fails tests that throw unexpected exceptions. Do not catch exception types just to manually
fail — let them propagate naturally:

```java
// Good - only catch the EXPECTED exception; unexpected ones propagate
@Test
public void executeRejectsNullInput() throws IOException
{
  try
  {
    cmd.execute(null, "value");
    requireThat(false, "execute").isEqualTo(true);
  }
  catch (NullPointerException e)
  {
    requireThat(e.getMessage(), "message").contains("input");
  }
  // IOException propagates naturally → TestNG fails the test
}

// Avoid - redundant catch-and-fail
@Test
public void executeRejectsNullInput()
{
  try
  {
    cmd.execute(null, "value");
    requireThat(false, "execute").isEqualTo(true);
  }
  catch (NullPointerException e)
  {
    requireThat(e.getMessage(), "message").contains("input");
  }
  catch (IOException _)
  {
    requireThat(false, "shouldThrowNullPointerException").isEqualTo(true); // Redundant
  }
}
```

**For "accepts" tests** (verifying no validation exception is thrown):

```java
// Good - catch only the acceptable operational exception
@Test
public void executeAcceptsEmptyBranch() throws IOException
{
  cmd.execute("HEAD~1", "HEAD", messageFile.toString(), "");
  // If NPE or IAE is thrown, TestNG fails the test automatically
  // IOException from git operations is acceptable → declared in throws
}
```

### Test Documentation
**All test methods must have Javadoc** describing:
- What the test verifies
- The expected behavior

```java
/**
 * Verifies that empty input returns an empty JSON object.
 */
@Test
public void emptyInput_returnsEmptyJson() throws IOException
{
  // ...
}
```

### Parallel Execution
Tests run in parallel. Test classes must not contain any shared state:

1. **No class fields** - use local variables only
2. **No @BeforeMethod/@AfterMethod/@BeforeClass/@AfterClass** - initialize in each test method
3. **Use try-with-resources** for all resources (files, streams, temp directories)
4. **No shared mutable state** - each test must be fully self-contained
5. **No TestBase classes** - each test method must inline its own setup. This boilerplate is intentional and preferred
   over shared helpers or inheritance.
6. **Use `TestJvmScope`, not `MainJvmScope`** - tests must never use `MainJvmScope` because it reads environment
   variables that may not be set in test contexts. Use `TestJvmScope(tempDir, tempDir)` with injectable paths instead.
7. **Never use scope-provided objects after closing the scope** - objects returned by `JvmScope` (e.g., `JsonMapper`,
   `DisplayUtils`) must not be used after the scope is closed. Keep the scope open for the entire duration of the test.
   Do not create helper methods like `getTestMapper()` that open a scope, extract an object, and close the scope.
8. **No `System.setErr()`/`System.setOut()`** - these mutate JVM-wide shared state and are not thread-safe. Instead:
   - **Design for testability**: Create methods that accept arbitrary streams as parameters, then have `main()` delegate
     to these methods passing `System.in`/`System.out`/`System.err`. Tests pass their own streams.
   - **Assert observable side effects**: When stream injection isn't practical, verify behavior through other means (e.g.,
     file still exists after failed deletion) rather than capturing stderr.

```java
// Good - self-contained test with TestJvmScope
@Test
public void testProcess() throws IOException
{
  Path tempDir = Files.createTempDirectory("test-");
  try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
  {
    JsonMapper mapper = scope.getJsonMapper();
    var result = process(scope, input);
    requireThat(result, "result").isEqualTo("expected");
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(tempDir);
  }
}

// Avoid - class fields, setup methods, or shared base classes
private InputStream sharedInput;  // Don't do this

@BeforeMethod
public void setup()  // Don't do this
{
  sharedInput = ...;
}
```

### Assertions
**Prefer requirements.java over TestNG assertions**:

```java
// Good - clear error messages
requireThat(result, "result").isEqualTo(expected);
requireThat(list, "list").contains(item);

// Avoid - less informative
assertEquals(result, expected);
assertTrue(list.contains(item));
```

### Validate Return Value Contents, Not Just Non-Null
Tests must validate the **contents** of return values, not merely that they are non-null. A non-null check proves the method
returned something but says nothing about correctness.

```java
// ❌ WRONG: Only checks non-null - passes even if result contains wrong data
SessionStartHandler.Result result = new CheckUpdateAvailable(scope).handle(input);
requireThat(result, "result").isNotNull();

// ✅ CORRECT: Validates the actual content of the result
SessionStartHandler.Result result = new CheckUpdateAvailable(scope).handle(input);
requireThat(result.output(), "output").contains("expected text");
requireThat(result.continueProcessing(), "continueProcessing").isTrue();
```

**Why:** A test that only asserts `isNotNull()` will pass even when the method returns completely wrong data. The test
provides false confidence - it "passes" but validates nothing meaningful.

**When `isNotNull()` alone is acceptable:**
- The test explicitly documents that it only verifies the method doesn't throw (smoke test)
- The value is an opaque handle where contents are not meaningful to the caller

### Test Requirements, Not Implementation
**Tests should validate business requirements with controlled inputs, not assert system configuration or implementation details.**

Tests that assert environment-dependent values (terminal emoji widths, system fonts, screen resolution) provide no business value - they only verify that your development machine has specific configuration.

```java
// ❌ WRONG: Asserts developer's terminal configuration
@Test
public void singleEmojiHasWidthTwo() throws IOException
{
  DisplayUtils display = new DisplayUtils();  // Auto-detects terminal
  int width = display.displayWidth("🐱");
  requireThat(width, "width").isEqualTo(2);  // Fails on different terminals
}

// ✅ CORRECT: Tests width calculation logic with controlled input
@Test
public void displayWidthCalculatesFromConfiguration() throws IOException
{
  Path tempConfig = createTempConfig(Map.of("🐱", 2, "✅", 1));
  DisplayUtils display = new DisplayUtils(tempConfig, TerminalType.KITTY);

  requireThat(display.displayWidth("🐱"), "catWidth").isEqualTo(2);
  requireThat(display.displayWidth("✅"), "checkWidth").isEqualTo(1);
  requireThat(display.displayWidth("🐱 cat"), "combined").isEqualTo(6);
}
```

**What to test:**
- **Behavior:** Does the class correctly load configuration and apply it?
- **Logic:** Does calculation handle edge cases (empty string, multiple emojis, mixed content)?
- **Requirements:** Does it meet the stated business requirements?

**What NOT to test:**
- **System state:** What emoji width does my terminal happen to have?
- **Implementation details:** What specific value is in the default config file?
- **Environment:** What does auto-detection return on my machine?

**Guideline:** If your test would fail when run on a different machine with different configuration (but the code still works correctly), you're testing implementation details, not requirements.

### Testability Over Convenience
If code cannot be tested in a thread-safe way (e.g., it reads from `System.in` or writes to `System.out`), ask the
user's permission to update the API to make it testable. For example, add a method overload that accepts an
`InputStream` parameter instead of reading from `System.in` directly. The `main()` method can delegate to the testable
overload.

### No Thread.sleep() in Tests
Avoid using `Thread.sleep()` in tests. There should always be a way to trigger the desired event/condition without
sleeping:

```java
// Good - inject Clock to control time
@Test
public void rateLimitExpires() throws IOException
{
  Instant baseTime = Instant.parse("2025-01-01T00:00:00Z");
  Clock clock1 = Clock.fixed(baseTime, ZoneOffset.UTC);
  Clock clock2 = Clock.fixed(baseTime.plusSeconds(2), ZoneOffset.UTC);

  DetectGivingUp handler1 = new DetectGivingUp(clock1);
  handler1.check(prompt, sessionId);

  DetectGivingUp handler2 = new DetectGivingUp(clock2);
  handler2.check(prompt, sessionId);  // Time has "passed"
}

// Avoid - sleeping slows tests and introduces flakiness
@Test
public void rateLimitExpires() throws InterruptedException
{
  handler.check(prompt, sessionId);
  Thread.sleep(1100);  // Don't do this
  handler.check(prompt, sessionId);
}
```

**Why:**
- `Thread.sleep()` makes tests slow and flaky
- Time-dependent code should accept a `Clock` parameter for testability
- Fixed clocks make tests deterministic and fast

## Build Commands

```bash
cd engine

./build.sh        # Build JAR (mvn package)
./build.sh test   # Run TestNG tests (mvn test)
./build.sh clean  # Clean artifacts (mvn clean)
mvn verify        # Full build validation (compile + test + checkstyle + PMD)
```

**Verification:** Always run `mvn verify` to verify the build. Do not use `mvn test` or `mvn compile` — these skip
checkstyle and PMD checks, allowing lint violations to go undetected.

## Module Structure

All modules must define `module-info.java`. Tests reside in a separate module from implementation.

### Naming Convention
| Implementation | Test Module | Test Package |
|----------------|-------------|--------------|
| `io.github.cowwoc.cat.hooks` | `io.github.cowwoc.cat.hooks.test` | `io.github.cowwoc.cat.hooks.test` |
| `com.example.foo` | `com.example.foo.test` | `com.example.foo.test` |

### Module Exports for Testing
The implementation module must export internal packages to the test module:

```java
// module-info.java for io.github.cowwoc.cat.hooks
module io.github.cowwoc.cat.hooks
{
  requires tools.jackson.databind;

  // Public API
  exports io.github.cowwoc.cat.hooks;

  // Internal packages exported only to test module
  exports io.github.cowwoc.cat.hooks.internal to io.github.cowwoc.cat.hooks.test;
}
```

```java
// module-info.java for io.github.cowwoc.cat.hooks.test
module io.github.cowwoc.cat.hooks.test
{
  requires io.github.cowwoc.cat.hooks;
  requires org.testng;
  requires io.github.cowwoc.requirements13.java;
}
```

### Access Implications
- Methods tested directly must be `public` (visible across modules)
- Package-private methods can only be tested indirectly through public API
- Use `exports ... to ...` for targeted exports to test module only

## Project Structure

```
client/                      # Maven project root
├── pom.xml
├── build.sh
├── mvnw
├── src/main/java/           # Implementation module (io.github.cowwoc.cat.hooks)
│   └── io/github/cowwoc/cat/hooks/
│       ├── module-info.java
│       ├── Config.java
│       ├── HookInput.java
│       ├── HookOutput.java
│       └── Get*Output.java
└── src/test/java/           # Test module (io.github.cowwoc.cat.hooks.test)
    └── io/github/cowwoc/cat/hooks/test/
        └── module-info.java
```

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
Omit braces for if/else/for/while with a single statement:

```java
// Good - single statement, no braces
if (value == null)
  return "";
for (int i = 0; i < 10; ++i)
  process(i);
while (hasMore)
  processNext();

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
- Create non-static instances: `JsonMapper.builder().build()`
- Create mapper where needed, don't use static class-level instances

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

### String Concatenation with Newlines
Split consecutive newlines across lines for readability:

```java
// Good - split newlines for readability
"First line\n" +
"\n" +
"Second line after blank"

// Avoid - multiple escape sequences per line
"First line\n\n" +
"Second line after blank"
```

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

**Records MUST have compact constructors** with validation - never leave the body empty:

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

// Avoid - empty record body (no validation)
public record Worktree(String path, String branch, String state)
{
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
Tests run in parallel. Design for thread safety:

1. **Use try-with-resources** for all resources
2. **Avoid class fields** - use local variables
3. **Avoid @BeforeClass/@AfterClass** - initialize in test method
4. **No shared mutable state**

```java
// Good - self-contained test
@Test
public void testProcess() throws IOException
{
  try (var input = new ByteArrayInputStream("test".getBytes()))
  {
    var result = process(input);
    requireThat(result, "result").isEqualTo("expected");
  }
}

// Avoid - shared state
private InputStream sharedInput;  // Don't do this

@BeforeClass
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

## Build Commands

```bash
cd plugin/hooks/java

./build.sh        # Build JAR (mvn package)
./build.sh test   # Run TestNG tests (mvn test)
./build.sh clean  # Clean artifacts (mvn clean)
mvn verify        # Full build validation (compile + test + checkstyle + PMD)
```

**Verification:** Always run `mvn verify` before committing Java changes to catch checkstyle/PMD violations.

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
plugin/hooks/
├── java/                    # Maven project root
│   ├── pom.xml
│   ├── build.sh
│   ├── mvnw
│   └── src/test/java/       # Test module (io.github.cowwoc.cat.hooks.test)
│       └── io/github/cowwoc/cat/hooks/test/
│           └── module-info.java
└── src/                     # Implementation module (io.github.cowwoc.cat.hooks)
    └── io/github/cowwoc/cat/hooks/
        ├── module-info.java
        ├── Config.java
        ├── HookInput.java
        ├── HookOutput.java
        └── Get*Output.java
```

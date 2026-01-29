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

### JsonMapper Usage
- Use `JsonMapper` instead of `ObjectMapper` for JSON parsing
- Create non-static instances: `JsonMapper.builder().build()`
- Create mapper where needed, don't use static class-level instances

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
- **All thrown exceptions must be documented with `@throws`** (including interface methods that expect implementations to validate parameters)
- Document parameters with `@param`
- Document return values with `@return`
- Do not duplicate constraint info in `@param` that is already in `@throws` (e.g., don't write "must not be null" if `@throws NullPointerException` documents it)

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
**Always validate constructor arguments** using requirements.java. This applies to both classes and records:

```java
// Class constructor
public Config(String name, int timeout)
{
  requireThat(name, "name").isNotBlank();
  requireThat(timeout, "timeout").isPositive();
  this.name = name;
  this.timeout = timeout;
}

// Record compact constructor
public record SkillContext(String userPrompt, String sessionId, String projectRoot,
                           String pluginRoot, JsonNode hookData)
{
  public SkillContext
  {
    requireThat(userPrompt, "userPrompt").isNotNull();
    requireThat(sessionId, "sessionId").isNotNull();
    requireThat(projectRoot, "projectRoot").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(hookData, "hookData").isNotNull();
  }
}
```

### Method Preconditions
Validate public method parameters:

```java
public void process(String input)
{
  requireThat(input, "input").isNotNull();
  // ...
}
```

See `.claude/rules/requirements-api.md` for full API conventions.

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
```

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

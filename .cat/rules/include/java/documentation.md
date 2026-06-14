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

### Imports for Javadoc References
Always add an `import` for classes referenced via `{@link}` or `{@linkplain}` in Javadoc, even if the class is not used
in executable code. Use the simple class name in the Javadoc tag:

```java
// Good - import added, simple name in {@link}
import io.github.cowwoc.cat.engine.hook.skills.DisplayUtils;

/**
 * This file is consumed by {@link DisplayUtils}.
 */

// Avoid - fully qualified name in {@link}
/**
 * This file is consumed by {@link io.github.cowwoc.cat.engine.hook.skills.DisplayUtils}.
 */
```

### Javadoc Requirements
- **All classes and records must have Javadoc** (public and non-public)
- **All methods must have Javadoc** (including interface methods and private methods)
- **Private helper methods must still have Javadoc**; inline comments are not a substitute for method-level contract
  documentation
- **`@Override` methods do not need Javadoc** unless the override changes the contract or adds important implementation
  details beyond what the parent documents
- **All constructors must have Javadoc** (including record compact constructors)
- **All thrown exceptions must be documented with `@throws`** (including interface methods that expect implementations
  to validate parameters)
- Document parameters with `@param`
- Document return values with `@return`
- **Delegating overloads must document how they differ from the full overload**. If one overload forwards to another
  with defaults, state that explicitly in the Javadoc (for example, "Equivalent to
  {@code method(name, 18)}")
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

// Good - delegating overload explains the defaulted argument
/**
 * Equivalent to {@code method(name, 18)}.
 *
 * @param name the person's name
 */
public void method(String name)
{
  method(name, 18);
}

/**
 * @param name the person's name
 * @param age the person's age
 */
public void method(String name, int age)
{
}

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
 * @param sessionId the engine session ID
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

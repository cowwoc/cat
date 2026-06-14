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

## Environment Variable Access

Engine environment variables (e.g., `CAT_SESSION_ID`, `CAT_PLUGIN_ROOT`) must be read through the correct API
depending on the execution context. Never call `System.getenv()` directly outside approved engine boundary classes.

| Context | Correct API |
|---------|-------------|
| Session CLI commands (`main()` methods) — have `CAT_SESSION_ID` | `scope.getSessionId()` via `CliTool` |
| Infrastructure CLI commands (`main()` methods) — invoked outside a session (e.g., by skill preprocessor) | `scope.getPluginRoot()` etc. via `AgentPluginScope` |
| Hook handlers | `HookInput.getSessionId()` |
| Skill directive variable substitution | `System.getenv(name)` (whitelisted; see below) |

**Why:** Hook handlers receive session-specific values from the `HookInput` JSON payload, not from environment
variables. Reading environment variables in hook handlers bypasses this contract. Session CLI commands use
`MainCliTool` (a `CliTool` implementation) which derives session and plugin values at startup. Infrastructure CLI
commands use the narrowest engine-specific `AgentPluginScope` implementation that exposes the values they need.

```java
// Good - session CLI main() method reads session ID via scope
public static void main(String[] args)
{
  try (CliTool scope = new MainCliTool())
  {
    String sessionId = scope.getSessionId();
    // ...
  }
}

// Good - infrastructure CLI main() method uses an AgentPluginScope (no session vars required)
public static void main(String[] args)
{
  try (AgentPluginScope scope = new MainCodexTool())
  {
    Path pluginRoot = scope.getPluginRoot();
    // ...
  }
}

// Bad - CLI main() method reads session ID via System.getenv()
public static void main(String[] args)
{
  String sessionId = System.getenv("CAT_SESSION_ID");  // Don't do this
  // ...
}

// Good - hook handler reads session ID from HookInput JSON
public Result handle(HookInput input)
{
  String sessionId = input.getSessionId();
  // ...
}

// Bad - hook handler bypasses HookInput to read environment directly
public Result handle(HookInput input)
{
  String sessionId = System.getenv("CAT_SESSION_ID");  // Don't do this
  // ...
}
```

The env-access enforcement test scans Java source files and fails the build if `System.getenv(` appears outside
approved boundary classes.

Approved boundary classes each have a specific reason for direct env var access:
- Session engine scope implementations read session env vars at startup and store them as fields.
- Infrastructure scope implementations read infrastructure path vars for tools that run without session context.
- Hook scope implementations read infrastructure path vars and hook JSON from stdin.
- Skill variable substitution expands env var references in skill directive templates.
- Terminal detection reads standard terminal env vars such as `TERM` and `TERM_PROGRAM`.

**Scope implementations:**
- `MainCliTool` — production use for shared CLI tools, derives CAT values from the active engine harness and
  environment
- `MainClaudeTool` / `MainCodexTool` — production engine-specific tool scopes
- `MainClaudeHook` / `MainCodexHook` — production engine-specific hook scopes
- `TestClaudeTool`, `TestClaudeHook`, and other `Test*` scopes — test use with injectable paths and deterministic
  values

## Exception Handling

### AssertionError vs IllegalStateException
Throw `AssertionError` when an internal assumption is violated — a condition that should never occur and is not
preventable by the caller. These represent programming errors or environment invariants.

Throw `IllegalStateException` only when the caller attempts to invoke a method that requires a certain object state, that
state is queryable, and the caller could have checked before calling.

```java
// Good - AssertionError for environment invariant (caller cannot prevent or query)
String sessionId = scope.getSessionId();  // throws AssertionError if env var not set

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

### Catching Error Types
The only `Error` subclass you may catch is `AssertionError`. Never catch generic `Error`:

```java
// Good - catch AssertionError specifically if needed
catch (RuntimeException | AssertionError e)
{
  log.error("Unexpected error", e);
  throw e;
}

// Bad - catches all Errors including OutOfMemoryError, StackOverflowError, etc.
catch (RuntimeException | Error e)
{
  log.error("Unexpected error", e);
  throw e;
}
```

Other `Error` types (`OutOfMemoryError`, `StackOverflowError`, `NoClassDefFoundError`, etc.) indicate
unrecoverable JVM failures and must not be caught.

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

## Logging

### Logger Instantiation

Loggers must always be **non-static** instance fields. Use `getClass()` for non-final classes
(captures the engine subclass name); use a concrete class literal for `final` classes (no
subclasses possible):

```java
// Correct — non-final class uses getClass()
public class MyClass
{
  private final Logger log = LoggerFactory.getLogger(getClass());
  ...
}

// Correct — final class uses concrete literal
public final class MyClass
{
  private final Logger log = LoggerFactory.getLogger(MyClass.class);
  ...
}
```

If a logger is needed inside a `static` method (e.g., `main()` or a static utility), create it
locally inside that method on demand. Never declare a `static Logger` class field.

```java
// Correct — on-demand in static context
public static void main(String[] args)
{
  try
  {
    ...
  }
  catch (RuntimeException | AssertionError e)
  {
    Logger log = LoggerFactory.getLogger(MyClass.class);
    log.error("Unexpected error", e);
  }
}

// Avoid — static logger field
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
```

**Why:** Non-static loggers use `getClass()` which correctly captures the engine subclass name in
inheritance hierarchies. Static loggers always report the declaring class, hiding which subclass
actually ran the code. Creating a logger on demand in static contexts is equivalent — `LoggerFactory`
caches instances internally so there is no meaningful overhead.

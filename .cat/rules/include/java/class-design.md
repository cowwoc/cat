## Class Design

### Interface vs Abstract Class
Prefer an abstract superclass over `default` or `static` methods on an interface. Interfaces define contracts (abstract
methods); implementation logic — even derived convenience methods — belongs in an abstract class.

Abstract classes must omit methods that cannot be properly implemented at their level. Do not satisfy an interface
contract by returning fallback values such as `""`, `null`, empty collections, or no-op results merely because a
subclass is expected to override the method. Leave the method abstract and require each concrete subclass to provide
the correct implementation for its engine context.

```java
// Good - interface defines contract, abstract class provides derived methods
public interface AgentScope extends AutoCloseable
{
  Path getConfigPath();
  Path getProjectPath();
}

public abstract class AbstractAgentScope implements AgentScope
{
  public Path getProjectCatDir()
  {
    return getConfigPath().resolve("projects").resolve(encodeProjectPath(getProjectPath().toString())).
      resolve("cat");
  }

  public String encodeProjectPath(String projectPath)
  {
    return projectPath.replace("/", "-").replace(".", "-");
  }
}

// Avoid - implementation logic in the interface
public interface AgentScope extends AutoCloseable
{
  Path getConfigPath();

  default Path getProjectCatDir()  // implementation in interface
  {
    return getConfigPath().resolve("...");
  }

  static String encodeProjectPath(String path)  // utility in interface
  {
    return path.replace("/", "-");
  }
}
```

Scope classes should expose the values they own directly through the scope API. Do not create `*Config` records or
config getters that callers have to unpack before constructing the next scope. If multiple concrete scope
implementations need the same derived value, derive it once in the abstract scope implementation and let concrete
classes invoke the superclass constructor with only their engine-specific inputs.

**When `default` methods ARE acceptable:**
- Backward-compatible additions to a widely-implemented interface
- Methods that truly have only one correct implementation and no state dependency

### Thread Safety Documentation
Only document classes that **are** thread-safe. Classes without thread-safety documentation are assumed to be
thread-unsafe (the default for most classes).

The Thread Safety notice must state only the observable guarantee — not the mechanism that achieves it. Do not mention
synchronization primitives, lock strategies, internal data structures, or race-condition details. The guarantee is
the contract; the mechanism is the implementation.

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
public final class LoaderUtility
{
  // ...
}

// Avoid - documenting the default (thread-unsafe)
/**
 * Processes skill content.
 * <p>
 * <b>Thread Safety:</b> This class is NOT thread-safe.
 */
public final class LoaderUtility
{
  // ...
}

// Avoid - reveals implementation details
/**
 * Glob matching utility.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe. Individual cache operations ({@code get},
 * {@code put}) are individually synchronized. Under concurrent access two threads may occasionally
 * compile the same pattern, but both produce equivalent immutable instances.
 */
public final class GlobMatcher
{
  // ...
}
```

### Minimum Method Visibility
Always use the most restrictive visibility that still allows the method to function correctly. Work down from the
least-restrictive level needed:

| Visibility | Use when |
|------------|----------|
| `public` | Part of the class's public API; called from outside the package |
| `protected` | Must be accessible to subclasses or other classes in the same package |
| package-private (no modifier) | Used only within the same package; no subclass involvement |
| `private` | Used only within the same class |

**Final classes:** `protected` is meaningless on a `final` class — the class cannot be subclassed, so no override or
inheritance-based access is possible. Convert every `protected` method in a `final` class to `private` unless another
class in the same package calls it (in which case package-private is sufficient).

```java
// Good - final class uses private instead of protected
public final class MainCliTool implements CliTool
{
  private String getEnvVar(String name)
  {
    return System.getenv(name);
  }
}

// Avoid - protected in a final class (no subclass can ever override this)
public final class MainCliTool implements CliTool
{
  protected String getEnvVar(String name)
  {
    return System.getenv(name);
  }
}
```

**Non-final classes:** `protected` is appropriate only when a subclass actually overrides or calls the method.
If no subclass uses it, prefer package-private (no modifier) or `private`.

```java
// Good - package-private; only used within the same package by non-subclass code
String buildCacheKey()
{
  return "prefix:" + id;
}

// Avoid - protected when no subclass calls or overrides it
protected String buildCacheKey()
{
  return "prefix:" + id;
}
```

**Public API surface:** Restrict `public` to methods that form the class's intended contract. Helper and utility
methods used only within the package should be package-private or private even if their class is public.

**Remove unused methods:** After reducing visibility, delete any method that is now unreachable — i.e., `private`
methods not called within the class, or package-private methods not called anywhere in the package. Dead code adds
noise and misleads future readers into thinking a method has callers.

### No Delegation-Only Methods

Do not declare methods that do nothing except delegate to the superclass implementation with the same method name.
These add no value and clutter the codebase.

When inheriting from a superclass that implements a required interface method, rely on the inherited implementation
unless your subclass needs to override it with custom behavior.

**Example - Bad (delegation-only method):**
```java
@Override
public Path getEngineConfigPath()
{
  return super.getEngineConfigPath();
}
```

**Example - Good (omit the method and use inherited implementation):**
```java
// Don't declare this method - the superclass implementation is sufficient
```

**When to override:**
- Your subclass needs different behavior (custom logic, computed values)
- You need to add visibility (e.g., a `protected` method in superclass that should be `public`)
- You need to add documentation specific to the subclass

### Service Access via Pouch Scopes (No Dependency Injection)
Do not use dependency injection frameworks (Spring, Guice, Dagger, etc.). Use [pouch](https://github.com/cowwoc/pouch)
scope-based ServiceLocators for inversion of control. Scopes are explicit objects passed through constructors that
provide access to shared services — no reflection, no annotations, no proxies, no config files. The dependency graph is
verified at compile-time.

**Scopes represent contexts where values remain constant.** `AgentScope` spans the application lifetime. Child scopes
(e.g., `RequestScope`) inherit from parent scopes, matching resource lifetimes. This prevents impossible configurations
like an HTTP request outliving its database connection.

```java
// Good - pouch scope passed through constructor
public final class GetDiffOutput
{
  private final CliTool scope;

  public GetDiffOutput(CliTool scope)
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
public final class GetDiffOutput
{
  private final JsonMapper jsonMapper;
  private final DisplayUtils display;

  public GetDiffOutput(JsonMapper jsonMapper, DisplayUtils display)
  {
    this.jsonMapper = jsonMapper;
    this.display = display;
  }
}
```

**Pass the scope, not its components.** If a constructor or method takes one or more parameters derived from a
ServiceLocator scope like `AgentScope` or `CliTool`, pass the scope directly instead. The class should pull whatever it needs from
the scope internally. This keeps constructors stable when new dependencies are added and avoids proliferating scope
accessors through call chains.

**Scope implementations:**
- `MainCliTool` — production use for shared CLI tools, derives CAT scope values from the active engine harness and
  exposes those values directly through `CliTool`
- `MainClaudeTool` / `MainCodexTool` — production engine-specific tool scopes
- `MainClaudeHook` / `MainCodexHook` — production engine-specific hook scopes
- Test scopes such as `TestClaudeTool`, `TestClaudeHook`, and `TestCodexHook` — accept injectable paths and
  deterministic defaults

When a engine has paired production and test scopes, such as `MainCodexHook` and `TestCodexHook`, introduce an
`Abstract*` scope for their shared behavior. Delegate as much common code as possible into the abstract scope, then keep
`Main*` and `Test*` subclasses focused on the parts that genuinely differ: production entrypoints read engine
environment, stdin, or installed filesystem locations, while test scopes accept injectable values and deterministic
defaults. Do not duplicate shared scope behavior between the production and test classes.

**Why pouch over DI frameworks:**
- No magic — explicit constructor wiring, fully debuggable code flow
- Compile-time dependency graph verification (no engine surprises)
- Scope hierarchy enforces resource lifetime constraints
- Each test instantiates its own scope hierarchy, executing as if in a separate JVM
- `AgentScope` lifecycle management (via `try-with-resources`) handles cleanup

### Builders vs Mandatory Constructors

Do not introduce a builder solely to reduce constructor argument count when all properties are mandatory. A direct
constructor fails at compile time when callers omit a required value; a builder usually converts that mistake into a
engine validation failure. Prefer compile-time failures for required dependencies and state.

Use a builder only when it provides a real type-safety or ergonomics benefit beyond argument count, such as optional
properties, many independent defaults, staged builders that preserve compile-time mandatory-field checks, or a public
API where named setters are materially clearer for callers.

### main() in Business Logic Classes
Classes with testable business logic may include a `main()` method for CLI invocation. Do not extract `main()` into a
separate command class - this adds a file with no value. The pattern of constructor (used by tests) + `main()` (used by
`hook.sh` for CLI invocation) is standard and acceptable:

```java
// Good - business logic class with CLI entry point
public final class GetDiffOutput
{
  public GetDiffOutput(CliTool scope) { ... }

  public String getOutput() { ... }  // Testable business logic

  public static void main(String[] args)  // CLI entry point via hook.sh
  {
    try (CliTool scope = new MainCliTool())
    {
      String output = new GetDiffOutput(scope).getOutput();
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
    new GetDiffOutput(new MainCliTool()).getOutput();
  }
}
```

### No System.exit(0) at End of main() Catch Blocks
Do not call `System.exit(0)` at the end of a `main()` catch block when returning from the block achieves the same
result. The JVM exits with code 0 when the main thread returns normally, making a trailing `System.exit(0)` redundant.

```java
// Good - catch block ends naturally, JVM exits 0
catch (IOException e)
{
  System.out.println(errorJson);
}

// Avoid - System.exit(0) is redundant when it's the last statement
catch (IOException e)
{
  System.out.println(errorJson);
  System.exit(0);  // Unnecessary
}
```

### Prefer Interface Types for Variable Declarations

Always use the most general interface type that the code actually needs when declaring variables, fields, parameters,
and return types. Use a concrete type only when the code requires a method or field not present on any interface.

This applies to all variable declaration contexts:

```java
// Good - interface type used for local variable
List<String> names = new ArrayList<>();
Map<String, Integer> index = new HashMap<>();

// Avoid - concrete type leaks implementation detail
ArrayList<String> names = new ArrayList<>();
HashMap<String, Integer> index = new HashMap<>();

// Good - interface type used for try-with-resources
try (CliTool scope = new MainCliTool())
{
  ...
}

// Avoid - concrete class on left side of try-with-resources
try (MainCliTool scope = new MainCliTool())
{
  ...
}

// Good - interface type used for field declarations
private final List<String> items = new ArrayList<>();
private final Map<String, Config> configs = new LinkedHashMap<>();

// Good - interface type used for method parameters
public void process(List<String> items) { ... }

// Good - interface type used for method return types
public List<String> getItems() { return Collections.unmodifiableList(items); }
```

**When to use a concrete type:** Only when the variable is used with a method or field that is not declared on any
interface — for example, `LinkedList` when `addFirst()` is needed, or `ArrayDeque` when `peekFirst()` is needed.

### Single-Scope Error Handling in main()

When `main()` handles expected and unexpected errors, use a single scope with a nested try-catch inside it. Do NOT create
additional scope instances in catch blocks:

```java
// Good - one scope, nested try-catch inside
public static void main(String[] args)
{
  try (CliTool scope = new MainCliTool())
  {
    try
    {
      new MyClass(scope).run(args, System.out);
    }
    catch (IllegalArgumentException | IOException e)
    {
      System.out.println(new HookOutput(scope).block(
        Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(MyClass.class);
      log.error("Unexpected error", e);
      System.out.println(new HookOutput(scope).block(
        Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}

// Avoid - multiple scope instances
public static void main(String[] args)
{
  try (CliTool scope = new MainCliTool())
  {
    new MyClass(scope).run(args, System.out);
  }
  catch (RuntimeException | AssertionError e)
  {
    try (CliTool errorScope = new MainCliTool())   // Don't do this
    {
      System.out.println(new HookOutput(errorScope).block(...));
    }
  }
}
```

**Why:** Creating a new scope in the catch block reads environment variables again, adds latency, and opens two resources
sequentially when one would suffice. The single outer scope is still open during the catch block, so it can be used
directly for error reporting.

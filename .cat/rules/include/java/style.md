## Code Style

### Javadoc

Private methods must have Javadoc. Private visibility is not an exemption from documenting purpose, parameters,
return values, and thrown exceptions. This includes small helper methods and one-call-site private methods; do not
omit Javadoc just because a method seems locally obvious.

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

### Imports
Use `import` statements instead of fully-qualified class names (FQNs) in code:

```java
// Good - import at top, short name in code
import java.util.stream.Stream;
import java.util.Comparator;

try (Stream<Path> walk = Files.walk(dir))
{
  walk.sorted(Comparator.reverseOrder()).forEach(Files::delete);
}

// Avoid - FQN inline
try (java.util.stream.Stream<Path> walk = Files.walk(dir))
{
  walk.sorted(java.util.Comparator.reverseOrder()).forEach(Files::delete);
}
```

**Exception:** FQNs are acceptable in Javadoc `{@link}` / `{@code}` tags when the type is not already imported.

**Nested Classes:** Import nested classes directly to reduce verbosity in instanceof checks and other code:

```java
// Good - import nested class directly
import io.github.cowwoc.cat.engine.hook.util.IssueDiscovery.DiscoveryResult.ExistingWorktree;

if (discoveryResult instanceof ExistingWorktree existingWorktree)
{
  // ...
}

// Avoid - long fully-qualified name in code
if (discoveryResult instanceof IssueDiscovery.DiscoveryResult.ExistingWorktree existingWorktree)
{
  // ...
}
```

Java allows importing nested classes at any nesting depth, and using direct imports keeps code readable while maintaining clarity about the type origin.

### Time Values
Prefer `Duration` over `long` when a value represents elapsed time, a timeout, a retry delay, or any other
meaningful time quantity. Convert to primitive units only at API boundaries that require them, and remove unit
suffixes from variable names once the value is typed as a `Duration`:

```java
// Good - time stays typed until the API boundary
Duration timeout = Duration.ofSeconds(30);
Duration waitPoll = Duration.ofMillis(50);
process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

// Avoid - raw long hides units in normal control flow
long timeoutMillis = 30_000;
process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
```

Use primitive `long` time values only when the API contract is inherently unit-specific or performance-critical.
When you must drop to primitive units, keep the unit explicit in the variable name (for example, `timeoutMillis`
or `deadlineNanos`).

Prefer `Instant` over `long` when a value represents an absolute wall-clock timestamp:

```java
// Good - wall-clock time stays typed
Instant startedAt = Instant.now();

// Avoid - raw epoch millis hides semantics in normal control flow
long startedAtMillis = System.currentTimeMillis();
```

Do not use `Instant` for monotonic elapsed-time math based on `System.nanoTime()`. Monotonic deadlines and stopwatch
values are not wall-clock timestamps, so unit-explicit `long` values such as `startTimeNanos` or `deadlineNanos`
remain acceptable there.

### Naming

Avoid abbreviations in variable names. Use full, descriptive names:

```java
// Good
int index = diskContent.indexOf(oldString);
String message = "Edit rejected";

// Avoid
int idx = diskContent.indexOf(oldString);
String msg = "Edit rejected";
```

### Static Imports for Common Constants

Use static imports for frequently used constants to reduce verbosity:

```java
// Good - static import
import static java.nio.charset.StandardCharsets.UTF_8;

byte[] bytes = str.getBytes(UTF_8);

// Avoid - qualified constant
byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
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

### Method Chaining
Prefer method chaining when calls naturally compose and intermediate variables are unnecessary:

```java
// Good - fluent construction without an unnecessary builder variable
Process process = new ProcessBuilder("git", "status", "--porcelain").
  directory(directory.toFile()).
  redirectErrorStream(true).
  start();

// Avoid - intermediate variable used only for setup chaining
ProcessBuilder processBuilder = new ProcessBuilder("git", "status", "--porcelain");
processBuilder.directory(directory.toFile());
processBuilder.redirectErrorStream(true);
Process process = processBuilder.start();
```

This convention is not specific to `ProcessBuilder`; apply it broadly when chaining improves clarity
without harming readability. Keep intermediate variables when later conditional mutation is required
(for example, optional environment or working-directory setup).

### Emptiness Checks
Prefer `!charSequence.isEmpty()` to `charSequence.length() > 0` for `CharSequence` emptiness checks:

```java
// Good
if (!branch.isEmpty())
  process(branch);

// Avoid
if (branch.length() > 0)
  process(branch);
```

### Increment/Decrement Operators
Prefer prefix increment/decrement (`++i`, `--i`) over compound assignment.
When incrementing/decrementing by one, write `++i` / `--i` (or `i++` / `i--` when needed by expression semantics),
not `i += 1` / `i -= 1`:

```java
// Good - prefix increment
for (int i = 0; i < 10; ++i)
++count;

// Avoid - compound assignment for simple increment
for (int i = 0; i < 10; i += 1)
count += 1;

// Avoid - compound assignment for simple decrement
for (int i = 10; i > 0; i -= 1)
process(i);
```

### Iteration
Avoid `stream.forEach()` and `iterable.forEach()`. Use native for-loops instead:

```java
// Good - native for-loop
for (String item : items)
  process(item);

// Avoid - forEach
items.forEach(item -> process(item));
items.stream().forEach(item -> process(item));
```

**Why:** For-loops support `break`, `continue`, checked exceptions, and mutable local variables. `forEach()` obscures
control flow and offers no advantage for simple iteration.

**Exception:** Terminal stream operations like `collect()`, `map()`, `filter()` are fine — the rule applies specifically
to `forEach()` as a terminal operation replacing a loop.

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

### Inline Trivial Helper Methods

Do not extract a private helper method that only validates an argument and returns one nested method call. Inline
that call at the use site instead. For example, prefer `hookPayload.toString()` over a `jsonFor(hookPayload)` helper
whose body only null-checks `hookPayload` and returns `hookPayload.toString()`, because the method call already fails
fast on null.

### Method And Class Size

Try to keep methods at 80 lines or fewer when practical. If a method grows beyond that, prefer splitting it into
smaller helpers with clearer responsibilities when doing so improves readability rather than adding indirection.

Try to keep classes at 2000 lines or fewer when practical. If a class grows beyond that, prefer splitting it into
smaller collaborating classes when the extracted pieces have coherent responsibilities and do not just move code around
mechanically.

### Design Principles

Prefer designs that follow SOLID principles, with one project-specific exception:

- **Single Responsibility Principle:** Prefer methods and classes with one clear responsibility and one clear reason
  to change.
- **Open/Closed Principle:** Prefer designs that can be extended through composition, focused collaborators, or
  localized changes instead of repeatedly editing large unrelated code paths.
- **Liskov Substitution Principle:** When using inheritance or implementing a contract, make sure subtypes preserve
  the parent contract's behavior rather than introducing surprising preconditions, side effects, or weaker guarantees.
- **Interface Segregation Principle:** Prefer small, focused interfaces and APIs so callers depend only on the
  operations they actually need.
- **Dependency Inversion Principle:** Do not introduce abstractions up-front just to satisfy DIP in theory. Create
  abstractions on demand, when there is a real need for multiple implementations, substitution, or test seams that
  justify the extra indirection.

### StringBuilder Empty Check
Use `!sb.isEmpty()` instead of `sb.length() > 0` to check whether a `StringBuilder` is empty:

```java
// Good - isEmpty()
if (!current.isEmpty())
  tokens.add(current.toString());

// Avoid - length comparison
if (current.length() > 0)
  tokens.add(current.toString());
```

### StringJoiner for Delimited Sequences
Use `StringJoiner` to build delimited strings (with commas, newlines, or other separators) instead of manual
`StringBuilder` loops with delimiter injection:

```java
// Good - StringJoiner with newline delimiter
StringJoiner output = new StringJoiner("\n");
try (BufferedReader reader = new BufferedReader(new FileReader(file)))
{
  String line;
  while ((line = reader.readLine()) != null)
    output.add(line);
}
return output.toString();

// Avoid - manual StringBuilder with conditional delimiter
StringBuilder output = new StringBuilder();
try (BufferedReader reader = new BufferedReader(new FileReader(file)))
{
  String line;
  while ((line = reader.readLine()) != null)
  {
    if (!output.isEmpty())
      output.append('\n');
    output.append(line);
  }
}
return output.toString();
```

**Why:** `StringJoiner` is purpose-built for this pattern. It eliminates the error-prone conditional check (`if (!output.isEmpty())`) and is more idiomatic than manual delimiter injection.

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

### Optional Unwrapping
When an `Optional` is used only once — immediately in a single method chain like `map().orElse()` or `isPresent()` — inline the constructor call rather than storing it in a named variable:

```java
// Good — no intermediate variable needed
Path branchDir = WorktreeContext.forSession(
    scope.getCatWorkPath(), projectPath, scope.getJsonMapper(), sessionId).
  map(WorktreeContext::absoluteWorktreePath).
  orElse(projectPath);

// Avoid — intermediate variable adds no information
Optional<WorktreeContext> context = WorktreeContext.forSession(
  scope.getCatWorkPath(), projectPath, scope.getJsonMapper(), sessionId);
Path branchDir = context.map(WorktreeContext::absoluteWorktreePath).orElse(projectPath);
```

When extracting a value from an `Optional` with a fallback, use `map().orElse()` instead of an `if (isPresent()) / get()`
block:

```java
// Good - concise, idiomatic
return context.map(WorktreeContext::absoluteWorktreePath).orElse(projectPath);

// Avoid - verbose, error-prone
if (context.isPresent())
  return context.get().absoluteWorktreePath();
return projectPath;
```

Branch on an `Optional` to select a **value**, not a code path. When both branches perform the same operation on
different values, extract the `Optional` to a single variable and call the operation once:

```java
// Good — extract the path, call the operation once
Path branchDir = context.map(WorktreeContext::absoluteWorktreePath).orElse(projectPath);
try
{
  return GitCommands.getCurrentBranch(branchDir.toString());
}
catch (IOException _)
{
  return null;
}

// Avoid — same operation duplicated in both branches
if (context.isEmpty())
{
  try
  {
    return GitCommands.getCurrentBranch(projectPath.toString());
  }
  catch (IOException _)
  {
    return null;
  }
}
try
{
  return GitCommands.getCurrentBranch(context.get().absoluteWorktreePath().toString());
}
catch (IOException _)
{
  return null;
}
```

When only the **empty** case has work to do, return early on `isPresent()` rather than wrapping the work in an
`isEmpty()` block. This avoids a gratuitous level of nesting:

```java
// Good — early return eliminates nesting
if (context.isPresent())
  return null;
String target = extractCheckoutTarget(command);
if (!isCheckoutFlag(target))
  return Result.block("Blocked: Cannot checkout '%s' in main worktree.".formatted(target));
return null;

// Avoid — work buried inside isEmpty() block
if (context.isEmpty())
{
  String target = extractCheckoutTarget(command);
  if (!isCheckoutFlag(target))
    return Result.block("Blocked: Cannot checkout '%s' in main worktree.".formatted(target));
}
return null;
```

When the present-case requires multiple statements (a block body), extract the value using `orElse(null)` and test
for null rather than using a two-step `isEmpty()` check followed by `.get()`:

```java
// Good — single null check, no isEmpty/get split
WorktreeContext context = WorktreeContext.forSession(...).orElse(null);
if (context == null)
  return Result.allow();
// use context directly
context.absoluteWorktreePath();

// Avoid — isEmpty check followed by separate .get() call
Optional<WorktreeContext> contextOptional = WorktreeContext.forSession(...);
if (contextOptional.isEmpty())
  return Result.allow();
WorktreeContext context = contextOptional.get();
```

### Null-First Conditionals
When a conditional handles both the null and non-null case, test the null case first. This applies to explicit
if/else and to early-return patterns:

```java
// Good - null case first (explicit else)
if (argumentsToken == null)
  expandedArgs = null;
else
  expandedArgs = expandDirectiveString(argumentsToken.strip(), skillName);

// Good - null case first (implicit else via early return)
if (envValue == null)
  return "${" + varName + "}";
return envValue;

// Avoid - non-null case first
if (argumentsToken != null)
  expandedArgs = expandDirectiveString(argumentsToken.strip(), skillName);
else
  expandedArgs = null;

// Avoid - non-null case first (implicit else)
if (envValue != null)
  return envValue;
return "${" + varName + "}";
```

### Positive Conditions First
In if/else blocks, handle the positive (non-negated) condition first when both branches are present:

```java
// Good - positive condition first
if (targetBranch.isEmpty())
  System.out.println(completedIssue + " merged.");
else
  System.out.println(completedIssue + " merged to " + targetBranch + ".");

// Avoid - negated condition first
if (!targetBranch.isEmpty())
  System.out.println(completedIssue + " merged to " + targetBranch + ".");
else
  System.out.println(completedIssue + " merged.");
```

**Why:** Positive conditions are easier to read and reason about.

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

### Regex: Minimal Escaping
Only escape characters that require escaping in the given context. In particular, `]` outside a character class `[...]`
is a literal and does not need a backslash:

```java
// Good - ] is not inside a character class, no escape needed
Pattern.compile("\\$ARGUMENTS\\[(\\d+)]");

// Avoid - unnecessary escape of ]
Pattern.compile("\\$ARGUMENTS\\[(\\d+)\\]");
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
Use `variable.equals("literal")` for standard comparisons. If the variable may be null, use
`Objects.equals(variable, "literal")` instead. Never use Yoda-style `"literal".equals(variable)`:

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
import static io.github.cowwoc.cat.engine.hook.Strings.equalsIgnoreCase;

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

### Inline Comments: Explain WHY, Not WHAT
Inline comments must explain **why** the code does what it does, not restate the operation. The code
itself shows WHAT happens; the comment must add the reasoning, context, or semantic distinction that
is not obvious from reading the code.

```java
// Good - explains WHY content is empty and what semantic distinction drives the branch
// "content" is only present for Write operations. For Edit operations, the tool provides
// "old_string" and "new_string" instead. Reconstruct the expected post-edit file content by
// applying the replacement to the on-disk file so we can validate the result.

// Avoid - restates the operation without explaining the semantic reason
// Edit tool call: reconstruct post-edit content by applying new_string to on-disk file
```

**Self-check before writing an inline comment:**
1. Does the comment explain something the code cannot express?
2. Would a reader understand the *reason* for this branch/guard/operation?
3. If removed, would a future reader be confused about *why* this path exists?

If the answer to all three is "no", the comment is noise. If the answer to #2 is "no", rewrite the
comment to explain the reasoning.

### Classified Branch Bodies
When a branch body needs a short classification label (a word or phrase naming what kind of case this
is), put it in a braced block with the comment on the first line and the statement(s) below it. Do
**not** place the comment as a trailing inline comment on the statement line.

```java
// Good - classification comment on its own line inside a braced block
if (isSingleQuoted || !rawValue.contains("$"))
{
  // pure literal
  assignments.put(varName, rawValue);
}
else if (rawValue.contains("$("))
{
  // skip — command substitution
}

// Avoid - trailing inline comment makes the label easy to miss and hard to scan
if (isSingleQuoted || !rawValue.contains("$"))
  assignments.put(varName, rawValue);   // pure literal
else if (rawValue.contains("$("))
  /* skip — command substitution */;
```

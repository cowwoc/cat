# Plan

## Goal

Fix NullPointerException in StatuslineCommand by moving closed field from all 4 concrete subclasses to
AbstractJvmScope — eliminates initialization order bug and removes duplicate boilerplate.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: StatuslineCommand no longer throws NullPointerException when invoked with valid JSON input
- [ ] Regression test added: Test that exercises the statusline initialization path and verifies no NPE occurs
- [ ] No new issues: All existing tests continue to pass
- [ ] E2E verification: Run statusline-command binary with sample JSON input and verify it produces formatted output
      without errors
- [ ] The `closed` field is declared in AbstractJvmScope, not in any concrete subclass
- [ ] None of the 4 concrete subclasses contain their own `closed` field, `isClosed()` method, or `close()` method
- [ ] AbstractJvmScope's `closed` field is initialized at field declaration so it is available before any subclass
      constructor runs

## Research Findings

### Root Cause

StatuslineCommand's constructor calls `scope.getJsonMapper()`, which calls `ensureOpen()` on AbstractJvmScope, which
calls `isClosed()`. The `isClosed()` method is defined in the concrete subclass (e.g., MainClaudeStatusline), but
during Java object construction the abstract superclass constructor runs before the concrete subclass fields are
initialized. The `closed` AtomicBoolean field in the concrete subclass is still `null` at this point, causing a
NullPointerException.

### Inheritance Hierarchy

```
JvmScope (interface)
    -> AbstractJvmScope (abstract) — has ensureOpen() that calls isClosed(), but does NOT define isClosed()/close()
        -> AbstractClaudeScope (abstract)
            -> AbstractClaudeHook -> MainClaudeHook [has closed field]
            -> AbstractClaudeTool -> MainClaudeTool [has closed field]
            -> AbstractClaudeStatusline -> MainClaudeStatusline [has closed field]
        -> MainJvmScope [has closed field]
```

### Affected Concrete Classes (4 total)

Each of these classes has identical boilerplate that must be removed:

1. `MainJvmScope` — `private final AtomicBoolean closed = new AtomicBoolean();` + `isClosed()` + `close()`
2. `MainClaudeHook` — same pattern
3. `MainClaudeTool` — same pattern
4. `MainClaudeStatusline` — same pattern

### Intermediate Abstract Classes to Verify

These 3 abstract classes must be checked for any `closed`-related code (none expected, but verify):

- `AbstractClaudeHook`
- `AbstractClaudeTool`
- `AbstractClaudeStatusline`

### Fix Approach

Move the `closed` field, `isClosed()`, and `close()` to AbstractJvmScope. Initialize `closed` at field declaration
(`new AtomicBoolean()`) so it is available before any subclass constructor runs. Remove all duplicate definitions from
the 4 concrete subclasses.

### Files (all under `client/src/main/java/io/github/cowwoc/cat/hooks/`)

- `AbstractJvmScope.java` — add `closed` field, `isClosed()`, `close()`
- `MainJvmScope.java` — remove `closed` field, `isClosed()`, `close()`
- `MainClaudeHook.java` — remove `closed` field, `isClosed()`, `close()`
- `MainClaudeTool.java` — remove `closed` field, `isClosed()`, `close()`
- `MainClaudeStatusline.java` — remove `closed` field, `isClosed()`, `close()`
- `AbstractClaudeHook.java` — verify no closed-related code
- `AbstractClaudeTool.java` — verify no closed-related code
- `AbstractClaudeStatusline.java` — verify no closed-related code
- `util/StatuslineCommand.java` — the class that triggers the NPE (no changes needed, just test target)

## Sub-Agent Waves

### Wave 1

All files are interdependent (shared inheritance hierarchy), so all work fits in a single wave.

**Step 1: Read AbstractJvmScope.java**

Read `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java` to understand its current fields,
methods, and imports. Note the exact location of `ensureOpen()` and confirm it calls `isClosed()`.

**Step 2: Add closed field and methods to AbstractJvmScope**

In `AbstractJvmScope.java`:

- Add `import java.util.concurrent.atomic.AtomicBoolean;` if not already present
- Add field: `private final AtomicBoolean closed = new AtomicBoolean();`
- Add method:
  ```java
  @Override
  public boolean isClosed()
  {
      return closed.get();
  }
  ```
- Add method:
  ```java
  @Override
  public void close()
  {
      closed.set(true);
  }
  ```

Place the field near the top of the class (with other fields). Place `isClosed()` and `close()` methods in a logical
location (near `ensureOpen()` or at the end of the class, following existing code style).

**Step 3: Remove closed field and methods from MainJvmScope**

Read `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java`. Remove:

- The `private final AtomicBoolean closed = new AtomicBoolean();` field
- The `@Override public boolean isClosed()` method
- The `@Override public void close()` method
- The `import java.util.concurrent.atomic.AtomicBoolean;` import if no longer used

**Step 4: Remove closed field and methods from MainClaudeHook**

Read `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeHook.java`. Remove the same 3 elements as Step 3
(closed field, isClosed(), close()), plus the AtomicBoolean import if unused.

**Step 5: Remove closed field and methods from MainClaudeTool**

Read `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeTool.java`. Remove the same 3 elements as Step 3
(closed field, isClosed(), close()), plus the AtomicBoolean import if unused.

**Step 6: Remove closed field and methods from MainClaudeStatusline**

Read `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeStatusline.java`. Remove the same 3 elements as
Step 3 (closed field, isClosed(), close()), plus the AtomicBoolean import if unused.

**Step 7: Verify intermediate abstract classes**

Read each of these files and confirm they contain no `closed` field, `isClosed()`, or `close()` methods:

- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeHook.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeTool.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeStatusline.java`

If any of them do contain closed-related code, remove it.

**Step 8: Write regression test**

Create a test class that verifies the initialization order bug is fixed. The test must:

- Construct a scope object (e.g., `MainJvmScope` or `MainClaudeStatusline`)
- Call `getJsonMapper()` on it (this is the call path that triggered the NPE)
- Assert no exception is thrown
- Assert the returned JsonMapper is not null
- Follow TestNG conventions and include the license header

Place the test in the appropriate test directory alongside existing tests. Check
`client/src/test/java/io/github/cowwoc/cat/hooks/` for existing test patterns.

**Step 9: Run all tests**

```bash
mvn -f client/pom.xml test
```

All tests must pass with exit code 0. If any test fails, diagnose and fix before proceeding.

**Step 10: Commit**

Commit all changes (source modifications + new test) with type `bugfix:` since this is a bug fix in `client/` code.
Include STATE.md update (status: closed, progress: 100%) in the same commit per project conventions.

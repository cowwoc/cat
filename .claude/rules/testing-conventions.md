---
paths: ["client/**"]
---
## Testing

- Java: TestNG for unit tests
- Bash: Bats (Bash Automated Testing System)
- Minimum coverage: 80% for business logic
- All edge cases must have tests

### No Redundant Builds

**Do not re-run a build or test suite if no source files changed since the last successful run.** A passing
build remains valid until files are modified. Re-running an unchanged build wastes time and adds noise to the
session.

**When a re-run IS required:**
- Any tracked source file was added, modified, or deleted since the last successful build
- The build tool configuration changed (e.g., `pom.xml`, `build.gradle`, `Makefile`)
- An external dependency changed (e.g., a dependency was upgraded)

**When a re-run is NOT required:**
- Only documentation, comments, or non-source files changed (e.g., `.md`, `.txt`)
- Only planning artifacts changed (`.cat/issues/`, `CLAUDE.md`)
- The last build passed and nothing has been committed or staged since

### Test Isolation

Tests must be **self-contained**, **thread-safe**, and must **never impact the production environment**:

1. **No operations against the real repository** — tests must never run git commands against the project's working
   directory, even read-only queries. Use isolated temporary repos. For validation-only tests where execution fails
   before any external operation, this is acceptable since no command actually runs.
2. **No production environment side effects** — tests must not modify files, git state, processes, or configuration
   outside their temporary directories.
3. **Concurrent safety** — multiple test runs, parallel tests, and concurrent Claude instances must not interfere with
   each other or with the host environment. Avoid JVM-global or process-global mutation (e.g., environment variables,
   system properties, stdout/stderr redirection, current working directory).
4. **Deterministic** — test results must not depend on host machine configuration, repository state, or timing. Use
   controlled inputs and injectable dependencies (e.g., `Clock` for time, temp dirs for paths).

**Why:** A leaky test that runs `git reset --soft HEAD~1 && git commit` against the real repo will silently corrupt the
working branch on every build. This is catastrophic when builds automatically or in parallel.

### Test Product Behavior, Not Tool Behavior

**Tests must validate YOUR CODE's behavior, not 3rd-party tool behavior.**

A test that verifies file/directory absence after invoking an external tool (git, curl, etc.) is testing the TOOL's
behavior, not your code's behavior. Your code's responsibility is to invoke the tool correctly with the right arguments,
not to verify the tool produces the expected side effects.

**Anti-pattern:**
```java
// ❌ WRONG: Testing git's behavior, not your code's behavior
@Test
public void newWorktreeExcludesClaude() {
    Path worktreePath = createWorktree();
    assertFalse(Files.exists(worktreePath.resolve(".claude")),
        ".claude directory should not exist in worktree");
}
```

**Problem:** This test validates that git sparse-checkout correctly excludes the directory from the filesystem. But
that's git's responsibility, not your code's. If git changes its behavior or if your code has a typo in the
sparse-checkout arguments, this test doesn't catch the bug.

**Correct patterns:**

**Option A: Unit test — verify invocation arguments**
```java
// ✅ CORRECT: Test that your code invokes git with correct arguments
@Test
public void setupSparseCheckoutInvokesGitCorrectly() {
    GitCommandRecorder recorder = new GitCommandRecorder();
    WorkPrepare.setupSparseCheckout(worktreePath, recorder);
    
    recorder.assertInvoked("sparse-checkout", "set", "--no-cone", "/*", "!/.claude");
}
```

**Option B: Integration test — verify business outcome**
```java
// ✅ CORRECT: Test the business requirement (no duplicate rules loaded)
@Test
public void worktreesDoNotLoadDuplicateRules() {
    Path worktreePath = createWorktree();
    RuleLoader loader = new RuleLoader(worktreePath);
    
    List<Rule> rules = loader.loadRules();
    assertNoDuplicates(rules, "Worktree should not load duplicate rules from .claude/");
}
```

**When filesystem checks ARE acceptable:**
- Testing your own code's file I/O operations (e.g., "does writeConfig() create the file?")
- Verifying cleanup logic (e.g., "does cleanupTempFiles() remove all temp files?")

**When filesystem checks are NOT acceptable:**
- After invoking external tools (git, curl, npm, etc.) — test the invocation, not the side effects
- Testing tool configuration state (sparse-checkout list, gitignore rules) — verify your code sets it correctly

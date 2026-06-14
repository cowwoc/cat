---
paths: ["client/**"]
---
## Testing

### Test Fixture Files

Pre-recorded data files used to make tests deterministic belong in a `fixtures/` subdirectory of the test
directory, not in the test directory root.

**Correct:**
```
plugin/tests/agents/instruction-grader-agent/
  fixtures/
    negative_1_runner.json
    req_grader_schema_runner.json
  negative_1.md
  req_grader_schema.md
```

**Incorrect:**
```
plugin/tests/agents/instruction-grader-agent/
  negative_1_runner.json        ← should be under fixtures/
  negative_1.md
```

---

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
- Only planning artifacts or engine-loaded project instruction files changed
- The last build passed and nothing has been committed or staged since

### Test Isolation

Tests must be **self-contained**, **thread-safe**, and must **never impact the production environment**:

1. **No operations against the real repository** — tests must never run git commands against the project's working
   directory, even read-only queries. Use isolated temporary repos. For validation-only tests where execution fails
   before any external operation, this is acceptable since no command actually runs.
2. **No production environment side effects** — tests must not modify files, git state, processes, or configuration
   outside their temporary directories.
3. **Concurrent safety** — multiple test runs, parallel tests, and concurrent engine instances must not interfere
   with each other or with the host environment. Avoid JVM-global or process-global mutation (e.g., environment
   variables, system properties, stdout/stderr redirection, current working directory).
4. **Deterministic** — test results must not depend on host machine configuration, repository state, or timing. Use
   controlled inputs and injectable dependencies (e.g., `Clock` for time, temp dirs for paths).
5. **Test-specific scopes only** — test code must interact with `Test*` scope implementations, not `Main*` production
   scopes. Production scopes read host environment variables, stdin, or engine-specific filesystem locations and are
   reserved for production entrypoints. For example, use `TestCodexTool`, `TestCodexHook`, `TestClaudeTool`, or
   `TestClaudeHook` instead of `MainCodexTool`, `MainCodexHook`, `MainClaudeTool`, or `MainClaudeHook`.

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
public void newWorktreeExcludesProjectRules() {
    Path worktreePath = createWorktree();
    assertFalse(Files.exists(worktreePath.resolve(".project-rules")),
        "projectRules");
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
    
    recorder.assertInvoked("sparse-checkout", "set", "--no-cone", "/*", "!/.project-rules");
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
    assertNoDuplicates(rules, "rules");
}
```

**When filesystem checks ARE acceptable:**
- Testing your own code's file I/O operations (e.g., "does writeConfig() create the file?")
- Verifying cleanup logic (e.g., "does cleanupTempFiles() remove all temp files?")

**When filesystem checks are NOT acceptable:**
- After invoking external tools (git, curl, npm, etc.) — test the invocation, not the side effects
- Testing tool configuration state (sparse-checkout list, gitignore rules) — verify your code sets it correctly

### Test Engine Behavior Only

Automated tests must validate engine behavior with meaningful inputs and outputs. Do not add tests that check whether
design constraints, package boundaries, source layout, build-time wiring, or release-artifact layout constraints hold
unless the test executes product code and observes user- or caller-visible behavior.

Do not add tests whose only purpose is to assert that a package contains only certain classes, that a class is declared
in a specific package or module, that source files do or do not import or mention particular symbols, or that generated
release artifacts have a particular internal layout. These are design and build-time constraints, not engine behavior.

Engine-specific boundary concerns, such as whether common code mentions Claude-only or Codex-only concepts, belong in
code review, architecture notes, and convention files unless they can be expressed as direct executable behavior.
Build-time tests should cover executable build behavior only when the build output is itself the product behavior being
validated.

### Test Access Seams

Shared-secret or other test-only access seams must be registered by the class that owns the behavior under test.
Do not route tests for engine-specific helper methods through an unrelated common orchestrator.

Keep shared test access methods named after the behavior they expose, not after the incidental caller that currently
reaches it.

### Do Not Test Non-Code File Contents

Do not add tests that verify the literal contents of non-code files such as Markdown instructions, documentation,
plans, rules, skills, or concepts. This includes tests that scan `.md` files for frontmatter keys, phrases, include
targets, section names, or source-layout conventions.

If a non-code file affects engine behavior, test the executable behavior that consumes it instead. For example, test
that an artifact builder produces the expected engine artifact from synthetic inputs, or that a parser rejects invalid
synthetic content. Do not test that repository Markdown files contain or omit specific text.

### Do Not Add Retrospective Documentation Tests

Do not add tests that merely document a change after the fact. A test belongs in the suite only when it would catch a
real behavioral regression that matters to users or callers. Avoid tests whose only value is proving that a recently
removed implementation detail, retired artifact layout, or historical packaging choice remains absent when no engine
behavior is exercised.

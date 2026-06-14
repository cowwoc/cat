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

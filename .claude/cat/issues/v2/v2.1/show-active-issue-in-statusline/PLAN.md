# Plan: show-active-issue-in-statusline

## Goal

Replace the statusline's first element (currently showing the git repository directory name) with the active CAT
issue ID for the current Claude session. When no CAT issue is locked for the session, hide the first element and
its following separator entirely, so the statusline begins with the model emoji.

## Parent Requirements

None

## Approaches

### A: Lock-file scan in StatuslineCommand (chosen)
- **Risk:** LOW
- **Scope:** 2 files (StatuslineCommand.java + its test)
- **Description:** Parse `session_id` from JSON input (already done), scan `{projectCatDir}/locks/*.lock` for a
  file whose `session_id` field matches, return the issue ID (lock filename without `.lock`). Uses
  `scope.getProjectCatDir()` already available in the scope.

### B: Environment variable set by work-prepare
- **Risk:** MEDIUM
- **Scope:** 3+ files (work-prepare + StatuslineCommand + documentation)
- **Description:** Have `work-prepare` export `CAT_ACTIVE_ISSUE` env var; statusline reads it. Rejected because
  env vars don't reliably propagate from the Claude Code parent process to the statusline subprocess, and
  disappear when Claude Code restarts.

### C: Dedicated session file written by work-prepare
- **Risk:** LOW-MEDIUM
- **Scope:** 3 files (work-prepare + StatuslineCommand + session file management)
- **Description:** `work-prepare` writes `{sessionCatDir}/active-issue` file; statusline reads it. Rejected as
  redundant — the lock file already contains the same information, and adding a second file creates two sources
  of truth that could diverge.

**Rationale for A:** The lock file is the authoritative source of truth for which issue is active for a session.
Reading it directly avoids duplication and keeps `StatuslineCommand` self-contained.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Lock directory may not exist (no issues ever locked); lock file JSON may be malformed; multiple
  locks could theoretically match the same session_id (shouldn't happen but edge case).
- **Mitigation:** Graceful degradation on all errors (IOException, malformed JSON, missing dir → empty string).
  Multiple matches: return the first match found (lock files are listed in filesystem order).

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java` — replace `getGitInfo()` with
  `getActiveIssue()`, update `execute()` signatures, update statusline format string to conditionally include
  first element
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineCommandTest.java` — replace `getGitInfo`
  tests with lock-file-based tests; update `executeWithDirectory` helper to `executeWithCatDir`

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update `StatuslineCommand.java`:
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java`
  - Remove `getGitInfo(Path directory)` method entirely (no longer needed)
  - Add `getActiveIssue(String sessionId, Path catDir)` package-private method:
    - `catDir` is the project CAT directory (the directory that contains the `locks/` subdirectory)
    - Resolves `catDir.resolve("locks")` to get the lock directory
    - If lock directory does not exist → return `""`
    - Lists `*.lock` files in lock directory; if none → return `""`
    - For each `.lock` file: read content as UTF-8 string, parse JSON with `mapper`, extract `session_id` field
    - If `session_id` field value equals `sessionId` parameter → return the lock filename without `.lock` suffix
    - If no match found → return `""`
    - Wrap entire method body in try-catch `IOException` → return `""` on failure
    - ANSI-sanitize the returned issue ID using `sanitizeForTerminal()` before returning
  - Update method signature: replace `execute(InputStream, PrintStream, Path directory)` with
    `execute(InputStream, PrintStream, Path catDir)` where `catDir` is the project CAT directory (not git dir)
  - Update 2-arg `execute(InputStream, PrintStream)` to delegate:
    `execute(inputStream, outputStream, scope.getProjectCatDir())`
  - In `execute()` body: replace `getGitInfo(directory)` call with `getActiveIssue(sessionId, catDir)`
  - Update Javadoc: `catDir` parameter description = "the project CAT directory containing `locks/`, or
    {@code null} to use the scope's project CAT directory"
  - Update statusline format string in `execute()`:
    - If `activeIssue` is non-empty: prepend `WORKTREE_COLOR + WORKTREE_EMOJI + " " + activeIssue + RESET + " " + SEPARATOR_COLOR + "|" + RESET + " "` before the model element
    - If `activeIssue` is empty: omit that segment entirely (model element is now first, no leading separator)
    - Use `StringJoiner` with `" " + SEPARATOR_COLOR + "|" + RESET + " "` as delimiter, then conditionally
      add the worktree element first
  - Update Javadoc for `execute()`: document `catDir` parameter
  - Update class-level Javadoc: replace "Git worktree/branch name" bullet with "Active CAT issue ID (or absent
    if no issue is locked for this session)"
  - Update Javadoc for removed `getGitInfo` / added `getActiveIssue`:
    - `getActiveIssue` must have `@param sessionId`, `@param catDir`, `@return`, `@throws NullPointerException`
  - Remove the `WORKTREE_EMOJI`, `WORKTREE_COLOR` constants only if they are no longer referenced; keep
    `WORKTREE_EMOJI` and `WORKTREE_COLOR` since they are still used in the conditional display

- Update `StatuslineCommandTest.java`:
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineCommandTest.java`
  - Rename `executeWithDirectory(TestJvmScope scope, String json, Path directory)` helper to
    `executeWithCatDir(TestJvmScope scope, String json, Path catDir)`; update parameter passed to
    `cmd.execute(inputStream, printStream, catDir)`
  - Remove all test methods that test `getGitInfo()` (search for `getGitInfo` in test file)
  - Update all existing calls to `executeWithDirectory` → `executeWithCatDir`, passing a temp cat dir (not a
    git repo); create the dir with `Files.createTempDirectory("cat-")`
  - For tests that previously verified the git repo name in output: update assertion to check model/session
    elements since the worktree element may be absent (use a temp catDir with no lock files)
  - Add these new test methods (self-contained, no class fields, no @BeforeMethod):

    **`getActiveIssueReturnsIssueIdWhenLockMatchesSession()`:**
    ```java
    Path tempDir = Files.createTempDirectory("cat-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      Path lockDir = scope.getProjectCatDir().resolve("locks");
      Files.createDirectories(lockDir);
      Files.writeString(lockDir.resolve("2.1-test-issue.lock"),
        """
        {"session_id": "aaaaaaaa-1111-2222-3333-444444444444", "created_at": 1000000}
        """);
      String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444",
        scope.getProjectCatDir());
      requireThat(result, "result").isEqualTo("2.1-test-issue");
    }
    finally { TestUtils.deleteDirectoryRecursively(tempDir); }
    ```

    **`getActiveIssueReturnsEmptyWhenNoMatchingLock()`:**
    ```java
    Path tempDir = Files.createTempDirectory("cat-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      Path lockDir = scope.getProjectCatDir().resolve("locks");
      Files.createDirectories(lockDir);
      Files.writeString(lockDir.resolve("2.1-other-issue.lock"),
        """
        {"session_id": "bbbbbbbb-2222-3333-4444-555555555555", "created_at": 1000000}
        """);
      String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444",
        scope.getProjectCatDir());
      requireThat(result, "result").isEmpty();
    }
    finally { TestUtils.deleteDirectoryRecursively(tempDir); }
    ```

    **`getActiveIssueReturnsEmptyWhenLocksDirectoryAbsent()`:**
    ```java
    Path tempDir = Files.createTempDirectory("cat-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      // No lock dir created — should return "" gracefully
      String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444",
        scope.getProjectCatDir());
      requireThat(result, "result").isEmpty();
    }
    finally { TestUtils.deleteDirectoryRecursively(tempDir); }
    ```

    **`executeOmitsFirstElementWhenNoActiveIssue()`:**
    ```java
    Path tempDir = Files.createTempDirectory("cat-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String json = """
        {
          "model": {"display_name": "claude-3-5-sonnet"},
          "session_id": "aaaaaaaa-1111-2222-3333-444444444444",
          "cost": {"total_duration_ms": 0},
          "context_window": {"used_percentage": 0}
        }""";
      String result = executeWithCatDir(scope, json, scope.getProjectCatDir());
      // No lock → no worktree element → output does not contain 🌿
      requireThat(result, "result").doesNotContain("🌿");
      // Model element still present
      requireThat(result, "result").contains("claude-3-5-sonnet");
    }
    finally { TestUtils.deleteDirectoryRecursively(tempDir); }
    ```

    **`executeIncludesIssueIdWhenActiveIssueFound()`:**
    ```java
    Path tempDir = Files.createTempDirectory("cat-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path lockDir = scope.getProjectCatDir().resolve("locks");
      Files.createDirectories(lockDir);
      Files.writeString(lockDir.resolve("2.1-my-issue.lock"),
        """
        {"session_id": "aaaaaaaa-1111-2222-3333-444444444444", "created_at": 1000000}
        """);
      String json = """
        {
          "model": {"display_name": "claude-3-5-sonnet"},
          "session_id": "aaaaaaaa-1111-2222-3333-444444444444",
          "cost": {"total_duration_ms": 0},
          "context_window": {"used_percentage": 0}
        }""";
      String result = executeWithCatDir(scope, json, scope.getProjectCatDir());
      requireThat(result, "result").contains("🌿");
      requireThat(result, "result").contains("2.1-my-issue");
    }
    finally { TestUtils.deleteDirectoryRecursively(tempDir); }
    ```

    **`getActiveIssueSanitizesAnsiInjectionInIssueId()`:**
    ```java
    Path tempDir = Files.createTempDirectory("cat-");
    try (TestJvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      Path lockDir = scope.getProjectCatDir().resolve("locks");
      Files.createDirectories(lockDir);
      // Lock filename can't contain ESC in real filesystems, but test sanitization path
      // by injecting via a lock file whose content has an injected session_id
      Files.writeString(lockDir.resolve("2-1-evil\u001b[0m.lock"),
        """
        {"session_id": "aaaaaaaa-1111-2222-3333-444444444444", "created_at": 1000000}
        """);
      String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444",
        scope.getProjectCatDir());
      // ESC character must be stripped by sanitizeForTerminal
      requireThat(result, "result").doesNotContain("\u001b");
    }
    finally { TestUtils.deleteDirectoryRecursively(tempDir); }
    ```

- Run full test suite to confirm all tests pass:
  - Command: `mvn -f client/pom.xml verify`

## Post-conditions

- [ ] Statusline first element shows `🌿 {issue-id}` (e.g., `🌿 2.1-fix-something`) when a lock file in
  `{projectCatDir}/locks/` has a `session_id` matching the value from the JSON input
- [ ] Statusline first element and its trailing `|` separator are absent when no lock matches the session_id
  (statusline begins directly with the model emoji, no leading `|`)
- [ ] IOException reading the lock directory results in first element being absent (no crash)
- [ ] Issue ID displayed in statusline is sanitized to remove ANSI control characters
- [ ] `session_id` for lock lookup comes from JSON input field, not environment variables
- [ ] All new tests pass; no existing tests regressed
- [ ] `mvn -f client/pom.xml verify` exits with code 0
- [ ] E2E: running `/cat:work` on an issue causes `🌿 {issue-id}` to appear in the statusline; an idle session
  with no active lock shows no first element in the statusline

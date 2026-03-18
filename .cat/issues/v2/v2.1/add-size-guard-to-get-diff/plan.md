# Plan: add-size-guard-to-get-diff

## Goal
Add a 2KB raw diff size guard to `GetDiffOutput` so that when the raw `git diff` output exceeds 2048 bytes, the
skill returns a brief notice ("Diff too large (>2KB), skipped.") without reading the full diff content into memory.

## Parent Requirements
None (infrastructure robustness improvement)

## Approaches

### A: Read at most 2KB+1 bytes from the git diff process (Chosen)
- **Risk:** LOW
- **Scope:** 2 files (GetDiffOutput.java, GetDiffOutputTest.java)
- **Description:** Add `getRawDiffLimited()` to `GitHelper` that starts `git diff` via `ProcessBuilder`, reads at most
  `maxBytes+1` bytes from stdout, and destroys the process early if exceeded. This ensures the full diff is never
  loaded into Java heap when it exceeds the limit.

### B: Run git diff twice — once with wc -c for size, once for content
- **Risk:** MEDIUM
- **Description:** Rejected: requires two full git diff executions; does not satisfy "check before reading full content
  into memory" post-condition.

### C: Derive size from git diff --stat line counts
- **Risk:** HIGH
- **Description:** Rejected: byte size of raw diff is not accurately derivable from line change counts.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Destroying the git process mid-read could leave zombie processes; must call `waitFor()` after
  `destroyForcibly()`.
- **Mitigation:** Call `process.destroyForcibly()` then `process.waitFor()` with interrupt handling in the exceeded
  path. Verified by unit test.

## Impact Notes
- `fix-work-merge-gate-timing` references `cat:get-diff-agent` for pre-approval-gate diffs; this change means that
  when the approval-gate diff exceeds 2KB, the agent sees the brief notice instead of the rendered table. This is
  intentional — large diffs at the approval gate would bloat agent context.
- `fix-post-bash-hook-test-failure-false-positive` pattern-matches on get-diff output; the new brief notice text
  ("Diff too large (>2KB), skipped.") is distinct from test failure patterns and will not trigger false positives.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java` — add imports, add constant, add
  `getRawDiffLimited()` to `GitHelper`, update call site in `getOutput()`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java` — add
  `rawDiffExceeding2KBReturnsSkipNotice()` test

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- **Add 2KB guard to `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java`:**

  **Step 1 — Add imports.**

  Find the line `import java.io.IOException;` in the import block. Insert the following two lines immediately
  after it and before `import java.nio.file.Files;`:
  ```java
  import java.io.InputStream;
  import java.nio.charset.StandardCharsets;
  ```
  The resulting block reads:
  ```java
  import java.io.IOException;
  import java.io.InputStream;
  import java.nio.charset.StandardCharsets;

  import java.nio.file.Files;
  ```

  **Step 2 — Add constant.**

  Find this exact block in the class body:
  ```java
  private static final int MAX_TOTAL_CHANGES = 50_000;
  /**
   * Maximum number of files to display in the summary.
   */
  private static final int MAX_FILES_DISPLAYED = 20;
  ```
  Insert the new constant between `MAX_TOTAL_CHANGES` and the Javadoc that precedes `MAX_FILES_DISPLAYED`:
  ```java
  private static final int MAX_TOTAL_CHANGES = 50_000;
  /**
   * Maximum raw diff size in bytes before the diff is considered too large to read into context.
   * Diffs exceeding this threshold return a brief notice instead of the rendered table.
   */
  private static final int MAX_RAW_DIFF_BYTES = 2_048;
  /**
   * Maximum number of files to display in the summary.
   */
  private static final int MAX_FILES_DISPLAYED = 20;
  ```

  **Step 3 — Add `getRawDiffLimited()` method to the `GitHelper` static inner class.**

  Find this exact closing sequence at the end of the `GitHelper` inner class (the `}` at column 4 closes
  `getRawDiff()`, and the `}` at column 2 closes the `GitHelper` class itself):
  ```java
      catch (IOException _)
      {
        return null;
      }
    }
  }
  ```
  Insert the new method between the `}` that closes `getRawDiff()` and the `}` that closes `GitHelper`:
  ```java
      catch (IOException _)
      {
        return null;
      }
    }

    /**
     * Gets the raw diff output between base and HEAD, limited to {@code maxBytes} bytes.
     * <p>
     * Reads at most {@code maxBytes + 1} bytes from the git diff process stdout. If the output
     * exceeds {@code maxBytes}, the process is destroyed immediately and {@code null} is returned
     * without loading the full diff into memory.
     *
     * @param projectRoot the project root path
     * @param targetBranch the target branch for comparison
     * @param maxBytes maximum bytes to read; if exceeded, returns null
     * @return the raw diff content if its byte size is {@code <= maxBytes}, or {@code null} if exceeded or
     *         if the git command fails
     * @throws NullPointerException if {@code projectRoot} or {@code targetBranch} are null
     */
    static String getRawDiffLimited(Path projectRoot, String targetBranch, int maxBytes)
    {
      requireThat(projectRoot, "projectRoot").isNotNull();
      requireThat(targetBranch, "targetBranch").isNotNull();

      ProcessBuilder pb = new ProcessBuilder("git", "-C", projectRoot.toString(),
        "diff", targetBranch + "..HEAD");
      pb.redirectErrorStream(false);
      Process process;
      try
      {
        process = pb.start();
      }
      catch (IOException _)
      {
        return null;
      }

      byte[] buffer = new byte[maxBytes + 1];
      int totalRead = 0;
      try (InputStream in = process.getInputStream())
      {
        int bytesRead;
        while (totalRead < buffer.length)
        {
          bytesRead = in.read(buffer, totalRead, buffer.length - totalRead);
          if (bytesRead == -1)
            break;
          totalRead += bytesRead;
        }
      }
      catch (IOException _)
      {
        process.destroyForcibly();
        try
        {
          process.waitFor();
        }
        catch (InterruptedException ie)
        {
          Thread.currentThread().interrupt();
        }
        return null;
      }

      if (totalRead > maxBytes)
      {
        // Diff exceeds the size limit: destroy the process to prevent blocking on unread stdout
        process.destroyForcibly();
        try
        {
          process.waitFor();
        }
        catch (InterruptedException ie)
        {
          Thread.currentThread().interrupt();
        }
        return null;
      }

      int exitCode;
      try
      {
        exitCode = process.waitFor();
      }
      catch (InterruptedException ie)
      {
        Thread.currentThread().interrupt();
        return null;
      }
      if (exitCode != 0)
        return null;

      return new String(buffer, 0, totalRead, StandardCharsets.UTF_8);
    }
  }
  ```

  **Step 4 — Update call site** in the `getOutput(Path projectRoot, String targetBranch)` method.

  Find and replace this exact block:
  ```java
  // Get raw diff output
  String rawDiff = GitHelper.getRawDiff(projectRoot, targetBranch);
  if (rawDiff == null || rawDiff.isEmpty())
    return "No diff output available (git diff command failed or returned empty).";
  ```

  With:
  ```java
  // Get raw diff output, limited to MAX_RAW_DIFF_BYTES to avoid bloating agent context
  String rawDiff = GitHelper.getRawDiffLimited(projectRoot, targetBranch, MAX_RAW_DIFF_BYTES);
  if (rawDiff == null)
    return "Diff too large (>2KB), skipped.";
  if (rawDiff.isEmpty())
    return "No diff output available (git diff command failed or returned empty).";
  ```

  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java`

- **Add test to `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java`:**

  **Existing helpers confirmed present in `GetDiffOutputTest`:** `createIssueDirWithTargetBranch(Path, String)` (line
  1890), `TestUtils.deleteDirectoryRecursively(Path)` (via `TestUtils` class), and `runGit(Path, String...)` (line
  1905) are all already defined in this file. Do not re-implement them.

  **`TestJvmScope` constructor:** Use `new TestJvmScope()` with no arguments. This zero-arg constructor is
  confirmed by all existing tests in this file and is valid for tests that do not need injectable paths.

  **`getOutput` invocation:** The test calls `handler.getOutput(new String[]{issuePath.toString()})`, which
  invokes the public `getOutput(String[] args)` method — the same signature used by all existing tests in this
  file.

  Add the following test method before the `private void setupTestRepo` helper method (the first private helper
  method in the file):

  ```java
  /**
   * Verifies that a raw diff exceeding 2KB returns the brief skip notice.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void rawDiffExceeding2KBReturnsSkipNotice() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-large-test");
      try
      {
        // Initialize repo; rename the empty initial branch to v2.0 (same pattern as setupTestRepo)
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "v2.0");

        // Create minimal cat-config.json so Config.load() does not fail
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"displayWidth\": 80}");

        // Create initial commit on v2.0 branch
        Files.writeString(tempDir.resolve("readme.txt"), "initial\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial commit");

        // Create feature branch and add a file whose raw diff exceeds 2048 bytes.
        // Each formatted line is ~50 chars; with the diff + prefix and newline, 60 lines
        // produce approximately 3120 bytes of raw diff, well above the 2048-byte limit.
        runGit(tempDir, "checkout", "-b", "2.0-large-change");
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 60; i++)
          largeContent.append(String.format("line_%04d_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx%n", i));
        Files.writeString(tempDir.resolve("large.txt"), largeContent.toString());
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "add large file");

        GetDiffOutput handler = new GetDiffOutput(scope);
        // createIssueDirWithTargetBranch writes STATE.md with "Target Branch: v2.0"
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        requireThat(result, "result").contains("too large").contains("2KB");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }
  ```

  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java`

- **Run tests to verify:**
  ```bash
  mvn -f client/pom.xml test -Dtest=GetDiffOutputTest
  ```
  All tests must pass (exit code 0).

- **Update STATE.md** — set `Status: in-progress`, `Progress: 100%` and add post-condition checkmarks once
  implementation is verified.
  - Files: `.cat/issues/v2/v2.1/add-size-guard-to-get-diff/STATE.md`

## Post-conditions
- [ ] `GetDiffOutput.getOutput()` returns a message containing "too large" and "2KB" when raw diff byte size > 2048
- [ ] `GetDiffOutput.getOutput()` produces normal 4-column rendered table for diffs ≤ 2048 bytes (existing tests
  verify this)
- [ ] The `getRawDiffLimited()` method reads at most 2049 bytes before returning null (size check before full read)
- [ ] Notice message is deterministic and contains ">2KB" or "2KB" (verifiable via unit test assertion)
- [ ] Unit test `rawDiffExceeding2KBReturnsSkipNotice` passes in `GetDiffOutputTest`
- [ ] All existing `GetDiffOutputTest` tests pass (no regressions)
- [ ] E2E: Run `GetDiffOutputTest` against a repo with a large synthetic diff and confirm "Diff too large (>2KB),
  skipped." is returned

# Plan: fix-verify-state-commit-index-suggestion

## Type
feature

## Goal
Fix `VerifyStateInCommit.java` so that when it blocks a commit missing `index.json`, the suggested
`git add` command references the specific current issue's `index.json` path rather than the glob
`.cat/issues/**/index.json`, which incorrectly stages all modified `index.json` files in the repo.

## Parent Requirements
None

## Approaches

### A: Derive path from worktree branch name (chosen)
- **Risk:** LOW
- **Scope:** 2 files (minimal — one source file, one test file)
- **Description:** Call `git rev-parse --abbrev-ref HEAD` in `effectiveDirectory` to get the branch
  name (e.g., `2.1-fix-something`), parse out version components and bare issue name using a regex,
  then construct `.cat/issues/v{major}/v{major}.{minor}/{bareName}/index.json`. Falls back to the glob
  if branch parsing fails.

### B: Walk .cat/issues/ directory
- **Risk:** MEDIUM
- **Scope:** 2 files (minimal)
- **Description:** Walk the `.cat/issues/` subdirectory tree in the worktree to find all `index.json`
  files and use the first result. Simpler but could find multiple files in edge cases (e.g., worktrees
  with multiple issue directories), and requires filesystem traversal at hook time.

### C: Derive path from staged files context
- **Risk:** LOW
- **Scope:** 2 files (minimal)
- **Description:** When `!indexJsonStaged`, we do not have any staged `index.json` to inspect. However
  we could search for unstaged `.cat/issues/**/index.json` files in the worktree. This is similar to
  approach B in that it requires a filesystem walk.

**Rationale for Approach A:** The worktree branch IS the issue ID by CAT convention; parsing it
produces a deterministic, single result. No filesystem walk is needed. The branch name encodes the
exact issue path in a structured, predictable format. Approach B and C both require filesystem
traversal and could match multiple files in corner cases.

**Edge cases for Approach A:**
- Branch names with major.minor: `2.1-issue-name` → `.cat/issues/v2/v2.1/issue-name/index.json`
- Branch names with major only: `3-issue-name` → `.cat/issues/v3/issue-name/index.json`
- Branch names with major.minor.patch: `2.1.1-issue-name` → `.cat/issues/v2/v2.1/v2.1.1/issue-name/index.json`
- Branch parsing failure (IOException or regex non-match): fall back to glob suggestion

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Branch name parsing is brittle if CAT branch naming convention changes; `git` command
  in hook could fail
- **Mitigation:** Catch IOException from `git rev-parse`, fall back to glob when parsing fails; null
  return from `deriveIndexJsonPath` triggers glob fallback

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/VerifyStateInCommit.java` — add private
  helper method `deriveIndexJsonPath(String directory)`, update block message in `!indexJsonStaged`
  branch to use the derived path
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/VerifyStateInCommitTest.java` — add new
  test `blockMessageContainsSpecificIssuePath()` verifying that the block message contains the
  specific path derived from the branch name

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- In `VerifyStateInCommit.java`, add a private helper method with this exact signature and logic:
  ```java
  private String deriveIndexJsonPath(String directory)
  ```
  The method must:
  1. Call `GitCommands.runGit(Path.of(directory), "rev-parse", "--abbrev-ref", "HEAD")` and strip
     the result
  2. Apply a regex with three capture patterns tried in order:
     - `^(\d+)\.(\d+)\.(\d+)-(.+)$` → major.minor.patch format:
       path = `.cat/issues/v{1}/v{1}.{2}/v{1}.{2}.{3}/{4}/index.json`
     - `^(\d+)\.(\d+)-(.+)$` → major.minor format:
       path = `.cat/issues/v{1}/v{1}.{2}/{3}/index.json`
     - `^(\d+)-(.+)$` → major-only format:
       path = `.cat/issues/v{1}/{2}/index.json`
  3. Return the constructed path string if a pattern matched, or `null` if no pattern matched
  4. Catch `IOException` from the git call and return `null`
  Use `Config.CAT_DIR_NAME` for the `.cat` prefix (i.e., `Config.CAT_DIR_NAME + "/issues/..."`).
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/VerifyStateInCommit.java`

- In `VerifyStateInCommit.java`, update the `!indexJsonStaged` return statement in `check()`:
  Replace the current block message that uses the glob:
  ```java
  "  git add " + Config.CAT_DIR_NAME + "/issues/**/index.json"
  ```
  With logic that calls `deriveIndexJsonPath(effectiveDirectory)`, stores the result in a local
  variable `specificPath`, and uses:
  - If `specificPath != null`: `"  git add " + specificPath`
  - If `specificPath == null`: `"  git add " + Config.CAT_DIR_NAME + "/issues/**/index.json"`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/VerifyStateInCommit.java`

- In `VerifyStateInCommitTest.java`, add a new `@Test` method `blockMessageContainsSpecificIssuePath()`.
  The method must follow the exact structure below:
  ```java
  @Test
  public void blockMessageContainsSpecificIssuePath() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktreeDir = TestUtils.createWorktree(mainRepo, worktreesDir, "2.1-my-test-issue");
      try
      {
        Files.writeString(worktreeDir.resolve("some-file.java"), "class Foo {}");
        TestUtils.runGit(worktreeDir, "add", "some-file.java");

        JsonMapper mapper = scope.getJsonMapper();
        VerifyStateInCommit handler = new VerifyStateInCommit();
        String command = "git commit -m \"bugfix: fix the thing\"";
        BashHandler.Result result = handler.check(
          TestUtils.bashInput(mapper, command, worktreeDir.toString(), "test-session"));

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason")
          .contains(".cat/issues/v2/v2.1/my-test-issue/index.json");
        requireThat(result.reason(), "reason").doesNotContain("**");
      }
      finally
      {
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(worktreeDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }
  ```
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/VerifyStateInCommitTest.java`

- Run `mvn -f client/pom.xml verify` from `/workspace` to verify all tests pass
  - Files: (build verification, no file modification)

- Update `index.json` to `{"status":"closed","resolution":"implemented","targetBranch":"v2.1"}` and
  stage it in the SAME commit as the implementation (this is enforced by VerifyStateInCommit hook)
  - Files: `.cat/issues/v2/v2.1/fix-verify-state-commit-index-suggestion/index.json`

## Post-conditions
- [ ] When `VerifyStateInCommit` blocks a commit in a CAT worktree, the suggested `git add` command
  references the specific current issue path, not a glob pattern
- [ ] The specific path is derived from the worktree branch name
- [ ] Running the suggested `git add` command stages ONLY the current issue `index.json`, not
  `index.json` files from other issues
- [ ] Existing unit tests for `VerifyStateInCommit` pass with the updated message
- [ ] New unit test added: blocked commit in a worktree produces a specific issue path in the suggestion
- [ ] E2E: `mvn -f client/pom.xml verify` exits 0 with all tests passing

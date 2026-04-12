# Plan

## Goal

Add sparse-checkout configuration to worktree creation to exclude `.claude/` directory, preventing Claude Code from loading duplicate rules when walking up directory tree from worktrees.

## Problem Analysis

Claude Code loads CLAUDE.md and `.claude/rules/` files by walking up the directory tree from the current working directory. When working in a git worktree (e.g., `/workspace/.cat/work/worktrees/my-issue/`), Claude Code finds:
1. `.claude/rules/` in the worktree (from git checkout - `.claude/` is tracked)
2. `.claude/rules/` in the main workspace (via relative path `../../../../`)

This causes duplicate loading of the same rules files.

## Research Findings

- Git sparse-checkout allows excluding paths from a worktree's working directory
- Sparse-checkout must be configured AFTER `git worktree add` completes
- The command pattern: `git -C "$WORKTREE_PATH" sparse-checkout set --no-cone '/*' '!/.claude'`
- `--no-cone` mode required for simple pattern matching
- Pattern `'/*'` includes all files, `'!/.claude'` excludes the .claude directory
- This is worktree-local configuration - doesn't affect main workspace or other worktrees
- `.cat/` directory should NOT be excluded as CAT plugin loads from `${CLAUDE_PROJECT_DIR}/.cat/rules/` (absolute path)

**Current worktree creation locations:**
1. `WorkPrepare.java` - CLI tool that creates worktrees for `/cat:work-agent`
2. `Empirical TestRunner.java:createTestWorktree()` - Test utility for SPRT trials
3. `TestUtils.java:createTempGitRepo()` - Test helper (creates bare repos, not worktrees)

## Pre-conditions

(none)

## Jobs

### Job 1

- Read `/workspace/client/src/main/java/io/github/cowwoc/cat/client/tool/WorkPrepare.java` to locate the method that invokes `git worktree add`
- Identify the ProcessBuilder that runs `git worktree add` (likely in a method like `createWorktree()` or `run()`)
- Immediately after the `git worktree add` process completes successfully (after `waitFor()` returns 0), insert sparse-checkout configuration:
  ```java
  // Configure sparse-checkout to exclude .claude/ directory
  ProcessBuilder sparseCheckout = new ProcessBuilder("git", "-C", worktreePath.toString(),
    "sparse-checkout", "set", "--no-cone", "/*", "!/.claude");
  sparseCheckout.redirectErrorStream(true);
  try (Process process = sparseCheckout.start())
  {
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();
    if (exitCode != 0)
      throw new IOException("git sparse-checkout failed (exit " + exitCode + "): " + output.strip());
  }
  ```
- Read `/workspace/client/src/test/java/io/github/cowwoc/cat/client/empirical/EmpiricalTestRunner.java` to locate the `createTestWorktree()` method
- In `createTestWorktree()`, immediately after the `git worktree add` command completes successfully, insert the same sparse-checkout ProcessBuilder block (exact code as above, replacing `worktreePath` with the local variable name used in that method)
- Add JavaDoc comment above the sparse-checkout block in both files: `// Exclude .claude/ directory from worktree to prevent duplicate rule loading when Claude Code walks up the directory tree`
- Run `mvn -f /workspace/client/pom.xml verify -e` to ensure no compilation or lint errors
- Commit changes with message: `feature: exclude .claude from worktrees via sparse-checkout`

### Job 2

- Create test `/workspace/client/src/test/java/io/github/cowwoc/cat/client/test/ExcludeClaudeFromWorktreesTest.java` with license header (read `/workspace/.claude/rules/license-header.md` for exact format)
- In test setup (`@BeforeEach` method):
  1. Call `TestUtils.createTempGitRepo()` to create a temporary git repository (returns `Path`)
  2. Create `.claude/rules/test.md` file in the temp repo with content `# Test Rule`
  3. Create `.cat/config.json` file in the temp repo with content `{}`
  4. Create `client/pom.xml` file in the temp repo with content `<project/>`
  5. Run `git add .claude/rules/test.md .cat/config.json client/pom.xml` in the temp repo
  6. Run `git commit -m "Initial commit"` in the temp repo
  7. Invoke WorkPrepare CLI tool: `${CLAUDE_PLUGIN_ROOT}/client/bin/work-prepare <session_id> <issue_id>` (use synthetic UUIDs for test) to create worktree in `.cat/work/worktrees/<issue_id>/` subdirectory of temp repo
  8. Store worktree path in test instance variable
- Test case 1: `newWorktreeExcludesClaude()`
  - Assertion: `Files.exists(worktreePath.resolve(".claude"))` returns `false`
- Test case 2: `newWorktreeSparseCheckoutConfigured()`
  - Run `git -C <worktreePath> sparse-checkout list` and capture output
  - Assertion: output contains exactly two lines: `/*` and `!/.claude` (in any order)
- Test case 3: `mainWorkspaceUnaffected()`
  - Assertion: `Files.exists(tempRepoPath.resolve(".claude/rules/test.md"))` returns `true`
- Test case 4: `catDirectoryPresent()`
  - Assertion: `Files.exists(worktreePath.resolve(".cat"))` returns `true`
- Test case 5: `otherFilesPresent()`
  - Assertion: `Files.exists(worktreePath.resolve("client/pom.xml"))` returns `true`
- Run `mvn -f /workspace/client/pom.xml verify -e` to verify tests pass
- Commit with message: `test: verify .claude exclusion in worktrees`
- Update `/workspace/.cat/work/worktrees/2.1-exclude-claude-from-worktrees/.cat/issues/v2/v2.1/exclude-claude-from-worktrees/index.json` field `status` from `"in_progress"` to `"closed"`
- Commit with message: `test: close issue exclude-claude-from-worktrees`

## Post-conditions

- [ ] New worktrees created by CAT exclude `.claude/` directory via sparse-checkout
- [ ] Sparse-checkout configuration applied correctly after `git worktree add`
- [ ] Existing worktrees remain unaffected
- [ ] Claude Code no longer loads duplicate rules from both worktree and main workspace `.claude/` directories
- [ ] Tests passing
- [ ] E2E verification: Create worktree, verify `.claude/` directory absent, confirm no duplicate rule loading

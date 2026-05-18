# Plan: Add Git Safety Hook

## Goal

Add a PreToolUse BashHandler that blocks destructive git commands after normalizing git global flags, so safety checks cannot be bypassed with flag prefixes like `-C`.

## Parent Requirements

None (infrastructure hardening issue).

## Background

A known Claude Code permission-bypass pattern uses git global flags (for example `git -C <path> ...`) to evade prefix-based command deny rules. Current CAT bash hook checks do not consistently normalize global flags before matching.

## Approaches

### A: Centralized Normalization + Dedicated Destructive Handler (chosen)
- **Risk:** MEDIUM
- **Scope:** 10 files (comprehensive)
- **Description:** Add one reusable git-command normalizer, retrofit all existing git handlers to consume normalized commands, and add a new handler for destructive commands not currently blocked.
- **Why chosen:** Single canonical parser behavior prevents drift across handlers and closes current and future bypass variants.

### B: Expand Regex in Each Existing Handler (rejected)
- **Risk:** HIGH
- **Scope:** 6 files (moderate)
- **Description:** Keep raw-command matching and add per-handler regex support for every global-flag variant.
- **Why rejected:** Duplicates parsing logic across handlers and is brittle when new global flags or command shapes are introduced.

### C: External Shell Parser Integration (rejected)
- **Risk:** MEDIUM
- **Scope:** 12+ files (comprehensive)
- **Description:** Introduce AST-level shell parsing for git command detection.
- **Why rejected:** Overkill for current scope; adds integration/maintenance cost without clear gain over a focused normalizer.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:**
  - Over-blocking safe git workflows if pattern matching is too broad.
  - Regression in existing handlers while switching from raw to normalized command matching.
  - Normalizer edge cases for chained commands and mixed shell operators.
- **Mitigation:**
  - Add targeted unit tests for every blocked and allowed command variant.
  - Preserve existing `# ACKNOWLEDGED` bypass semantics.
  - Run the full client verification command after hook/test updates.

## Files to Modify

- `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/GitCommandNormalizer.java` - new utility for extracting and normalizing git commands.
- `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockDestructiveGitCommands.java` - new BashHandler for destructive command blocking.
- `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/ValidateGitOperations.java` - consume normalized commands for reset/push checks.
- `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockMainRebase.java` - consume normalized commands for protected-branch rebase checks.
- `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/ValidateGitFilterBranch.java` - consume normalized commands for history-rewrite checks.
- `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockReflogDestruction.java` - consume normalized commands for reflog/gc destruction checks.
- `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockMergeCommits.java` - consume normalized commands for merge-policy checks.
- `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/PreToolUseHook.java` - register new `BlockDestructiveGitCommands` handler.
- `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/GitCommandNormalizerTest.java` - new unit tests for normalization logic.
- `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/BlockDestructiveGitCommandsTest.java` - new unit tests for block/allow behavior.
- `client/plugin/tests/scripts/hooks/validate-git-operations.bats` - update integration coverage for new block rules and flag-prefix bypass cases.

## Pre-conditions

- [ ] All dependent issues are closed.
- [ ] Implementation occurs in the issue worktree branch `2.1-add-git-safety-hook`.

## Jobs

### Job 1
- Implement `GitCommandNormalizer` with strict, reusable normalization behavior.
  - Files: `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/GitCommandNormalizer.java`
  - Add a static API that accepts raw bash command text and returns normalized git command strings.
  - Split command chains on `&&`, `||`, `;`, and `|` boundaries.
  - For each git command segment, remove global flags in-place before matching:
    - `-C <path>`
    - `--git-dir=<path>` and `--git-dir <path>`
    - `--work-tree=<path>` and `--work-tree <path>`
    - `-c <key>=<value>`
  - Preserve subcommand/arguments order after flag stripping.
  - Use Allman braces, 2-space indentation, and `requireThat()` parameter validation.

- Add `BlockDestructiveGitCommands` and enforce destructive command blocking with ACK bypass parity.
  - Files: `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockDestructiveGitCommands.java`
  - Implement `BashHandler#check()` to:
    - Short-circuit allow when raw command contains `# ACKNOWLEDGED`.
    - Normalize command segments via `GitCommandNormalizer`.
    - Block with explicit reason when any normalized command matches:
      - `git push --force` or `git push -f` (any branch)
      - `git branch -D <branch>` where `<branch>` is `main`, `master`, or `v[0-9]+`
      - `git checkout .` or `git checkout -- .`
      - `git restore .` or destructive staging/worktree variants targeting full tree
      - `git clean` with force flag (including `-f`, `-fd`, combined forms)
      - `git stash drop --all` or `git stash clear`
    - Keep safe alternatives in block messages when applicable (for example `--force-with-lease`, `git clean -n`).

- Retrofit existing git handlers to match normalized commands instead of raw command prefixes.
  - Files:
    - `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/ValidateGitOperations.java`
    - `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockMainRebase.java`
    - `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/ValidateGitFilterBranch.java`
    - `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockReflogDestruction.java`
    - `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockMergeCommits.java`
  - Replace direct raw-string git matching with normalized command iteration.
  - Preserve existing behavior and block messages unless change is required for correctness.
  - Preserve existing ACK handling semantics where already supported.

- Register the new handler in hook execution order.
  - Files: `client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/PreToolUseHook.java`
  - Add `new BlockDestructiveGitCommands(scope)` in the handlers list.
  - Place ordering so destructive-command checks run with existing git safety checks and do not bypass earlier required checks.

- Write failing tests first, then implement until tests pass (TDD).
  - Files:
    - `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/GitCommandNormalizerTest.java`
    - `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/BlockDestructiveGitCommandsTest.java`
  - `GitCommandNormalizerTest` must cover:
    - single and combined global flags (`-C`, `--git-dir`, `--work-tree`, `-c`)
    - command chains with `&&`, `||`, `;`, `|`
    - non-git segments, empty input, and `git` without subcommand
  - `BlockDestructiveGitCommandsTest` must cover:
    - every blocked pattern (direct and with global-flag prefixes)
    - `# ACKNOWLEDGED` bypass
    - allowed safe variants (`--force-with-lease`, `git branch -d`, `git clean -n`, version-safe branch deletion)
  - Follow existing `io.github.cowwoc.cat.client.test` conventions and `TestClaudeHook`/`TestUtils` helpers for hook testing.

- Update integration coverage for current bash-hook script tests.
  - Files: `client/plugin/tests/scripts/hooks/validate-git-operations.bats`
  - Add cases for `git -C ...` prefixed dangerous commands to ensure bypass resistance.
  - Update expected behavior for force-push to non-main branches (now blocked).
  - Keep existing allow-cases for `--force-with-lease` and acknowledged `reset --hard` where still applicable.

- Run verification and prepare issue closure metadata.
  - Files: `.cat/issues/v2/v2.1/add-git-safety-hook/index.json`
  - Run: `mvn -f client/pom.xml verify -e`
  - Fix regressions introduced by normalization retrofit and block-rule expansion.
  - After verification passes and implementation is complete, set issue status to `closed` in `index.json`.

## Post-conditions

- [ ] `GitCommandNormalizer` strips `-C`, `--git-dir`, `--work-tree`, and `-c` global flags before safety matching.
- [ ] `BlockDestructiveGitCommands` blocks force push (any branch), protected/version branch force-delete, checkout/restore discard-all, force clean, and destructive stash clear/drop.
- [ ] Existing git BashHandlers (`ValidateGitOperations`, `BlockMainRebase`, `ValidateGitFilterBranch`, `BlockReflogDestruction`, `BlockMergeCommits`) consume normalized git commands.
- [ ] `# ACKNOWLEDGED` bypass behavior remains available where intended.
- [ ] Unit and integration tests cover direct commands and global-flag-prefixed variants for all new/updated matching logic.
- [ ] `mvn -f client/pom.xml verify -e` passes with no new violations.

# Plan: eliminate-cwd-worktree-assumption

## Current State

Java hooks and skill instructions assume the shell's current working directory (cwd) is the issue
worktree. This assumption is broken because Claude Code's Bash tool resets cwd to the configured
project directory (`/workspace`) on every invocation. Agents cannot persist a `cd` across Bash calls
when the target is outside the project directory (e.g., the config-dir worktree path).

Two failure modes:
1. **Java hooks** read `System.getProperty("user.dir")` to derive worktree context — always returns
   `/workspace`, producing wrong results (wrong branch diffs, skipped isolation checks, misplaced commits).
2. **Skills/agents** are instructed to `cd` into the worktree and use relative paths — the `cd` only
   lasts within a single Bash invocation, so subsequent calls silently operate on `/workspace`.

## Target State

- Java hooks/tools derive the worktree path exclusively via `session_id → lock file → worktree path`
  (fail-fast if worktree path is needed but no lock exists)
- Skills and agents use `${WORKTREE_PATH}/path` (absolute) for all Read/Edit/Write file operations
- Git commands keep `cd ${WORKTREE_PATH} && git ...` (single Bash call — cwd persists within the call)
- `EnforceWorktreePathIsolation` remains as a safety net for absolute-path violations

## Parent Requirements

None — infrastructure correctness fix

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Skills change from relative-path to absolute-path conventions; agents must
  have `WORKTREE_PATH` in context at all times
- **Mitigation:** `EnforceWorktreePathIsolation` catches agents that slip back to `/workspace` paths;
  fail-fast in Java surfaces missing lock immediately

## Files to Modify

**Java hooks (remove `user.dir` dependency):**
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java` — replace
  `detectFromWorktreePath()` cwd parse with lock-based branch detection
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java` — replace cwd
  check with `WorktreeContext.forSession()` lookup
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java` — replace cwd-based
  commit location with lock-based lookup; fail-fast if no lock
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java` — replace cwd vs
  project-dir comparison with `WorktreeContext.forSession()` null check

**Skills/agents (absolute paths instead of cd + relative):**
- `plugin/skills/work/first-use.md` — remove "cd into worktree" instruction; require `${WORKTREE_PATH}/` prefix
- `plugin/skills/work-with-issue-agent/first-use.md` — replace relative-path git/file operations with absolute
- `plugin/agents/work-execute.md` — update file operation instructions to use absolute paths
- `plugin/agents/work-verify.md` — update file operation instructions to use absolute paths
- `plugin/agents/stakeholder-*.md` — update `.claude/cat/review/` relative paths to `${WORKTREE_PATH}/.claude/cat/review/`
- `plugin/concepts/worktree-isolation.md` — update conventions to reflect absolute-path model
- `plugin/concepts/git-operations.md` — update git command examples (keep `cd ${WORKTREE_PATH} && git ...`)
- `plugin/skills/git-commit-agent/first-use.md` — remove pwd-based worktree verification
- `plugin/skills/validate-git-safety-agent/first-use.md` — replace `pwd` checks with lock-based detection

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1: Fix Java hooks

- Fix `GetDiffOutput.java` — remove `detectFromWorktreePath()`, derive target branch from lock →
  issue ID → branch name (e.g., `2.1-issue-name` → branch `v2.1`)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java`
- Fix `WarnBaseBranchEdit.java` — replace `System.getProperty("user.dir")` cwd check with
  `WorktreeContext.forSession()`: active worktree = enforce, null = skip
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java`
- Fix `RecordLearning.java` — replace cwd with lock-based worktree lookup; fail-fast with clear
  error if lock not found when commit location is needed
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
- Fix `BlockMainRebase.java` — replace cwd == projectDir check with `WorktreeContext.forSession()`
  null check (null = no active worktree = main context)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java`
- Write/update tests for all four fixes
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/`
- Run `mvn -f client/pom.xml test` — confirm BUILD SUCCESS

### Wave 2: Update skills and agents

- Update `plugin/skills/work/first-use.md`: remove `cd "${worktree_path}"` instruction; replace
  with convention "use `${WORKTREE_PATH}/path` for all file operations; git commands use
  `cd ${WORKTREE_PATH} && git ...`"
- Update `plugin/skills/work-with-issue-agent/first-use.md`: replace all relative-path file
  operations with `${WORKTREE_PATH}/`-prefixed absolute paths
- Update `plugin/agents/work-execute.md` and `work-verify.md`: same absolute-path convention
- Update all `plugin/agents/stakeholder-*.md`: replace `.claude/cat/review/<stakeholder>-concerns.json`
  with `${WORKTREE_PATH}/.claude/cat/review/<stakeholder>-concerns.json`
- Update `plugin/concepts/worktree-isolation.md` and `git-operations.md`: document new convention
- Update `plugin/skills/git-commit-agent/first-use.md`: remove `pwd` worktree verification
- Update `plugin/skills/validate-git-safety-agent/first-use.md`: replace `pwd` checks with
  lock-based approach

## Post-conditions

- [ ] No `System.getProperty("user.dir")` calls remain in hook/tool code for worktree detection
- [ ] No skill instructs agents to `cd` into the worktree as a persistent context setup
- [ ] All skill file-operation examples use `${WORKTREE_PATH}/path` (absolute)
- [ ] Git command examples keep `cd ${WORKTREE_PATH} && git ...` (single-call pattern)
- [ ] `mvn -f client/pom.xml test` exits 0
- [ ] E2E: Run `/cat:work` on a real issue; verify hooks produce correct output without agent
  having `cd`'d into the worktree

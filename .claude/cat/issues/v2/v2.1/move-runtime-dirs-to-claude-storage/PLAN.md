# Plan: move-runtime-dirs-to-claude-storage

## Goal
Move CAT runtime directories out of the repository into Claude Code's native storage layout: cross-session project
files (`worktrees/`, `locks/`) to `~/.config/claude/projects/{encoded-project-dir}/cat/`, and session-scoped files
(`verify/`, `e2e-config-test/`) to `~/.config/claude/projects/{encoded-project-dir}/{sessionId}/cat/`.

## Satisfies
None

## Approach: Direct Path Encoding

Resolve the Claude Code project directory path by applying the encoding algorithm directly:

```java
String encoded = projectDir.replace("/", "-").replace(".", "-");
Path projectCatDir = configDir.resolve("projects").resolve(encoded).resolve("cat");
```

Encoding examples:
- `/workspace` → `-workspace`
- `/home/user/myproject` → `-home-user-myproject`
- `/tmp` → `-tmp`

## Risk Assessment
- **Risk Level:** HIGH
- **Concerns:** ~75 file references across plugin/ and client/src/ need updating; encoding algorithm is a Claude Code
  implementation detail that could change
- **Mitigation:** Encoding is a simple, obvious transform unlikely to change; validate at startup that the resolved
  path exists

## Directory Mapping

| Directory | Current Location | New Location | Scope |
|-----------|-----------------|--------------|-------|
| `worktrees/` | `{projectDir}/.claude/cat/worktrees/` | `{configDir}/projects/{encoded}/cat/worktrees/` | Cross-session, per-project |
| `locks/` | `{projectDir}/.claude/cat/locks/` | `{configDir}/projects/{encoded}/cat/locks/` | Cross-session, per-project |
| `verify/` | `{projectDir}/.claude/cat/verify/` | `{configDir}/projects/{encoded}/{sessionId}/cat/verify/` | Per-session |
| `e2e-config-test/` | `{projectDir}/.claude/cat/e2e-config-test/` | `{configDir}/projects/{encoded}/{sessionId}/cat/e2e-config-test/` | Per-session |

Where:
- `{configDir}` = `CLAUDE_CONFIG_DIR` (e.g., `/home/node/.config/claude`)
- `{encoded}` = `CLAUDE_PROJECT_DIR` with `/` and `.` replaced by `-`
- `{sessionId}` = `CLAUDE_SESSION_ID`

## Files to Modify

### Java source (path resolution)
- `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java` — Add `getProjectCatDir()` and
  `getSessionCatDir()` methods
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java` — Update lock dir resolution
- `client/src/main/java/io/github/cowwoc/cat/hooks/WorktreeLock.java` — Update lock scan path
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockLockManipulation.java` — Update lock path patterns
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java` — Update locks/worktrees paths
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java` — Update worktree path in messages
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java` — Update lock/worktree
  paths
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java` — Update worktree path in messages
- `client/src/main/java/io/github/cowwoc/cat/hooks/SubagentStartHook.java` — Update worktree path in messages
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetSubagentStatusOutput.java` — Update worktree monitoring

### Java tests
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforcePluginFileIsolationTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RemindGitSquashTest.java`

### Plugin skills
- `plugin/skills/work-prepare/first-use.md` — Update locks and worktree paths
- `plugin/skills/work-with-issue/first-use.md` — Update lock and verify paths
- `plugin/skills/delegate/first-use.md` — Update lock and worktree paths
- `plugin/skills/cleanup/first-use.md` — Update lock and worktree scan paths
- `plugin/skills/safe-rm/first-use.md` — Update worktree path examples
- `plugin/skills/validate-git-safety/first-use.md` — Update worktree path checks
- `plugin/skills/merge-subagent/first-use.md` — Update worktree path

### Plugin concepts/agents
- `plugin/concepts/git-operations.md` — Update worktree path examples
- `plugin/concepts/merge-and-cleanup.md` — Update worktree path examples
- `plugin/concepts/agent-architecture.md` — Update worktree path reference
- `plugin/agents/work-verify.md` — Update verify output paths

### Configuration
- `.gitignore` — Remove `.claude/cat/locks/`, `.claude/cat/worktrees/` entries (no longer in repo)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1: Add path resolution methods
- Add `getProjectCatDir()` to `JvmScope.java` — returns `{configDir}/projects/{encoded}/cat/`
- Add `getSessionCatDir()` to `JvmScope.java` — returns `{configDir}/projects/{encoded}/{sessionId}/cat/`
- Add encoding method that applies `replace("/", "-").replace(".", "-")` to `CLAUDE_PROJECT_DIR`
- Write tests for encoding edge cases (paths with dots, nested paths, root paths)
  - Files: `JvmScope.java`, new test class

### Wave 2: Update Java source to use new paths
- Replace all `projectDir.resolve(".claude/cat/locks")` with `getProjectCatDir().resolve("locks")`
- Replace all `projectDir.resolve(".claude/cat/worktrees")` with `getProjectCatDir().resolve("worktrees")`
- Replace all verify path references with `getSessionCatDir().resolve("verify")`
- Update all test classes to use new path resolution
  - Files: All Java source and test files listed above

### Wave 3: Update plugin skills and docs
- Update all hardcoded `.claude/cat/locks/`, `.claude/cat/worktrees/`, `.claude/cat/verify/` references in skill
  files, concept docs, and agent docs
- Skills must use runtime-resolved paths (e.g., from JvmScope or environment variables) rather than hardcoded strings
  - Files: All plugin files listed above

### Wave 4: Update gitignore and cleanup
- Remove `.claude/cat/locks/` and `.claude/cat/worktrees/` from `.gitignore`
- Remove `.claude/cat/verify/` and `.claude/cat/e2e-config-test/` directories from the repo if they exist
  - Files: `.gitignore`

## Post-conditions
- [ ] No references to `.claude/cat/locks/`, `.claude/cat/worktrees/`, `.claude/cat/verify/`, or
  `.claude/cat/e2e-config-test/` remain in plugin/ or client/src/ (except planning artifacts)
- [ ] `JvmScope.getProjectCatDir()` correctly resolves for `/workspace` → `-workspace`
- [ ] `JvmScope.getSessionCatDir()` correctly resolves with session ID
- [ ] All existing tests pass with updated paths
- [ ] `mvn -f client/pom.xml test` exits 0
- [ ] E2E: Lock files are created under `~/.config/claude/projects/{encoded}/cat/locks/` during `/cat:work`
- [ ] E2E: Worktrees are created under `~/.config/claude/projects/{encoded}/cat/worktrees/` during `/cat:work`

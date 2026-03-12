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
| `worktrees/` | `{projectDir}/.cat/worktrees/` | `{configDir}/projects/{encoded}/cat/worktrees/` | Cross-session, per-project |
| `locks/` | `{projectDir}/.cat/locks/` | `{configDir}/projects/{encoded}/cat/locks/` | Cross-session, per-project |
| `verify/` | `{projectDir}/.cat/verify/` | `{configDir}/projects/{encoded}/{sessionId}/cat/verify/` | Per-session |
| `e2e-config-test/` | `{projectDir}/.cat/e2e-config-test/` | `{configDir}/projects/{encoded}/{sessionId}/cat/e2e-config-test/` | Per-session |

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
- `.gitignore` — Remove `.cat/locks/`, `.cat/worktrees/` entries (no longer in repo)

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
- Replace all `projectDir.resolve(".cat/locks")` with `getProjectCatDir().resolve("locks")`
- Replace all `projectDir.resolve(".cat/worktrees")` with `getProjectCatDir().resolve("worktrees")`
- Replace all verify path references with `getSessionCatDir().resolve("verify")`
- Update all test classes to use new path resolution
  - Files: All Java source and test files listed above

### Wave 3: Update plugin skills and docs
- Update all hardcoded `.cat/locks/`, `.cat/worktrees/`, `.cat/verify/` references in skill
  files, concept docs, and agent docs
- Skills must use runtime-resolved paths (e.g., from JvmScope or environment variables) rather than hardcoded strings
  - Files: All plugin files listed above

### Wave 4: Update gitignore and cleanup
- Remove `.cat/locks/` and `.cat/worktrees/` from `.gitignore`
- Remove `.cat/verify/` and `.cat/e2e-config-test/` directories from the repo if they exist
  - Files: `.gitignore`

### Wave 5: Fix remaining old-path usages in core lock/worktree classes (iteration 1)

#### Step 5.1: Fix `IssueLock.java` — lock directory resolution
- In `IssueLock(JvmScope, Clock)` constructor, replace the `catDir.resolve("locks")` assignment with
  `scope.getProjectCatDir().resolve("locks")`.
- Remove the `catDir` local variable and the `Files.isDirectory(catDir)` guard if it is only used to build
  `lockDir`; keep or relocate the CAT-project validation check if it is needed for another purpose.
- Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java`

#### Step 5.2: Fix `WorkPrepare.java` — `findLockedIssues()` lock directory
- In `findLockedIssues(Path projectDir)`, replace
  `projectDir.resolve(".claude").resolve("cat").resolve("locks")` with
  `scope.getProjectCatDir().resolve("locks")`.
- Remove the `projectDir` parameter if `scope` is already available at the call site and no other use of
  `projectDir` remains in the method.
- Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

#### Step 5.3: Fix `WorkPrepare.java` — `createWorktree()` worktree directory
- In `createWorktree(Path projectDir, String issueBranch)`, replace
  `projectDir.resolve(".claude").resolve("cat").resolve("worktrees").resolve(issueBranch)` with
  `scope.getProjectCatDir().resolve("worktrees").resolve(issueBranch)`.
- Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

#### Step 5.4: Fix `IssueDiscovery.java` — `getWorktreePath()` worktree directory
- In `getWorktreePath(String issueId)`, replace
  `projectDir.resolve(".claude").resolve("cat").resolve("worktrees").resolve(issueId)` with a call that uses
  `scope.getProjectCatDir().resolve("worktrees").resolve(issueId)`.
- This requires either storing `scope` as a field (preferred) or passing the worktrees path at construction
  time.
- Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`

#### Step 5.5: Fix `RestoreWorktreeOnResume.java` — lock directory resolution
- In the `handle()` method, replace
  `projectDir.resolve(".claude").resolve("cat").resolve("locks")` with
  `scope.getProjectCatDir().resolve("locks")`.
- Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreWorktreeOnResume.java`

#### Step 5.6: Update `RestoreWorktreeOnResumeTest.java` — test lock directory
- Replace all occurrences of `tempDir.resolve(".claude").resolve("cat").resolve("locks")` with the new
  external path `configDir.resolve("projects").resolve(encodedProjectDir).resolve("cat").resolve("locks")`,
  using a `TestJvmScope` configured to point to the temp directories.
- Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/RestoreWorktreeOnResumeTest.java`

#### Step 5.7: Run all tests and confirm exit 0
- Run `mvn -f client/pom.xml test` and confirm BUILD SUCCESS with zero failures.

## Post-conditions
- [ ] No references to `.cat/locks/`, `.cat/worktrees/`, `.cat/verify/`, or
  `.cat/e2e-config-test/` remain in plugin/ or client/src/ (except planning artifacts)
- [ ] `JvmScope.getProjectCatDir()` correctly resolves for `/workspace` → `-workspace`
- [ ] `JvmScope.getSessionCatDir()` correctly resolves with session ID
- [ ] All existing tests pass with updated paths
- [ ] `mvn -f client/pom.xml test` exits 0
- [ ] E2E: Lock files are created under `~/.config/claude/projects/{encoded}/cat/locks/` during `/cat:work`
- [ ] E2E: Worktrees are created under `~/.config/claude/projects/{encoded}/cat/worktrees/` during `/cat:work`

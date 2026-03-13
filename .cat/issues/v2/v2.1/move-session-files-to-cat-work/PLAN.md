# Plan: Move Session Files to .cat/work/

## Current State

All session-specific CAT runtime files (locks, worktrees, verify output) are stored outside the project workspace
under `${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat/` (for cross-session files) and
`${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/cat/` (for session-scoped files).
`cat-env.sh` defines `LOCKS_DIR` and `WORKTREES_DIR` to point at these external locations.

## Target State

All session-specific CAT runtime files are stored inside the project workspace under `.cat/work/`, with session-ID
subdirectories used where concurrent session safety requires it. `.cat/.gitignore` excludes `work/`. A migration
script relocates any existing locks and worktrees. Java's `getProjectCatDir()` is updated to return the new path.

## Parent Requirements

None (infrastructure/tech debt refactor).

## Research Findings

### Current Path Definitions

**`plugin/scripts/cat-env.sh`** (the central path-definition file):
- `ENCODED_PROJECT_DIR=$(echo "${CLAUDE_PROJECT_DIR}" | tr '/.' '-')`
- `PROJECT_CAT_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat"` — cross-session external storage
- `LOCKS_DIR="${PROJECT_CAT_DIR}/locks"` → resolves to `~/.config/claude/projects/-workspace/cat/locks/`
- `WORKTREES_DIR="${PROJECT_CAT_DIR}/worktrees"` → resolves to `~/.config/claude/projects/-workspace/cat/worktrees/`

**VERIFY_DIR** (session-scoped, defined inline in agent/skill files, NOT in cat-env.sh):
- Current path: `"${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/cat/verify"`
- Used in `plugin/agents/work-verify.md` (lines 28–32, 44, 50) and `plugin/skills/work-confirm-agent/first-use.md`
  (lines 135–139, 151, 157)
- VERIFY_DIR is constructed inline — there is no shell variable for it in cat-env.sh

### Files That Reference LOCKS_DIR / WORKTREES_DIR / PROJECT_CAT_DIR

These reference the shell variables exported by `cat-env.sh`:
- `plugin/skills/merge-subagent-agent/first-use.md` — uses `${WORKTREES_DIR}`
- `plugin/skills/decompose-issue-agent/first-use.md` — uses `${WORKTREES_DIR}`
- `plugin/skills/work-implement-agent/first-use.md` — references worktree path format
  (`${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat/worktrees/...`)
- `plugin/skills/cleanup/first-use.md` — uses `${LOCKS_DIR}`, shows worktree path format in JSON
- `plugin/skills/collect-results-agent/first-use.md` — uses `${WORKTREES_DIR}`
- `plugin/skills/work-prepare-agent/first-use.md` — uses `${LOCKS_DIR}`, shows `${PROJECT_CAT_DIR}/worktrees/` in
  output contract example and Step 4 code
- `plugin/scripts/cat-env.sh` — defines all three

### Files That Reference VERIFY_DIR (inline path, not from cat-env.sh)

- `plugin/agents/work-verify.md` — constructs VERIFY_DIR inline (6 occurrences)
- `plugin/skills/work-confirm-agent/first-use.md` — constructs VERIFY_DIR inline (5 occurrences)

### Java Source References (via `scope.getProjectCatDir()`)

The Java `getProjectCatDir()` method returns `${claudeConfigDir}/projects/${encodedProjectDir}/cat/`. All callers
resolve subdirectories relative to it:

| File | Usage |
|------|-------|
| `util/WorkPrepare.java` | `.resolve("locks")`, `.resolve("worktrees").resolve(issueBranch)` |
| `util/IssueDiscovery.java` | `.resolve("worktrees").resolve(issueId)` |
| `util/StatuslineCommand.java` | `.resolve("locks")` |
| `util/IssueLock.java` | `.resolve("locks")` (at line 112, constructor) |
| `util/RecordLearning.java` | passed as `scope.getProjectCatDir()` argument |
| `session/CheckDataMigration.java` | `.resolve("backups/...")` |
| `session/CheckUpdateAvailable.java` | `.resolve("cache/update-check")` |
| `session/RestoreWorktreeOnResume.java` | `.resolve("locks")`, `isValidWorktreePath(..., scope.getProjectCatDir())` |
| `bash/BlockUnsafeRemoval.java` | `.resolve("locks")` (2 occurrences) |
| `bash/WarnMainWorkspaceCommit.java` | passes to `WorktreeLock.findIssueIdForSession(...)`, `.resolve("worktrees")` |
| `bash/BlockMainRebase.java` | `.resolve("worktrees")`, passed to `WorktreeContext.forSession(...)` |
| `bash/BlockWorktreeIsolationViolation.java` | passed to `WorktreeContext.forSession(...)` |
| `task/EnforceCommitBeforeSubagentSpawn.java` | passed to `WorktreeContext.forSession(...)` |
| `tool/post/SetPendingAgentResult.java` | passed to `WorktreeContext.forSession(...)` |
| `write/EnforceWorktreePathIsolation.java` | passed to `WorktreeContext.forSession(...)`, `.resolve("locks")`, `.resolve("worktrees")` |
| `write/WarnBaseBranchEdit.java` | `.resolve("worktrees").resolve("issue-name")` (in an error message example) |
| `hooks/SessionEndHook.java` | `.resolve("locks")` (3 occurrences) |
| `hooks/WorktreeLock.java` | static `findIssueIdForSession(Path projectCatDir, ...)` |
| `hooks/PreCompactHook.java` | `scope.getSessionCatDir()` for `session.cwd` |
| `hooks/JvmScope.java` | interface declaration of `getProjectCatDir()` and `getSessionCatDir()` |
| `hooks/AbstractJvmScope.java` | concrete implementation; returns `sessionBasePath.resolve("cat")` |

`getSessionCatDir()` returns `${claudeConfigDir}/projects/${encodedProjectDir}/${sessionId}/cat/`. The only current
usage is `RestoreCwdAfterCompaction.java` for `session.cwd` file. This is session-scoped and would also move to
`.cat/work/sessions/${CLAUDE_SESSION_ID}/`.

### Migration Numbering

Current migrations: 1.0.8, 1.0.9, 1.0.10, 2.0, 2.1, 2.2. The next migration is **2.3**.

### .cat/.gitignore Current State

File contains only: `cat-config.local.json`. It does NOT yet have `work/` excluded.

### Impact Notes

The issue `relocate-claude-cat-to-cat` (v2.1) is OPEN at 0% but the bulk directory move was already applied in
commit 2cc97072. Both issues touch overlapping skill files. This issue is independent of that historical context:
path updates here are for external→internal relocation, not .claude/cat→.cat.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Existing worktrees and lock files at the old path will stop being found. Migration script
  handles this. No behavioral change for users once migration runs.
- **Mitigation:** Migration script with idempotency checks; extensive test coverage of `IssueLock` and `IssueDiscovery`;
  E2E verification via `/cat:work` smoke test.

## Files to Modify

### Shell / Script Files
- `plugin/scripts/cat-env.sh` — redefine `LOCKS_DIR`, `WORKTREES_DIR`; remove `ENCODED_PROJECT_DIR` and
  `PROJECT_CAT_DIR` if no longer needed by any caller
- `plugin/agents/work-verify.md` — update inline VERIFY_DIR path (6 occurrences)
- `plugin/skills/work-confirm-agent/first-use.md` — update inline VERIFY_DIR path (5 occurrences)
- `plugin/skills/work-prepare-agent/first-use.md` — update `${LOCKS_DIR}` check, output contract example path,
  Step 4 `WORKTREE_PATH` definition
- `plugin/skills/work-implement-agent/first-use.md` — update worktree_path example in Arguments Format table
- `plugin/skills/cleanup/first-use.md` — update `${LOCKS_DIR}` usage in Step 6, worktree path in Step 4 JSON
- `plugin/skills/collect-results-agent/first-use.md` — update `${WORKTREES_DIR}` usage
- `plugin/skills/merge-subagent-agent/first-use.md` — update `${WORKTREES_DIR}` usage, path in example
- `plugin/skills/decompose-issue-agent/first-use.md` — update `${WORKTREES_DIR}` usage

### Java Source Files
- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java` — update `getProjectCatDir()` to return
  `${claudeProjectDir}/.cat/work` and update `getSessionCatDir()` to return
  `${claudeProjectDir}/.cat/work/sessions/${sessionId}`; update Javadoc
- `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java` — update Javadoc for `getProjectCatDir()` and
  `getSessionCatDir()`

### Migration / Config Files
- `plugin/migrations/2.3.sh` — new migration script to relocate existing locks and worktrees from external storage
- `plugin/migrations/registry.json` — add entry for version `2.3`
- `.cat/.gitignore` — add `work/` entry

## Pre-conditions

- [ ] 2.1-fix-work-with-issue-path-validation is closed
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1 — Update cat-env.sh and Java path definitions

- Update `plugin/scripts/cat-env.sh`:
  - Remove `ENCODED_PROJECT_DIR`, `PROJECT_CAT_DIR` variable definitions (or retain `ENCODED_PROJECT_DIR` only if any
    remaining callers still need it after the VERIFY_DIR change — see Wave 2)
  - Change `LOCKS_DIR` to `"${CLAUDE_PROJECT_DIR}/.cat/work/locks"`
  - Change `WORKTREES_DIR` to `"${CLAUDE_PROJECT_DIR}/.cat/work/worktrees"`
  - Remove the `CLAUDE_CONFIG_DIR` validation block if it is only required for the old path construction. Keep it if
    any remaining reference still needs it.
  - Update the header comment to reflect the new variables provided
  - Files: `plugin/scripts/cat-env.sh`

- Update Java path definitions in `AbstractJvmScope.java`:
  - Change `getProjectCatDir()` body from `getSessionBasePath().resolve("cat")` to
    `getClaudeProjectDir().resolve(".cat").resolve("work")`
  - Change `getSessionCatDir()` body from `getSessionDirectory().resolve("cat")` to
    `getClaudeProjectDir().resolve(".cat").resolve("work").resolve("sessions").resolve(getClaudeSessionId())`
  - Update Javadoc for both methods: update path examples and descriptions. `getProjectCatDir()` now stores
    cross-session files at `{claudeProjectDir}/.cat/work/`. `getSessionCatDir()` stores session-scoped files at
    `{claudeProjectDir}/.cat/work/sessions/{sessionId}/`.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`

- Update `JvmScope.java` Javadoc for `getProjectCatDir()` and `getSessionCatDir()` to match new paths.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

- Add `work/` to `.cat/.gitignore`:
  - Append `work/` as a new line
  - Files: `.cat/.gitignore`

### Wave 2 — Update VERIFY_DIR in agent and skill files

- Update `plugin/agents/work-verify.md`:
  - Replace both occurrences of:
    ```
    VERIFY_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/cat/verify"
    ```
    with:
    ```
    VERIFY_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}"
    ```
  - Update all `${VERIFY_DIR}` string references (they remain unchanged as they reference the variable, not the path)
  - Files: `plugin/agents/work-verify.md`

- Update `plugin/skills/work-confirm-agent/first-use.md`:
  - Replace both occurrences of:
    ```
    VERIFY_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/cat/verify"
    ```
    with:
    ```
    VERIFY_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}"
    ```
  - Files: `plugin/skills/work-confirm-agent/first-use.md`

### Wave 3 — Update remaining skill file path references

- Update `plugin/skills/work-prepare-agent/first-use.md`:
  - In the output contract JSON example, update `"worktree_path"` field to show:
    `"${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/2.1-issue-name"`
  - In Step 4 code block, update `WORKTREE_PATH` assignment to:
    `WORKTREE_PATH="${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/${ISSUE_BRANCH}"`
  - The `source "${CLAUDE_PLUGIN_ROOT}/scripts/cat-env.sh"` calls remain as-is (cat-env.sh now sets the new paths)
  - Files: `plugin/skills/work-prepare-agent/first-use.md`

- Update `plugin/skills/work-implement-agent/first-use.md`:
  - In the Arguments Format table, update the `worktree_path` example from
    `${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat/worktrees/2.1-issue-name` to
    `${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/2.1-issue-name`
  - Files: `plugin/skills/work-implement-agent/first-use.md`

- Update `plugin/skills/cleanup/first-use.md`:
  - In Step 4 JSON payload (`"worktrees_to_remove"` array), update the example path from
    `"${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat/worktrees/2.1-issue-name"` to
    `"${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/2.1-issue-name"`
  - Files: `plugin/skills/cleanup/first-use.md`

- Update `plugin/skills/collect-results-agent/first-use.md`:
  - In Clean Merge examples, update `${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat/worktrees/...` to
    `${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/...`
  - Files: `plugin/skills/collect-results-agent/first-use.md`

- Update `plugin/skills/merge-subagent-agent/first-use.md`:
  - In examples that hardcode the worktree path, update from
    `${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat/worktrees/...` to
    `${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/...`
  - Files: `plugin/skills/merge-subagent-agent/first-use.md`

- Update `plugin/skills/decompose-issue-agent/first-use.md`:
  - Update `${WORKTREES_DIR}` usage — the shell variable reference is fine (cat-env.sh now provides the right value).
    No inline hardcoded paths in this file need changing beyond shell variable usage.
  - Files: `plugin/skills/decompose-issue-agent/first-use.md`

### Wave 4 — Migration script and registry

- Create `plugin/migrations/2.3.sh`:
  - Standard license header (shell script format per `license-header.md`)
  - Script description: "Migration 2.3: Move locks and worktrees from external storage to .cat/work/"
  - Source `"${SCRIPT_DIR}/lib/utils.sh"` for `log_migration`, `log_error`, `log_success`, `set_last_migrated_version`
  - **Phase 1: Idempotency check** — if `.cat/work/locks/` and `.cat/work/worktrees/` both already exist and the old
    external dirs do NOT exist, call `set_last_migrated_version "2.3"` and exit 0
  - **Phase 2: Derive old paths** — compute `ENCODED_PROJECT_DIR` and `OLD_PROJECT_CAT_DIR`:
    ```bash
    ENCODED_PROJECT_DIR=$(echo "${CLAUDE_PROJECT_DIR}" | tr '/.' '-')
    OLD_PROJECT_CAT_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat"
    OLD_SESSION_CAT_BASE="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}"
    ```
  - **Phase 3: Check for active worktrees in old location** — if `${OLD_PROJECT_CAT_DIR}/worktrees/` exists and has
    subdirectories, abort with instructions to run `/cat:cleanup` first
  - **Phase 4: Check for active lock files in old location** — if `${OLD_PROJECT_CAT_DIR}/locks/` exists and has
    `.lock` files, abort with instructions to run `/cat:cleanup` first
  - **Phase 5: Move locks** — if `${OLD_PROJECT_CAT_DIR}/locks/` exists, move its contents to
    `.cat/work/locks/` (using `mkdir -p` and `find ... -exec mv`), then remove the old dir
  - **Phase 6: Move worktrees** — if `${OLD_PROJECT_CAT_DIR}/worktrees/` exists, move its contents to
    `.cat/work/worktrees/`, then remove the old dir
  - **Phase 7: Migrate session-scoped verify dirs** — scan
    `${OLD_SESSION_CAT_BASE}/*/cat/verify/` (glob pattern for session UUID dirs) and move each to
    `.cat/work/verify/${SESSION_ID}/`. Use `find "${OLD_SESSION_CAT_BASE}" -mindepth 3 -maxdepth 3 -name "verify"
    -type d` to locate them.
  - **Phase 8: Update worktree path references in STATE.md files** — scan `.cat/issues/**/STATE.md` for
    `WORKTREE_PATH` lines that reference the old external path and update them to the new `.cat/work/worktrees/` path
    using `sed -i`
  - **Final:** Call `set_last_migrated_version "2.3"` and `log_success`
  - Files: `plugin/migrations/2.3.sh`

- Update `plugin/migrations/registry.json`:
  - Add a new entry after the `2.2` entry:
    ```json
    {
      "version": "2.3",
      "script": "2.3.sh",
      "description": "Move locks and worktrees from external ~/.config/claude/... storage to .cat/work/ within project workspace"
    }
    ```
  - Files: `plugin/migrations/registry.json`

- Run tests to verify no regressions:
  ```bash
  mvn -f client/pom.xml test
  ```

## Post-conditions

- [ ] `plugin/scripts/cat-env.sh` defines `LOCKS_DIR` as `${CLAUDE_PROJECT_DIR}/.cat/work/locks` and `WORKTREES_DIR`
  as `${CLAUDE_PROJECT_DIR}/.cat/work/worktrees`
- [ ] `AbstractJvmScope.getProjectCatDir()` returns `${claudeProjectDir}/.cat/work`
- [ ] `AbstractJvmScope.getSessionCatDir()` returns `${claudeProjectDir}/.cat/work/sessions/${sessionId}`
- [ ] `plugin/agents/work-verify.md` and `plugin/skills/work-confirm-agent/first-use.md` define `VERIFY_DIR` as
  `${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}`
- [ ] `.cat/.gitignore` contains `work/` entry so runtime files are not committed
- [ ] `plugin/migrations/2.3.sh` exists and is idempotent (running twice is a no-op)
- [ ] `plugin/migrations/registry.json` has version `2.3` entry pointing to `2.3.sh`
- [ ] All Maven tests pass (`mvn -f client/pom.xml test` exits 0) with no regressions
- [ ] E2E: Run `/cat:work` on a test issue; confirm the worktree appears under
  `${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/`, lock file under `${CLAUDE_PROJECT_DIR}/.cat/work/locks/`, and
  verify output under `${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}/`
- [ ] Multi-instance safety: verify output uses `${CLAUDE_SESSION_ID}` subdirectory, preventing concurrent session
  collisions

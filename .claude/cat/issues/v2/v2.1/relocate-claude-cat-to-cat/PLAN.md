# Plan: relocate-claude-cat-to-cat

## Goal
Move the CAT data directory from `.claude/cat/` to `.cat/` at the workspace root. This consolidates all
CAT-managed planning files, configuration, issues, rules, and retrospectives under a top-level `.cat/`
directory rather than nested inside Claude's own `.claude/` directory. All references throughout hooks,
scripts, skills (Markdown), Java source, tests, and project-level documentation must be updated, and a
migration script must handle existing installations.

## Parent Requirements
None

## Impact Notes
- `add-gitignore-for-claude-cat` (v2.1): **Already closed** — .gitignore was added to `.claude/cat/`.
  The new migration script must create an equivalent `.gitignore` in `.cat/` and the init skill must
  also target `.cat/`.
- `move-worktrees-under-cat` (v2.1): **Already closed** — Worktrees were moved to
  `.claude/cat/worktrees/`. After this issue, worktrees will be at `.cat/worktrees/`. The migration
  script must handle moving any existing `.claude/cat/worktrees/` content.

## Approaches

### A: Introduce a Java constant for the CAT directory path (chosen)
- **Risk:** MEDIUM
- **Scope:** ~80 files (12 Java production, 20+ Java test, 6 shell scripts, 52 Markdown skill/concept files)
- **Description:** Add a `CAT_DIR_NAME = ".cat"` constant in `Config.java` (or a new `CatPaths.java`
  utility class) so all Java code resolves the CAT directory via a single source of truth. Shell scripts
  reference `".cat"` as a literal string. Markdown/skill files use the literal `.cat` path.

### B: Environment variable injection
- **Risk:** HIGH
- **Scope:** Same files plus env-var infrastructure
- **Description:** Introduce a `CLAUDE_CAT_DIR` environment variable and have all consumers read it.
  Rejected: adds runtime complexity, requires variable to be set in every process context, and doesn't
  address in-process Java path resolution cleanly.

**Chosen approach: A.** Lowest risk, most auditable, and aligns with the existing pattern of
`projectDir.resolve(".claude").resolve("cat")` becoming `projectDir.resolve(".cat")`.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:**
  1. Large blast radius — 80+ files touched
  2. Active worktrees at `.claude/cat/worktrees/` will break if migration not run
  3. Lock files under `.claude/cat/locks/` must be absent before migration
  4. `utils.sh` in migrations assumes `.claude/cat` path; new migration must update VERSION file location
  5. `CLAUDE.md` and `.claude/rules/` files reference `.claude/cat` — must be updated
  6. Java test utilities use `.claude/cat` as file_path strings in test inputs
- **Mitigation:**
  1. Pre-condition: no active worktrees during migration
  2. Migration script moves directory atomically (`mv .claude/cat .cat`)
  3. Static scan post-conditions confirms zero residual `.claude/cat` references
  4. Comprehensive test updates ensure all test infrastructure uses `.cat`

## Files to Modify

### Java Production Files (~12 files)
All files currently resolving `.claude/cat` or `.claude" + "cat"`:

- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`
  — Change `projectDir.resolve(".claude").resolve("cat")` → `projectDir.resolve(".cat")`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueCreator.java`
  — Change `".claude/cat/issues/..."` → `".cat/issues/..."`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
  — Change `projectDir.resolve(".claude").resolve("cat")` → `projectDir.resolve(".cat")`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
  — Change `.claude/cat` directory check → `.cat`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RootCauseAnalyzer.java`
  — Change retrospectivesDir resolve to `.cat/retrospectives`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
  — Three occurrences: catDir/catDir check/issuesDir resolve
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
  — Change `.claude/cat/retrospectives` → `.cat/retrospectives`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RetrospectiveMigrator.java`
  — Change `.claude/cat/retrospectives` → `.cat/retrospectives`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectMainAgentRules.java`
  — Change `projectRulesDir` resolution from `.claude/cat/rules` → `.cat/rules`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSubAgentRules.java`
  — Change projectRulesDir resolution from `.claude/cat/rules` → `.cat/rules`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckRetrospectiveDue.java`
  — Change retroDir resolve from `.claude/cat/retrospectives` → `.cat/retrospectives`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckDataMigration.java`
  — Change catDir resolve from `.claude/cat` → `.cat`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java`
  — Any `.claude/cat` references
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java`
  — Any `.claude/cat` references
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetInitOutput.java`
  — Any `.claude/cat` references
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
  — catDir resolve from `.claude/cat` → `.cat`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java`
  — Any `.claude/cat` references
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java`
  — Change `.claude/cat/cat-config.json` → `.cat/cat-config.json`
- `client/src/main/java/io/github/cowwoc/cat/hooks/edit/EnforceWorkflowCompletion.java`
  — Change regex pattern from `\.claude/cat/` → `\.cat/`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/VerifyStateInCommit.java`
  — Any `.claude/cat` references
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java`
  — Any `.claude/cat` references
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java`
  — Any `.claude/cat` references
- `client/src/main/java/io/github/cowwoc/cat/hooks/licensing/LicenseValidator.java`
  — Any `.claude/cat` references

### Java Test Files (~20 files)
All test files using `.claude/cat` as file path string literals in test inputs:

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StateSchemaValidatorTest.java`
  — ~285 occurrences of `.claude/cat/issues/...` in `file_path` strings → change to `.cat/issues/...`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestUtils.java`
  — Any `.claude/cat` path constants
- All other test files referencing `.claude/cat` (full list from grep: InjectMainAgentRulesTest,
  WarnBaseBranchEditTest, CheckDataMigrationTest, RulesDiscoveryTest, IssueDiscoveryTest,
  HookEntryPointTest, GetDiffOutputTest, InjectSubAgentRulesTest, WarnApprovalWithoutRenderDiffTest,
  RecordLearningTest, GetRetrospectiveOutputTest, GetStakeholderOutputTest, WorkPrepareTest,
  MergeAndCleanupTest, HandlerOutputTest, GetAddOutputPlanningDataTest, GetInitOutputTest,
  SubagentStartHookTest)

### Shell Scripts (6 files)
- `plugin/migrations/2.1.sh`
  — All `find .claude/cat/...`, `config_file=".claude/cat/..."` references — these are historical and
  correct for the 2.1 migration (they run on `.claude/cat` data) — **do NOT change** 2.1.sh; it must
  still migrate from the old `.claude/cat` path (because the new migration moves the directory itself)
- `plugin/migrations/lib/utils.sh`
  — `backup_cat_dir`, `get_last_migrated_version`, `set_last_migrated_version` functions reference
  `.claude/cat` — these must be updated to use `.cat` for the new migration script; however since the
  new migration is version 2.2, it needs to handle the case where `.claude/cat` still exists and
  `.cat` does not yet.
  **Approach:** Add a new helper `resolve_cat_dir` that returns `.cat` if it exists, else `.claude/cat`
  (for pre-migration compatibility). Update `backup_cat_dir`, `get_last_migrated_version`,
  `set_last_migrated_version` to use `resolve_cat_dir`.
- `plugin/migrations/1.0.10.sh`, `plugin/migrations/1.0.9.sh`, `plugin/migrations/1.0.8.sh`,
  `plugin/migrations/2.0.sh`
  — These run on older installations that still have `.claude/cat`. **Do NOT change** them.
- `plugin/scripts/cat-env.sh`
  — Does NOT reference `.claude/cat` (it defines `PROJECT_CAT_DIR` under external Claude storage).
  No change needed for the primary path. However, review if any line references the project-side `.cat`
  directory and update accordingly.

### New Migration Script
- `plugin/migrations/2.2.sh` (NEW)
  — Moves `.claude/cat` → `.cat` at workspace root, handles all sub-paths
- `plugin/migrations/registry.json`
  — Add entry for version `2.2` pointing to `2.2.sh`

### Markdown/Skill Files (~52 files in plugin/skills and plugin/concepts)
All `first-use.md` and concept files referencing `.claude/cat`. Full list from grep:

**plugin/concepts/** (12 files):
- `merge-and-cleanup.md`, `version-completion.md`, `version-paths.md`, `rules-audience.md`,
  `duplicate-issue.md`, `issue-resolution.md`, `hierarchy.md`, `worktree-isolation.md`,
  `commit-types.md`, `documentation-style.md`, `build-verification.md`,
  `subagent-context-minimization.md`

**plugin/skills/** (25+ first-use.md files):
- `optimize-execution/first-use.md`, `remove/first-use.md`, `work-review-agent/first-use.md`,
  `decompose-issue-agent/first-use.md`, `research/first-use.md`, `work-implement-agent/first-use.md`,
  `init/first-use.md`, `git-squash-agent/first-use.md`, `work-merge-agent/first-use.md`,
  `stakeholder-review-agent/first-use.md`, `cleanup/first-use.md`, `feedback/first-use.md`,
  `config/first-use.md`, `retrospective/first-use.md`, `add/first-use.md`, `help/first-use.md`,
  `work-with-issue-agent/first-use.md`, `work-confirm-agent/first-use.md`,
  `collect-results-agent/first-use.md`, `git-merge-linear-agent/first-use.md`,
  `rebase-impact-agent/first-use.md`, `work/first-use.md`, `git-commit-agent/first-use.md`,
  `work-prepare-agent/first-use.md`, `git-rebase-agent/first-use.md`,
  and any additional skill files from the full grep list

### Project Configuration & Rules Files
- `CLAUDE.md`
  — Update commit type table entry `.claude/cat/issues/` → `.cat/issues/`
  — Update M267 worktree path example from `.claude/cat/worktrees/` → `.cat/worktrees/`
- `.claude/rules/hooks.md`
  — Update reference to `.claude/cat/rules/hooks.md` → `.cat/rules/hooks.md`
- `.claude/rules/common.md`
  — Update examples referencing `.claude/cat/cat-config.json` → `.cat/cat-config.json`
  — Update worktree path examples
- `.claude/rules/license-header.md`
  — Update exemption comment referencing `.claude/cat/` → `.cat/`
- `.claude/cat/rules/convention-locations.md` (if it references `.claude/cat`)
  — Update path references

### The .claude/cat directory itself
- Move all files from `.claude/cat/` to `.cat/` as part of the migration (done by migration script,
  not during development)
- Update `.gitignore` in root if `.cat/` needs gitignore patterns

## Pre-conditions
- [ ] All dependent issues are closed
- [ ] **No active worktrees exist** under `.claude/cat/worktrees/` before running the migration script.
  The migration script checks for active worktrees and aborts if any are found.

## Sub-Agent Waves

### Wave 1: Java production code updates
- Update all Java production source files in `client/src/main/` to resolve `.cat` instead of
  `.claude/cat` or `.claude" + "cat"`
  - Use global search-and-replace patterns:
    - `resolve(".claude").resolve("cat")` → `resolve(".cat")`
    - `resolve(".claude/cat")` → `resolve(".cat")`
    - `".claude/cat/` → `".cat/`
    - `".claude/cat"` → `".cat"`
  - Files: all 20+ Java main source files listed in Files to Modify → Java Production Files
  - Verify: `grep -r '\.claude/cat' client/src/main/ --include='*.java'` returns zero matches

### Wave 2: Java test file updates
- Update all Java test source files in `client/src/test/` to use `.cat` path strings
  - Replace all `".claude/cat/` occurrences with `".cat/`
  - Replace `".claude/cat"` with `".cat"` (standalone without trailing slash)
  - Files: all ~20 test files listed in Files to Modify → Java Test Files
  - Verify: `grep -r '\.claude/cat' client/src/test/ --include='*.java'` returns zero matches
- Run full test suite: `mvn -f client/pom.xml test`

### Wave 3: Shell script and migration script
- Update `plugin/migrations/lib/utils.sh`:
  - Add `resolve_cat_dir()` function: returns `.cat` if exists, else `.claude/cat` (for backwards
    compatibility during migration execution)
  - Update `backup_cat_dir`, `get_last_migrated_version`, `set_last_migrated_version` to call
    `resolve_cat_dir` to get the active CAT dir path
  - Files: `plugin/migrations/lib/utils.sh`
- Create `plugin/migrations/2.2.sh` (new migration script):
  - Header with license and change description
  - Import `lib/utils.sh`
  - Phase 1: Pre-condition check — abort if `.cat` already exists (idempotency)
  - Phase 2: Check for active worktrees under `.claude/cat/worktrees/` — abort with error if found
  - Phase 3: Check for active lock files under `.claude/cat/locks/` — abort with error if found
  - Phase 4: Move the directory: `mv .claude/cat .cat`
  - Phase 5: Update VERSION file (already at `.cat/VERSION` after move)
  - Phase 6: Update any internal path references stored in data files (e.g., STATE.md fields referencing
    old `.claude/cat` issue paths, if any)
  - Phase 7: Create `.cat/.gitignore` if not already present with standard patterns
  - Use `set_last_migrated_version "2.2"` to record completion
  - Files: `plugin/migrations/2.2.sh`
- Update `plugin/migrations/registry.json`:
  - Add entry: `{ "version": "2.2", "script": "2.2.sh", "description": "Move .claude/cat to .cat at workspace root" }`
  - Files: `plugin/migrations/registry.json`

### Wave 4: Markdown skill and concept file updates
- Update all Markdown files in `plugin/skills/` and `plugin/concepts/` to use `.cat/` paths
  - Replace `.claude/cat/` with `.cat/` throughout
  - Replace `.claude/cat` (without trailing slash where used as exact dir reference) with `.cat`
  - Do NOT change references inside `plugin/migrations/` — historical migration scripts must
    retain their old paths
  - Files: 52 Markdown files listed in Files to Modify → Markdown/Skill Files
  - Verify: `grep -r '\.claude/cat' plugin/skills/ plugin/concepts/ --include='*.md'` returns zero
    matches (excluding plugin/migrations/)
- Update project configuration/rules files:
  - `CLAUDE.md`: update commit table and M267 example
  - `.claude/rules/hooks.md`: update `.claude/cat/rules/hooks.md` reference
  - `.claude/rules/common.md`: update config path examples
  - `.claude/rules/license-header.md`: update exemption comment
  - `.claude/cat/rules/convention-locations.md`: update if needed
  - Files: listed above

### Wave 5: Move .claude/cat to .cat in the actual project data
- In the workspace (not a migration script execution), move the actual data directory:
  `mv /workspace/.claude/cat /workspace/.cat`
- Update `.gitignore` at workspace root if needed to ensure `.cat/.gitignore` patterns apply
- Verify the move: `ls /workspace/.cat/issues/v2/v2.1/`
- Verify git status shows the rename
- Run `git add -A` to stage the move as a rename
- Commit: `feature: move .claude/cat to .cat`
- Verify: `grep -r '\.claude/cat' /workspace/plugin/ /workspace/client/src/ --include='*.java' --include='*.sh' --include='*.md'` (excluding migrations/) returns zero matches
- Run final test suite: `mvn -f client/pom.xml test`
- Update STATE.md: set status to closed, progress 100%

## Post-conditions
- [ ] Functionality works: `.cat/` directory exists at workspace root; `.claude/cat/` no longer exists
- [ ] Tests passing: `mvn -f client/pom.xml test` exits 0 with all tests passing
- [ ] No regressions: All CAT features (init, work, status, config, cleanup) function correctly after
  the move
- [ ] E2E verification: Spawn a subagent that reads config from `.cat/cat-config.json` and confirm it
  correctly locates the file
- [ ] Migration script present: `plugin/migrations/2.2.sh` exists with idempotent pre-condition check
  and active-worktree/lock-file abort guard
- [ ] Migration script idempotency: Running `plugin/migrations/2.2.sh` twice produces same result as
  running once (second run detects `.cat` exists and skips with no-op)
- [ ] Pre-condition documented: This PLAN.md and the 2.2.sh migration script both document that active
  worktrees must not exist before migration
- [ ] No residual hardcoded references: `grep -r '\.claude/cat' plugin/skills/ plugin/concepts/
  client/src/main/ client/src/test/ CLAUDE.md .claude/rules/` (excluding migrations/) returns zero
  matches
- [ ] Lock files and session data accounted for: Migration script aborts if active lock files are found
  under `.claude/cat/locks/` before moving the directory

# Plan

## Goal

Rename all stale `/cat:X` references to `/cat:X-agent` across the codebase. Many skill commands were
renamed to add the `-agent` suffix, but references in plugin skills, client Java source, tests, docs,
and config files still use the old names.

The following 27 renames are required (old → new):

| Old | New |
|-----|-----|
| `/cat:add` | `/cat:add-agent` |
| `/cat:collect-results` | `/cat:collect-results-agent` |
| `/cat:decompose-issue` | `/cat:decompose-issue-agent` |
| `/cat:empirical-test` | `/cat:empirical-test-agent` |
| `/cat:get-diff` | `/cat:get-diff-agent` |
| `/cat:get-history` | `/cat:get-history-agent` |
| `/cat:get-output` | `/cat:get-output-agent` |
| `/cat:git-merge-linear` | `/cat:git-merge-linear-agent` |
| `/cat:git-rebase` | `/cat:git-rebase-agent` |
| `/cat:git-rewrite-history` | `/cat:git-rewrite-history-agent` |
| `/cat:git-squash` | `/cat:git-squash-agent` |
| `/cat:instruction-builder` | `/cat:instruction-builder-agent` |
| `/cat:load-skill` | `/cat:load-skill-agent` |
| `/cat:plan-builder` | `/cat:plan-builder-agent` |
| `/cat:recover-from-drift` | `/cat:recover-from-drift-agent` |
| `/cat:register-hook` | `/cat:register-hook-agent` |
| `/cat:remove` | `/cat:remove-agent` |
| `/cat:research` | `/cat:research-agent` |
| `/cat:retrospective` | `/cat:retrospective-agent` |
| `/cat:safe-rm` | `/cat:safe-rm-agent` |
| `/cat:stakeholder-review` | `/cat:stakeholder-review-agent` |
| `/cat:tdd-implementation` | `/cat:tdd-implementation-agent` |
| `/cat:token-report` | `/cat:token-report-agent` |
| `/cat:verify-implementation` | `/cat:verify-implementation-agent` |
| `/cat:work` | `/cat:work-agent` |
| `/cat:work-complete` | `/cat:work-complete-agent` |
| `/cat:work-with-issue` | `/cat:work-with-issue-agent` |

**Exclusions:** Do not rename references that are intentionally using the old name as a detection
example in tests (e.g., test strings that verify the `/cat:[a-z]` pattern detects slash commands).
Also skip historical changelog entries and `.cat/issues/` files (closed issues are historical records).

## Pre-conditions

(none)

## Post-conditions

- [ ] All stale `/cat:X` references updated to `/cat:X-agent` in plugin skills (including SKILL.md and first-use.md files), client Java source, docs, tests, CLAUDE.md, AGENTS.md, and `.claude/rules/` files
- [ ] Historical changelog entries and closed issue files left unchanged
- [ ] Tests pass (`mvn -f client/pom.xml verify -e`)
- [ ] No behavioral regressions

## Research Findings

Comprehensive grep across the worktree (excluding `.cat/`, `.git/`, `target/`, `changelog.md`) found
35 files with stale references. All changes are trivial string substitutions in comments, Javadoc,
error messages, and description fields.

**Note:** `RemindGitSquash.java` production code already uses `/cat:git-squash-agent`. Test assertions
in `RemindGitSquashTest.java` still check for `/cat:git-squash` (old substring), which passes only
because the new string contains the old one. These tests should be updated to check the full new name.

## Jobs

### Job 1

Rename stale `/cat:X` references in plugin files, config files, and docs.

**Commit 1 — `refactor:` (plugin + config mix → plugin type wins)**

Files to update (each file and its exact changes):

- `CLAUDE.md`: replace `/cat:work` with `/cat:work-agent` (lines 35, 45, 71 — 3 occurrences)
- `AGENTS.md`: replace `/cat:work` with `/cat:work-agent` (lines 35, 45, 71 — 3 occurrences)
- `.claude/rules/hooks.md`: replace `/cat:work` with `/cat:work-agent` (lines 203, 213 — 2 occurrences)
- `.claude/rules/error-handling.md`: replace `/cat:work` with `/cat:work-agent` (line 51 — 1 occurrence)
- `plugin/agents/work-merge.md`: replace `/cat:work` with `/cat:work-agent` in the `description:` frontmatter field (line 3)
- `plugin/agents/work-squash.md`: replace `/cat:work` with `/cat:work-agent` in the `description:` frontmatter field (line 3)
- `plugin/migrations/2.0.sh`: replace `/cat:research` with `/cat:research-agent` (lines 15, 41, 53, 151 — 4 occurrences)
- `plugin/skills/init/first-use.md`: replace `/cat:work` with `/cat:work-agent` (lines 950, 1022, 1023 — 3 occurrences)
- `plugin/skills/learn/first-use.md`: replace `/cat:work` with `/cat:work-agent` (line 43 — 1 occurrence)
- `plugin/skills/learn/phase-investigate.md`: replace `/cat:get-history` with `/cat:get-history-agent` (line 69 — 1 occurrence; this is a standalone skill invocation line)
- `plugin/skills/learn/phase-investigate-subagent.md`: replace `/cat:work` with `/cat:work-agent` in the `description:` frontmatter field (line 70)
- `plugin/skills/add-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (line 23 — in "do NOT mention internal slash commands" example list)
- `plugin/skills/collect-results-agent/first-use.md`:
  - Line 46: replace `/cat:work` with `/cat:work-agent`
  - Line 245: replace `/cat:decompose-issue` with `/cat:decompose-issue-agent`
- `plugin/skills/decompose-issue-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (line 171)
- `plugin/skills/get-subagent-status-agent/SKILL.md`: replace `/cat:token-report` with `/cat:token-report-agent` (line 5 — description field)
- `plugin/skills/instruction-builder-agent/skill-conventions.md`:
  - Line 40: replace `/cat:work` with `/cat:work-agent` (in `Internal` description example string)
  - Line 1171: replace `/cat:work` with `/cat:work-agent`
- `plugin/skills/instruction-builder-agent/workflow-output.md`: replace `/cat:work` with `/cat:work-agent` (line 92 — section heading "Phase Batching for /cat:work")
- `plugin/skills/merge-subagent-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (line 23)
- `plugin/skills/plan-builder-agent/SKILL.md`: replace `/cat:work` with `/cat:work-agent` (line 4 — description frontmatter field)
- `plugin/skills/plan-builder-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (lines 31, 49, 51 — 3 occurrences)
- `plugin/skills/rebase-impact-agent/SKILL.md`: replace `/cat:work` with `/cat:work-agent` (line 3 — description frontmatter field)
- `plugin/skills/rebase-impact-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (lines 229, 254, 256, 270, 278, 287 — 6 occurrences)
- `plugin/skills/stakeholder-review-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (lines 58, 192, 325, 521 — 4 occurrences)
- `plugin/skills/verify-implementation-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (lines 14, 19, 24, 220, 223 — 5 occurrences)
- `plugin/skills/work-complete-agent/SKILL.md`: replace `/cat:work` with `/cat:work-agent` (line 2 — description frontmatter field)
- `plugin/skills/work-confirm-agent/SKILL.md`:
  - Line 2: replace `/cat:work-with-issue` with `/cat:work-with-issue-agent` (description frontmatter field)
  - Line 8: replace `/cat:work` with `/cat:work-agent`
- `plugin/skills/work-confirm-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (line 8)
- `plugin/skills/work-implement-agent/SKILL.md`: replace `/cat:work-with-issue` with `/cat:work-with-issue-agent` (line 2 — description frontmatter field)
- `plugin/skills/work-implement-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (lines 10, 180, 213 — 3 occurrences)
- `plugin/skills/work-implement-agent/instruction-test/trust-gate-e2e-check.md`: replace `/cat:work` with `/cat:work-agent` (lines 9, 15 — 2 occurrences)
- `plugin/skills/work-merge-agent/SKILL.md`: replace `/cat:work-with-issue` with `/cat:work-with-issue-agent` (line 3 — description frontmatter field)
- `plugin/skills/work-merge-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (lines 8, 95, 333, 339, 348 — 5 occurrences)
- `plugin/skills/work-prepare-agent/SKILL.md`: replace `/cat:work` with `/cat:work-agent` (line 2 — description frontmatter field)
- `plugin/skills/work-prepare-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (lines 10, 166, 202, 483, 673 — 5 occurrences)
- `plugin/skills/work-review-agent/SKILL.md`: replace `/cat:work-with-issue` with `/cat:work-with-issue-agent` (line 2 — description frontmatter field)
- `plugin/skills/work-review-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (lines 8, 227, 279 — 3 occurrences)
- `plugin/skills/work-with-issue-agent/SKILL.md`: replace `/cat:work` with `/cat:work-agent` (line 3 — description frontmatter field)
- `plugin/skills/work-with-issue-agent/first-use.md`: replace `/cat:work` with `/cat:work-agent` (lines 8, 11, 38, 52, 83, 178 — 6 occurrences)

Commit message: `refactor: rename stale /cat:X references to /cat:X-agent in plugin and config files`

**Commit 2 — `docs:`**

- `docs/demo-task-management.html`: 
  - Replace `<code>/cat:work</code>` with `<code>/cat:work-agent</code>` (line 331)
  - Replace `/cat:add` with `/cat:add-agent` in the span element (line 370)
  - Replace `/cat:work` with `/cat:work-agent` in the span element (line 380)

Commit message: `docs: rename stale /cat:X references to /cat:X-agent in demo HTML`

### Job 2

Rename stale `/cat:X` references in client Java source files and test files. Run the full test suite.
Update index.json to close the issue in the same commit as implementation.

**Base path prefix for all files:** `client/src/main/java/io/github/cowwoc/cat/claude/hook/`

**Source files to update:**

- `ask/WarnApprovalWithoutRenderDiff.java`:
  - Line 28: `/cat:work` → `/cat:work-agent` (Javadoc comment)
  - Line 114: `1. Invoke: /cat:get-diff` → `1. Invoke: /cat:get-diff-agent` (error message text)

- `ask/WarnUnsquashedApproval.java`:
  - Line 23: `/cat:work` → `/cat:work-agent` (Javadoc comment)

- `bash/BlockMergeCommits.java`:
  - Lines 55, 57, 71, 73: `/cat:git-merge-linear` → `/cat:git-merge-linear-agent` (error message text — 4 occurrences)

- `bash/ValidateGitFilterBranch.java`:
  - Line 57: `/cat:git-rewrite-history` → `/cat:git-rewrite-history-agent` (error message text)

- `bash/ValidateGitOperations.java`:
  - Line 65: `/cat:git-rebase` → `/cat:git-rebase-agent` (error message text)

- `bash/WarnMainWorkspaceCommit.java`:
  - Line 121: `{@code /cat:work}` → `{@code /cat:work-agent}` (Javadoc)

- `failure/DetectRepeatedFailures.java`:
  - Line 31: `{@code /cat:recover-from-drift}` → `{@code /cat:recover-from-drift-agent}` (Javadoc)
  - Line 195: `` `/cat:recover-from-drift` `` → `` `/cat:recover-from-drift-agent` `` (injected message text)

- `skills/GetCheckpointOutput.java`:
  - Line 25: `{@code /cat:work}` → `{@code /cat:work-agent}` (Javadoc)

- `skills/GetOutput.java`:
  - Line 30: `{@code /cat:get-output}` → `{@code /cat:get-output-agent}` (Javadoc)

- `skills/GetResearchOutput.java`:
  - Line 16: `/cat:research` → `/cat:research-agent` (Javadoc)

- `skills/GetRetrospectiveOutput.java`:
  - Line 47: `/cat:retrospective` → `/cat:retrospective-agent` (Javadoc)

- `skills/GetStakeholderConcernBox.java`:
  - Line 26: `/cat:stakeholder-review` → `/cat:stakeholder-review-agent` (Javadoc)

- `skills/GetStakeholderReviewBox.java`:
  - Line 27: `/cat:stakeholder-review` → `/cat:stakeholder-review-agent` (Javadoc)

- `skills/GetStakeholderSelectionBox.java`:
  - Line 27: `/cat:stakeholder-review` → `/cat:stakeholder-review-agent` (Javadoc)

- `skills/GetTokenReportOutput.java`:
  - Line 34: `/cat:token-report` → `/cat:token-report-agent` (Javadoc)

- `skills/GetWorkOutput.java`:
  - Line 20: `/cat:work` → `/cat:work-agent` (Javadoc)

- `util/MergeAndCleanup.java`:
  - Line 34: `/cat:work` → `/cat:work-agent` (Javadoc)

- `util/WorkPrepare.java`:
  - Line 48: `/cat:work` → `/cat:work-agent` (Javadoc)
  - Line 304: `/cat:decompose-issue` → `/cat:decompose-issue-agent` (JSON suggestion field)
  - Line 670: `/cat:decompose-issue` → `/cat:decompose-issue-agent` (JSON suggestion field)

- `write/EnforcePluginFileIsolation.java`:
  - Line 24: `{@code /cat:work}` → `{@code /cat:work-agent}` (Javadoc)
  - Line 96: `{@code /cat:work}` → `{@code /cat:work-agent}` (Javadoc)

**Test path prefix:** `client/src/test/java/io/github/cowwoc/cat/client/test/`

**Test files to update:**

- `DetectRepeatedFailuresTest.java`:
  - Lines 83, 201: `.contains("/cat:recover-from-drift")` → `.contains("/cat:recover-from-drift-agent")` (2 occurrences)

- `EnforcePluginFileIsolationTest.java`:
  - Line 169: `Worktrees created by /cat:work` → `Worktrees created by /cat:work-agent` (Javadoc comment)

- `RemindGitSquashTest.java`:
  - Lines 59, 91, 142: `.contains("/cat:git-squash")` → `.contains("/cat:git-squash-agent")` (3 occurrences)

- `WorkPrepareTest.java`:
  - Lines 2723, 2770: `simulates /cat:work invocation` → `simulates /cat:work-agent invocation` (2 comment occurrences)

**After all edits:** Run `mvn -f client/pom.xml verify -e` and confirm exit code 0.

**Commit — `refactor:` including index.json:**

- Stage all modified client Java files
- Stage `${ISSUE_PATH}/index.json` (set `status: "closed"`, `progress: 100`)
- Commit message: `refactor: rename stale /cat:X references to /cat:X-agent in client Java source and tests`

## Success Criteria

- All stale files updated with the correct new skill names (plugin skills, config, docs, Java source)
- `mvn -f client/pom.xml verify -e` exits 0
- `changelog.md` is unchanged
- No file under `.cat/issues/` is modified

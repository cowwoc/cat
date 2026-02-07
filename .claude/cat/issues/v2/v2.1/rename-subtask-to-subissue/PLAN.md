# Plan: rename-subtask-to-subissue

## Current State
The codebase uses "sub-task", "subtask", and "sub_task" inconsistently. Since CAT uses "issue" as its primary
work item term, child issues should be called "sub-issue" for consistency with GitHub and Linear conventions.

## Target State
All references to sub-task/subtask/sub_task renamed to sub-issue/subissue/sub_issue across plugin skills,
scripts, tests, and issue tracking files. "Wave" terminology (execution batching) remains unchanged.

## Satisfies
None - terminology consistency

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Function and variable renames in `get-available-issues.sh` must match test expectations
- **Mitigation:** Run full test suite after rename

## Files to Modify

### Category 1: Plugin Scripts (functional code - variable/function renames)
- `plugin/scripts/get-available-issues.sh` - Rename function `all_subtasks_closed` to `all_subissues_closed`,
  rename all variables (`subtask_names`, `subtask_state`, `subtask_status`, `subtask`) to use `subissue_*`,
  update all comments

### Category 2: Plugin Skills (documentation/instructions)
- `plugin/skills/decompose-issue/SKILL.md` - Rename all "subtask"/"sub_task" references to "sub-issue"/"sub_issue"
- `plugin/skills/delegate/SKILL.md` - Rename "subtasks" reference
- `plugin/skills/work-merge/SKILL.md` - Rename subtask references in merge logic
- `plugin/skills/add/SKILL.md` - Rename subtask references in parent-check logic
- `plugin/skills/work/phase-prepare.md` - Rename "subtasks" in decompose option
- `plugin/skills/work/phase-review.md` - Rename "subtask context" reference
- `plugin/skills/work/anti-patterns.md` - Rename "subtask" in anti-pattern table

### Category 3: Test Files
- `tests/scripts/get-available-issues.bats` - Rename all test descriptions, directory names, comments,
  and variable references from subtask to subissue

### Category 4: Active v2.1 Issue Files
- `.claude/cat/issues/v2/v2.1/migrate-python-to-java/STATE.md` - Rename "Sub-task" headers and "sub-tasks" text
- `.claude/cat/issues/v2/v2.1/rename-task-to-issue/STATE.md` - Rename "Sub-task" headers
- `.claude/cat/issues/v2/v2.1/compress-md-files/STATE.md` - Rename "Sub-task" and "sub-tasks"
- `.claude/cat/issues/v2/v2.1/compress-skills-md/STATE.md` - Rename "subtasks"
- `.claude/cat/issues/v2/v2.1/fix-decomposed-parent-completion/PLAN.md` - Rename all subtask references
- `.claude/cat/issues/v2/v2.1/skip-decomposed-parents-in-discovery/PLAN.md` - Rename subtask references
- `.claude/cat/issues/v2/v2.1/skip-decomposed-parents-in-discovery/STATE.md` - Rename subtask references
- `.claude/cat/issues/v2/v2.1/migrate-to-silent-preprocessing/PLAN.md` - Rename section header
- `.claude/cat/issues/v2/v2.1/migrate-to-silent-preprocessing/STATE.md` - Rename section header and table
- `.claude/cat/issues/v2/v2.1/compress-commands-md/PLAN.md` - Rename "subtask" reference
- `.claude/cat/issues/v2/v2.1/compress-lang-md/PLAN.md` - Rename "subtask" reference
- `.claude/cat/issues/v2/v2.1/compress-skills-batch-[1-5]/PLAN.md` - Rename "subtask" references
- `.claude/cat/issues/v2/v2.1/compress-stakeholders-md/PLAN.md` - Rename "subtask" reference
- `.claude/cat/issues/v2/v2.1/compress-concepts-md/PLAN.md` - Rename "subtask" reference
- `.claude/cat/issues/v2/v2.1/compress-templates-md/PLAN.md` - Rename "subtask" reference

### Category 5: Historical/Changelog Files
- `CHANGELOG.md` - Rename "subtasks"/"sub-task-based" references
- `.claude/cat/issues/v1/v1.0/CHANGELOG.md` - Rename "subtasks" reference
- `.claude/cat/issues/v1/v1.2/CHANGELOG.md` - Rename "subtasks"/"sub-task-based"
- `.claude/cat/issues/v1/v1.2/auto-decomposition-parallel-execution/PLAN.md` - Rename
- `.claude/cat/issues/v2/v2.0/STATE.md` - Rename "subtasks" reference
- `.claude/cat/issues/v2/v2.0/suggest-decomposition-at-soft-limit/PLAN.md` - Rename "sub-tasks"

### Category 6: Retrospective Files (JSON)
- `.claude/cat/retrospectives/index.json` - Rename subtask references
- `.claude/cat/retrospectives/mistakes-2026-01.json` - Rename subtask references
- `.claude/cat/retrospectives/mistakes-2026-02.json` - Rename subtask references
- `.claude/cat/retrospectives/retrospectives-2026-01.json` - Rename subtask reference

### NOT modified (leave "wave" as-is)
- `.claude/cat/issues/v1/v1.10/parallel-execution-naming/PLAN.md` - Historical closed issue describing the old rename

## Rename Mapping

| Old Pattern | New Pattern | Context |
|-------------|-------------|--------|
| `subtask` | `sub-issue` | Display text, prose |
| `Subtask` | `Sub-issue` | Capitalized display text |
| `subtasks` | `sub-issues` | Plural display text |
| `Subtasks` | `Sub-issues` | Capitalized plural |
| `sub-task` | `sub-issue` | Hyphenated display text |
| `Sub-task` | `Sub-issue` | Capitalized hyphenated |
| `sub-tasks` | `sub-issues` | Plural hyphenated |
| `Sub-tasks` | `Sub-issues` | Capitalized plural hyphenated |
| `sub_task_1` | `sub_issue_1` | Variable/identifier (underscore) |
| `sub_task_2` | `sub_issue_2` | Variable/identifier (underscore) |
| `all_subtasks_closed` | `all_subissues_closed` | Bash function name |
| `subtask_names` | `subissue_names` | Bash variable |
| `subtask_state` | `subissue_state` | Bash variable |
| `subtask_status` | `subissue_status` | Bash variable |
| `$subtask` | `$subissue` | Bash loop variable |
| `subtask-1` | `subissue-1` | Test directory names |
| `subtask-2` | `subissue-2` | Test directory names |
| `subtask-done` | `subissue-done` | Test directory names |
| `subtask-pending` | `subissue-pending` | Test directory names |
| `subtask-completion` | `subissue-completion` | JSON tag |

## Acceptance Criteria
- [ ] Behavior unchanged - all tests pass
- [ ] No remaining references to sub-task/subtask/sub_task (except v1.10 historical issue)
- [ ] Code quality maintained

## Execution Steps
1. **Step 1:** Rename function and variables in `plugin/scripts/get-available-issues.sh`
   - Files: `plugin/scripts/get-available-issues.sh`
   - Apply all identifier renames from mapping table
2. **Step 2:** Rename references in test file
   - Files: `tests/scripts/get-available-issues.bats`
   - Apply all renames including test directory names
3. **Step 3:** Rename references in plugin skill files
   - Files: All 8 skill files listed in Category 2
4. **Step 4:** Rename references in active v2.1 issue files
   - Files: All Category 4 files
5. **Step 5:** Rename references in historical and changelog files
   - Files: All Category 5 files
6. **Step 6:** Rename references in retrospective JSON files
   - Files: All Category 6 files
7. **Step 7:** Run test suite to verify no regressions
   - Command: `python3 /workspace/run_tests.py`

## Success Criteria
- [ ] All tests pass after rename
- [ ] Grep for subtask/sub-task/sub_task returns only the v1.10 historical issue

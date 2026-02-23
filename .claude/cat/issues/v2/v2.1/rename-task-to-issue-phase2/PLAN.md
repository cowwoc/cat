# Plan: rename-task-to-issue-phase2

## Goal
Complete the terminology rename from "task" to "issue" across all remaining plugin files - bash scripts, skill MD files,
JSON config, and Java code. The previous rename (rename-task-to-issue-terminology) only covered surface-level MD
documentation but missed variable names, status codes, JSON fields, step names, script names, and comments.

## Type
Refactoring

## Satisfies
None (terminology consistency)

## Scope Rules

**DO rename** (CAT project terminology):
- Variable names: `TASK_NAME` → `ISSUE_NAME`, `BLOCKED_TASKS` → `BLOCKED_ISSUES`, `TASK_COMMITS` → `ISSUE_COMMITS`, etc.
- Status codes: `NO_TASKS` → `NO_ISSUES`
- JSON fields: `blocked_tasks` → `blocked_issues`, `locked_tasks` → `locked_issues`, `task_metrics` → `issue_metrics`
- Step names in skills: `task_gather_intent` → `issue_gather_intent`, etc.
- Script names: `get-next-task-box` → `get-next-issue-box`
- Config entries in `tiers.json`: `basic-task-management` → `basic-issue-management`, etc.
- Comments referring to CAT tasks as "tasks" when they mean "issues"
- `task_creation_info` → `issue_creation_info` in learn skill

**DO NOT rename** (Claude Code built-in tools):
- `Task` tool references (Claude's subagent spawner)
- `TaskList`, `TaskCreate`, `TaskUpdate`, `TaskGet`, `TaskOutput` tool references
- `TaskStop` tool references
- Generic English usage of "task" meaning "a piece of work" (e.g., "your task is to...")
- `subagent_type` values or Claude tool parameter names

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Script name renames (`get-next-task-box`) require updating all references. JSON field renames in
  `work-prepare` output require updating all consumers. Must distinguish CAT "task" from Claude "Task tool".
- **Mitigation:** Search for all consumers of renamed scripts/fields before renaming. Test after changes.

## Files to Modify

### Bash Scripts
- `plugin/scripts/check-existing-work.sh` - comment: "task branch" → "issue branch"
- `plugin/scripts/lib/version-utils.sh` - comment: "without task" → "without issue"
- `plugin/scripts/get-available-issues.sh` - comments and error messages: "task" → "issue", "decomposed parent task" →
  "decomposed parent issue", `decompose-task` → `decompose-issue`
- `plugin/scripts/test-harness/is-dependency-satisfied.sh` - error message: "parent task" → "parent issue"
- `plugin/hooks/session-start.sh` - comments: "session start tasks" → "session start operations"

### Java Client (binary names and Java source)
- `plugin/hooks/hooks.json` - references to `pre-task` binary
- `plugin/hooks/README.md` - `pre-task` / `PreTaskHook` / `get-next-task-box` / `GetNextTaskOutput`
- Java source: `PreTaskHook` → `PreIssueHook`, `GetNextTaskOutput` → `GetNextIssueOutput`
- Binary: `pre-task` → `pre-issue`, `get-next-task-box` → `get-next-issue-box`

### Skill MD Files
- `plugin/skills/work-prepare/first-use.md` - `NO_TASKS` → `NO_ISSUES`, `blocked_tasks` → `blocked_issues`,
  `locked_tasks` → `locked_issues`, `TASK_COMMITS` → `ISSUE_COMMITS`, `BLOCKED_TASKS` → `BLOCKED_ISSUES`,
  `LOCKED_TASKS` → `LOCKED_ISSUES`
- `plugin/skills/work/first-use.md` - `NO_TASKS` → `NO_ISSUES`, `blocked_tasks` → `blocked_issues`,
  `locked_tasks` → `locked_issues`, `skip compression tasks` → `skip compression issues`
- `plugin/skills/work-with-issue/first-use.md` - `TASK_GOAL` → `ISSUE_GOAL`, `task_metrics` → `issue_metrics`
- `plugin/skills/work-complete/first-use.md` - `get-next-task-box` → `get-next-issue-box`
- `plugin/skills/add/first-use.md` - all `TASK_*` variables → `ISSUE_*`, all `task_*` step names → `issue_*`
- `plugin/skills/remove/first-use.md` - all `TASK_*` variables → `ISSUE_*`, all `task_*` step names → `issue_*`,
  `TOTAL_TASKS` → `TOTAL_ISSUES`, `COMPLETED_TASKS` → `COMPLETED_ISSUES`
- `plugin/skills/decompose-issue/first-use.md` - `TASK_DIR` → `ISSUE_DIR`, `decompose_task` → `decompose_issue`
- `plugin/skills/stakeholder-review/first-use.md` - `TASK_PLAN` → `ISSUE_PLAN`, `TASK_TYPE` → `ISSUE_TYPE`,
  `TASK_TEXT` → `ISSUE_TEXT`
- `plugin/skills/git-squash/first-use.md` - `TASK_STATE` → `ISSUE_STATE`
- `plugin/skills/git-merge-linear/first-use.md` - `TASK_BRANCH` → `ISSUE_BRANCH`, `task_branch` → `issue_branch`
- `plugin/skills/init/first-use.md` - `first_task_guide` → `first_issue_guide`, `TASK_NAME` → `ISSUE_NAME`,
  `first_task_walkthrough` → `first_issue_walkthrough`, `first_task_created` → `first_issue_created`
- `plugin/skills/register-hook/first-use.md` - `TASK_NAME` → `ISSUE_NAME`
- `plugin/skills/learn/phase-prevent.md` - `task_creation_info` → `issue_creation_info`
- `plugin/skills/learn/first-use.md` - `task_creation_info` → `issue_creation_info`
- `plugin/skills/learn/RCA-AB-TEST.md` - "task decomposition" → "issue decomposition"
- `plugin/skills/learn/phase-analyze.md` - "task" in table entries → "issue" where referring to CAT issues
- `plugin/skills/learn/rca-methods.md` - "task decomposition" → "issue decomposition"
- `plugin/skills/learn/mistake-categories.md` - "finish task" → "finish issue"
- `plugin/concepts/work.md` - `NO_TASKS` → `NO_ISSUES`
- `plugin/concepts/silent-execution.md` - `get-next-task-box` → `get-next-issue-box`

### JSON Config
- `plugin/config/tiers.json` - `basic-task-management` → `basic-issue-management`,
  `parallel-task-execution` → `parallel-issue-execution`, `task-decomposition` → `issue-decomposition`
- `plugin/package.json` - "task orchestration" → "issue orchestration"

### Templates
- `plugin/templates/patch-plan.md` - "## Tasks" → "## Issues"
- `plugin/templates/minor-plan.md` - "## Tasks" → "## Issues", "All tasks complete" → "All issues complete"

### Migration Scripts (comments only, preserve behavior)
- `plugin/migrations/2.0.sh` - comment reference
- `plugin/migrations/2.1.sh` - comment references to "Exit Gate Tasks"

### Test Files
- `plugin/scripts/tests/test_work_prepare.py` - `test_filters_add_task_commits` → rename, `add task` in strings
- `plugin/scripts/tests/test_check_existing_work.py` - comments and branch names

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Rename Java classes and binaries:** Rename `PreTaskHook` → `PreIssueHook`, `GetNextTaskOutput` →
   `GetNextIssueOutput`, `pre-task` → `pre-issue`, `get-next-task-box` → `get-next-issue-box`. Update hooks.json and
   README.md references.
2. **Rename variables and status codes in skill MD files:** Apply all `TASK_*` → `ISSUE_*` variable renames, `NO_TASKS`
   → `NO_ISSUES`, JSON field renames across work-prepare, work, work-with-issue, work-complete, add, remove,
   decompose-issue, stakeholder-review, git-squash, git-merge-linear, init, register-hook, learn skills.
3. **Rename step names in add and remove skills:** All `task_*` step names → `issue_*`.
4. **Rename config entries:** Update tiers.json feature names and package.json description.
5. **Rename template headers:** Update patch-plan.md and minor-plan.md.
6. **Update bash script comments:** Fix remaining "task" references in comments across bash scripts.
7. **Update test files:** Rename test methods and string references.
8. **Run tests:** Verify all tests pass after changes.

## Post-conditions
- [ ] No CAT-specific "task" terminology remains in plugin files (excluding Claude built-in tool names and generic
  English usage)
- [ ] All references to renamed scripts (`get-next-issue-box`, `pre-issue`) are consistent
- [ ] All references to renamed JSON fields (`blocked_issues`, `locked_issues`, `NO_ISSUES`) are consistent
- [ ] All tests pass

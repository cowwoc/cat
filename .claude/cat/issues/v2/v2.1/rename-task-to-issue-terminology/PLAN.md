# Plan: rename-task-to-issue-terminology

## Current State
Plugin files still use "task" to refer to CAT project work items in ~12-15 files despite prior
rename issues (rename-task-to-issue, rename-task-in-skills, etc.) being closed. This primes agents
to use wrong terminology, confusing CAT issues (persistent work items) with Claude TaskList items
(within-session tracking).

## Target State
All plugin agent-facing text uses "issue" for CAT project work items. "Task" only appears when
referring to Claude's TaskList/TaskCreate/TaskUpdate tools or the Task tool (subagent spawning).

## Satisfies
None (terminology consistency)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — terminology only, no behavior change
- **Mitigation:** Grep before/after to verify no legitimate "task" references were changed

## Files to Modify

### Agents
- `plugin/agents/work-merge.md` — "task branch", "task locks" → "issue branch", "issue locks"
- `plugin/agents/work-execute.md` — "task PLAN.md", "task plans" → "issue PLAN.md", "issue plans"
- `plugin/agents/stakeholder-requirements.md` — "task claims", "task objective" → "issue claims", etc.
- `plugin/agents/README.md` — "task context" → "issue context"

### Concepts
- `plugin/concepts/work.md` — "Find task", "task complexity" → "Find issue", "issue complexity"
- `plugin/concepts/agent-architecture.md` — "CAT task" → "CAT issue"
- `plugin/concepts/merge-and-cleanup.md` — "task branch", "task branches" → "issue branch", etc.

### Skills
- `plugin/skills/add/SKILL.md` — "issue/task" → "issue"
- `plugin/skills/work/SKILL.md` — "issue or task" → "issue"
- `plugin/skills/work/first-use.md` — multiple "task" references (including M400 prevention additions)
- `plugin/skills/help/first-use.md` — ~10 "task" references in help text
- `plugin/skills/cleanup/first-use.md` — "task" in state change context
- `plugin/skills/delegate/SUBAGENT-PROMPT-CHECKLIST.md` — "task size" → "issue size"

### Exclusions (do NOT rename)
- `Task` tool references (subagent spawning)
- `TaskList`, `TaskCreate`, `TaskUpdate` tool references
- "Your task is to..." procedural instructions
- Tool/command names (e.g., `get-next-task-box`)
- Test data and test fixture strings

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Scan all files in `plugin/agents/`, `plugin/concepts/`, `plugin/skills/` for "task"
   used to mean CAT issue. Replace with "issue" while preserving legitimate uses.
   - Files: All files listed above
2. **Step 2:** Also update the M400 prevention text in `plugin/skills/work/first-use.md` — replace
   the terminology reminder with the actual fix (since the whole file is being renamed).
3. **Step 3:** Update the NO_TASKS guidance in `plugin/skills/work/first-use.md` so the agent
   determines which issues are available rather than directing the user to `/cat:status`.
4. **Step 4:** Run `grep -ri '\btask\b' plugin/agents/ plugin/concepts/ plugin/skills/` and verify
   remaining hits are all legitimate (Task tool, TaskList, procedural "your task").

## Post-conditions
- [ ] No agent-facing text in plugin/ uses "task" to mean CAT issue
- [ ] All references to Claude's Task/TaskList tools remain unchanged
- [ ] Procedural "your task is to..." phrases remain unchanged
- [ ] `mvn -f client/pom.xml test` passes (no regressions)

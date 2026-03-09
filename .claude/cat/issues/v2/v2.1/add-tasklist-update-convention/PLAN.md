# Plan: add-tasklist-update-convention

## Goal
Add a plugin convention to `plugin/rules/` that requires agents to update TaskList status after every
major CAT operation completes (e.g., /cat:work, /cat:add, /cat:cleanup). This prevents stale
tasks from accumulating across operations.

## Parent Requirements
None

## Approaches

### A: New standalone rule file
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Create `plugin/rules/tasklist-lifecycle.md` with the cleanup convention.

### B: Extend user-input-handling.md
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Add a "Post-Operation Cleanup" section to existing `user-input-handling.md`.

**Chosen: Approach A** — The new rule is about lifecycle/cleanup, not about input handling. A separate file
keeps single-responsibility and is easier to find. `user-input-handling.md` already covers the "add to TaskList"
case; this rule covers the complementary "update/clean TaskList" case.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Rule must be precise about what "major operation" means to avoid ambiguity
- **Mitigation:** Enumerate the specific operations by name

## Files to Modify
- `plugin/rules/tasklist-lifecycle.md` — CREATE new rule file

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Create `plugin/rules/tasklist-lifecycle.md` with the following content:

```markdown
---
mainAgent: true
subAgents: []
---
## TaskList Lifecycle After Major Operations

**MANDATORY**: After any major CAT operation completes, update the TaskList to reflect the current
project state. Major operations include: `/cat:work` (issue merge), `/cat:add` (issue creation),
`/cat:cleanup` (orphan removal), and any other skill that closes, merges, or significantly
advances an issue.

**What to do after a major operation:**
1. Mark tasks that were completed as part of the operation as `completed`
2. Delete tasks that are now stale or irrelevant (e.g., verification steps for an issue that just
   merged)
3. Keep tasks that represent work still in progress

**Pattern:**
- After `/cat:work` completes: delete all session tasks that tracked work for that issue
- After `/cat:add` creates an issue: no TaskList cleanup required
- After `/cat:cleanup` runs: delete any tasks created to investigate the abandoned work

**Skip TaskList update only when**: The operation made no changes to issue state (e.g., read-only
status check, failed preparation with no lock acquired).

**Common failure**: Completing a major operation and leaving stale "verify criterion X" or
"investigate Y" tasks in the list from the completed work.
```
  - Files: `plugin/rules/tasklist-lifecycle.md`
- Update `plugin/rules/user-input-handling.md` to add a cross-reference note after the TaskList section pointing to the new lifecycle rule
  - Files: `plugin/rules/user-input-handling.md`

## Post-conditions
- [ ] `plugin/rules/tasklist-lifecycle.md` exists with correct frontmatter (`mainAgent: true, subAgents: []`)
- [ ] Rule enumerates specific major operations: `/cat:work`, `/cat:add`, `/cat:cleanup`
- [ ] Rule defines "update" as: mark completed tasks completed, delete stale tasks
- [ ] `user-input-handling.md` cross-references the new lifecycle rule
- [ ] E2E: After completing a `/cat:work` session, agent cleans up TaskList items that tracked that issue's work

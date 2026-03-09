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
- For other skills: apply the `/cat:work` pattern when closing/merging an issue, or the
  `/cat:cleanup` pattern when removing orphaned work

**Skip TaskList update only when**: The operation made no changes to issue state (e.g., read-only
status check, failed preparation with no lock acquired).

**Common failure**: Completing a major operation and leaving stale "verify criterion X" or
"investigate Y" tasks in the list from the completed work.

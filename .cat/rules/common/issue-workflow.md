---
subAgents: []
---
# Issue Workflow vs Direct Implementation

When a user asks to update or modify `plugin/**` or `client/**` behavior, treat it as issue workflow work.
Do not directly read/analyze/edit source as an inline one-off implementation path.

Correct interpretation:
- Create an issue via `/cat:add-agent` capturing the requested change.
- Implementation happens via `/cat:work-agent` in worktree isolation.

Wrong interpretation:
- Direct source edits on `plugin/**` or `client/**` in the main workspace.
- Bypassing planning/review/merge workflow with ad-hoc edits.

Exception:
- If user explicitly requests a "quick fix" or "one-line change", still use minimal issue workflow
  (`/cat:add-agent` + `/cat:work-agent`) rather than direct main-workspace edits.

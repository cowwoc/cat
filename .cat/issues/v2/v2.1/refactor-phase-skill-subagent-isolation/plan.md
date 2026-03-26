# Plan

## Goal

Refactor the `/cat:work` phase orchestration so that `work-with-issue-agent` and all phase skills
(`work-implement-agent`, `work-confirm-agent`, `work-review-agent`, `work-merge-agent`) run as Task
subagents rather than being loaded via the Skill tool into the main agent context. Move inline
edge-case content to conditionally-loaded reference files. The result is a main agent that stays
under 20K tokens throughout a `/cat:work` run.

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior of `/cat:work` is unchanged
- [ ] `work-with-issue-agent` is spawned as a Task subagent by `work-agent`, not invoked via Skill tool
- [ ] All phase skills (implement, confirm, review, merge) run as isolated Task subagents spawned by `work-with-issue-agent`
- [ ] Inline edge-case content (error recovery tables, lengthy script templates, conflict resolution examples) moved to conditionally-loaded reference files
- [ ] Main agent context during a `/cat:work` run stays under 20K tokens (no phase skill content loads into main agent)
- [ ] All existing tests pass
- [ ] E2E: run `/cat:work` on a real issue and confirm the full phase workflow completes successfully

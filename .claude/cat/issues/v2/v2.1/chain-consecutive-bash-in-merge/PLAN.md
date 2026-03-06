# Plan: chain-consecutive-bash-in-merge

## Problem

The work-merge-agent skill's verification and cleanup steps issue individual Bash calls for
independent operations (e.g., `git log`, `git status`, `git diff --stat`, `git worktree list`).
These are issued one-at-a-time as separate tool calls despite having no dependencies on each other,
adding unnecessary round-trips and wall-clock time.

In the 2.1-handle-persisted-skill-output session, the merge subagent issued 8 consecutive Bash
calls, 5 of which could be chained.

## Satisfies

None — performance optimization

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — chaining independent read-only verification commands has no correctness risk
- **Mitigation:** Chain only commands with no output dependencies between them

## Files to Modify

- `plugin/skills/work-merge-agent/SKILL.md` — add explicit chaining guidance for verification steps;
  combine independent git verification commands with `&&` in step instructions

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Update merge verification steps in work-merge-agent to chain independent commands:
  `git log --oneline -1 && git status --porcelain` and
  `git worktree list && git branch --show-current && git log --oneline -3`
  - Files: `plugin/skills/work-merge-agent/SKILL.md`
- Add explicit note: "Chain independent verification commands with && in a single Bash call"
  - Files: `plugin/skills/work-merge-agent/SKILL.md`

## Post-conditions

- [ ] Merge skill verification steps combine independent commands into single Bash calls
- [ ] At least 3 previously-separate sequential Bash commands are combined into chained calls
- [ ] All existing tests pass

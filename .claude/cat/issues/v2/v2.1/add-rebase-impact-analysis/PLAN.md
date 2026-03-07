# Plan: add-rebase-impact-analysis

## Goal

After rebasing an issue branch onto the target branch, automatically analyze what changed between the old
and new fork points and determine whether those changes impact the current PLAN.md. Silently continue for
no-impact cases; auto-revise for unambiguous impacts; ask the user only when conflicting approaches require
a human decision.

## Parent Requirements

None

## Design

### Severity Levels

| Severity | Condition | Action |
|----------|-----------|--------|
| `NO_IMPACT` | No changed files overlap with PLAN.md dependencies | Continue silently |
| `LOW` | Overlapping files changed cosmetically (whitespace, comments, renames) | Continue silently |
| `MEDIUM` | Overlapping files changed in ways that affect PLAN.md, but revision is unambiguous | Auto-revise PLAN.md and implementation, continue |
| `HIGH` | Changes introduce conflicting requirements or multiple valid revision paths | Write proposal file, ask user for guidance |

### Context Isolation

The full analysis is written to `${WORKTREE_PATH}/.claude/cat/rebase-impact-analysis.md`.
Only a compact JSON summary is returned to the main agent:

```json
{
  "status": "NO_IMPACT|LOW|MEDIUM|HIGH",
  "severity": "NO_IMPACT|LOW|MEDIUM|HIGH",
  "summary": "one-line description of findings",
  "analysis_path": "${WORKTREE_PATH}/.claude/cat/rebase-impact-analysis.md"
}
```

### Severity Decision Rule

- **MEDIUM**: PLAN.md can be revised with zero judgment calls (mechanical update)
- **HIGH**: Revision requires choosing between conflicting approaches (human decision needed)

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** False-positive HIGH classifications would unnecessarily interrupt the user; false-negative MEDIUM
  classifications could auto-apply incorrect revisions
- **Mitigation:** Conservative severity assignment ��� when uncertain, classify as HIGH rather than MEDIUM

## Files to Modify

- `plugin/skills/work-with-issue-agent/first-use.md` ��� add post-rebase impact analysis step after rebase
- `plugin/skills/git-rebase-agent/first-use.md` (or new skill) ��� extend or create post-rebase analysis logic
- `plugin/agents/` or `plugin/skills/` ��� new `rebase-impact-agent` skill (analysis subagent)

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Create `rebase-impact-agent` skill:
  - Accepts: issue_path, worktree_path, old_fork_point, new_fork_point
  - Computes git diff between fork points
  - Reads PLAN.md to extract file dependencies
  - Determines severity using decision rule
  - Writes full analysis to `${WORKTREE_PATH}/.claude/cat/rebase-impact-analysis.md`
  - Returns compact JSON summary

### Wave 2

- Integrate into `/cat:work` rebase step:
  - After rebase completes, invoke `rebase-impact-agent`
  - Route based on severity: NO_IMPACT/LOW = continue; MEDIUM = invoke plan-builder-agent then continue;
    HIGH = write proposal, ask user
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

## Post-conditions

- [ ] Post-rebase impact analysis runs automatically after each rebase in /cat:work
- [ ] The analysis diffs between old and new fork points and compares changed files against PLAN.md dependencies
- [ ] NO_IMPACT and LOW severity results proceed silently without user interruption
- [ ] MEDIUM severity auto-revises PLAN.md and any partially-implemented code without user involvement
- [ ] HIGH severity writes revision proposal to file before presenting user with guidance choices
- [ ] Main agent context is not polluted ��� only compact JSON (status, severity, summary, analysis_path) returned
- [ ] Analysis details are written to `${WORKTREE_PATH}/.claude/cat/rebase-impact-analysis.md`
- [ ] Severity classification follows defined rule: MEDIUM = zero judgment calls needed; HIGH = conflicting approaches
- [ ] E2E: Rebase an issue branch where target branch changes conflict with PLAN.md assumptions; verify correct severity triggers correct behavior

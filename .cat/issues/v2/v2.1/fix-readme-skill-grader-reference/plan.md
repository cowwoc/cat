# Plan: fix-readme-skill-grader-reference

## Goal

Fix stale `skill-grader-agent` reference in `plugin/agents/README.md` — the agent was renamed to
`instruction-grader-agent` but the README still lists the old name.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — single-line rename in a README listing
- **Mitigation:** N/A

## Files to Modify

- `plugin/agents/README.md` — update line listing `skill-grader-agent.md` to `instruction-grader-agent.md`

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Update plugin/agents/README.md

Replace:
```
├── skill-grader-agent.md          # Internal subagent — grades assertions against test-case output, returns commit SHA
```
With:
```
├── instruction-grader-agent.md    # Internal subagent — grades assertions against test-case output, returns commit SHA
```

- Files: `plugin/agents/README.md`

## Post-conditions

- [ ] `plugin/agents/README.md` lists `instruction-grader-agent.md`, not `skill-grader-agent.md`
- [ ] `grep -r skill-grader-agent plugin/` returns no results

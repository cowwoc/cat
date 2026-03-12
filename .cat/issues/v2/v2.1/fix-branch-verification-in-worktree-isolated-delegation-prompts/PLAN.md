# Plan: fix-branch-verification-in-worktree-isolated-delegation-prompts

## Problem

When `work-with-issue-agent` delegates Wave tasks to subagents with `isolation: "worktree"`, the
delegation prompt instructs subagents to verify their git branch matches the issue branch name
(e.g., `2.1-preload-common-tools-at-session-start`). However, `isolation: "worktree"` creates NEW
branches with different names (e.g., `worktree-agent-ae59cc32`). This causes Wave subagents to
return BLOCKED unnecessarily.

## Root Cause

The delegation prompt template in `work-with-issue-agent/first-use.md` includes a mandatory first
action:

```
Before doing ANYTHING else, verify the branch:
git branch --show-current  # Must output: ${BRANCH}
If the branch does not match ${BRANCH}, STOP and return BLOCKED immediately.
```

This check is appropriate for subagents working directly on the issue branch, but incorrect for
worktree-isolated subagents which always run on a `worktree-agent-*` branch.

## Parent Requirements

None — internal tooling fix.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — removing an incorrect check cannot break correct behavior
- **Mitigation:** Verify waves complete successfully after the fix

## Files to Modify

- `plugin/skills/work-with-issue-agent/first-use.md` — remove the `## First Action (MANDATORY)`
  branch verification step from worktree-isolated subagent delegation prompts (both single-subagent
  and parallel execution sections)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Read `plugin/skills/work-with-issue-agent/first-use.md` and locate all delegation prompt blocks
  that include `## First Action (MANDATORY)` with branch verification instructions. Remove the branch
  verification instruction from every isolation: worktree delegation prompt. The verification block to
  remove reads: "Before doing ANYTHING else, verify the branch: `git branch --show-current` # Must
  output: ${BRANCH}. If the branch does not match ${BRANCH}, STOP and return BLOCKED immediately."
  Update STATE.md to status: closed, progress: 100%.
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`,
    `.cat/issues/v2/v2.1/fix-branch-verification-in-worktree-isolated-delegation-prompts/STATE.md`

## Post-conditions

- [ ] No delegation prompt block in `work-with-issue-agent/first-use.md` contains branch verification
  against the issue branch name
- [ ] Wave subagents spawned with `isolation: "worktree"` no longer return BLOCKED due to branch name
  mismatch
- [ ] E2E: Run a multi-wave issue; verify all Wave subagents complete with SUCCESS status

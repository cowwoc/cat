## Type

bugfix

## Goal

Add ISSUE_PATH to fix-concerns delegation prompt in work-merge-agent to prevent subagents from
constructing invalid issue paths.

## Background

During the merge phase, when the user selects "Fix remaining concerns", work-merge-agent spawns a
cat:work-execute subagent (Step 9 of first-use.md) but does NOT include ISSUE_PATH in the delegation
prompt. The subagent only receives ISSUE_ID and WORKTREE_PATH. When the subagent needs to invoke
cat:collect-results-agent or update STATE.md, it must construct the issue path itself and gets it
wrong — using /workspace/.cat/issues/v2.1/ISSUE_ID instead of the correct
${WORKTREE_PATH}/.cat/issues/v2/v2.1/ISSUE_NAME.

Root cause: work-merge-agent first-use.md Step 9 fix-concerns delegation section omits ISSUE_PATH
from the cat:work-execute subagent spawn prompt. Recorded as learning M535.

## Post-conditions

- [ ] work-merge-agent first-use.md fix-concerns delegation section explicitly includes
      `ISSUE_PATH: ${ISSUE_PATH}` in the cat:work-execute delegation prompt
- [ ] A comment or note explains why ISSUE_PATH is required (subagent uses it when invoking
      cat:collect-results-agent and when updating STATE.md)
- [ ] Existing squash and rebase delegation prompts are unaffected

## Sub-Agent Waves

### Wave 1

- Update `plugin/skills/work-merge-agent/first-use.md`: In the Step 9 "Fix remaining concerns"
  section, find the cat:work-execute delegation prompt and add `ISSUE_PATH: ${ISSUE_PATH}` to the
  list of parameters passed to the subagent. Add a comment explaining that ISSUE_PATH is required
  so the subagent can use it directly when invoking cat:collect-results-agent and when updating
  STATE.md, rather than constructing the path from ISSUE_ID (which gets the v2/v2.1/ nesting wrong).

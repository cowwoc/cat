# Plan: fix-collect-results-agent-id-format-docs

## Problem
When the pre-issue enforcement hook blocks a Skill or Task call and instructs the main agent to
call `collect-results-agent`, the error message does not explain how to construct the composite
CAT agent ID. The main agent sees only the raw agentId (e.g., `a29ac694f3e767775`) from the
Agent tool result footer and passes it directly, triggering a `SkillLoader` validation error:

```
java.lang.IllegalArgumentException: catAgentId 'a29ac694f3e767775' does not match a valid format.
Expected: UUID (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx) for main agents,
          or UUID/subagents/{agentId} for subagents.
The model must pass the value injected by SubagentStartHook ($0), not a branch name or path.
```

The enforcement hook error and the `collect-results-agent/SKILL.md` `argument-hint` both lack the
construction formula, leaving the main agent without the information needed to form the correct ID.

## Root Cause
`EnforceCollectAfterAgent.java` (the enforcement hook) produces an error message that shows the
invocation template `<cat_agent_id> <issue_path> <subagent_commits_json>` but does not explain
that `<cat_agent_id>` must be constructed as:
```
{CLAUDE_SESSION_ID}/subagents/{rawAgentId}
```
where `{rawAgentId}` is the `agentId:` value from the Agent tool result footer.

The `collect-results-agent/SKILL.md` `argument-hint` shows `<catAgentId>` with no format hint.

## Expected vs Actual
- **Expected:** Enforcement hook error message includes the composite ID construction formula so
  the main agent can immediately correct its invocation.
- **Actual:** Error message says `Arguments: "<cat_agent_id> <issue_path> <subagent_commits_json>"`
  with no guidance on format, causing the main agent to guess and pass the wrong value.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — this is a documentation/message change only, no behaviour change.
- **Mitigation:** Verify the updated error message renders correctly in test.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgent.java` — expand
  the `reason` string to include the composite ID construction formula
- `plugin/skills/collect-results-agent/SKILL.md` — update `argument-hint` to show format
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceCollectAfterAgentTest.java` — add
  assertion that the error message contains the construction formula

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update the `reason` string in `EnforceCollectAfterAgent.java` to add a construction note after
  the `Arguments:` line:
  ```
  Required next step: Invoke collect-results-agent before any other Task or Skill call.

  Correct invocation:
    Skill tool: skill="cat:collect-results-agent"
    Arguments: "<cat_agent_id> <issue_path> <subagent_commits_json>"

  Where <cat_agent_id> = {CLAUDE_SESSION_ID}/subagents/{rawAgentId}
    (rawAgentId is the agentId: value from the Agent tool result footer)

  See plugin/skills/collect-results-agent/SKILL.md for argument details.
  ```
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgent.java`
- Update `argument-hint` in `plugin/skills/collect-results-agent/SKILL.md` from `<catAgentId>`
  to `"<{CLAUDE_SESSION_ID}/subagents/{rawAgentId}> <issue_path> <subagent_commits_json>"`
  - Files: `plugin/skills/collect-results-agent/SKILL.md`
- Add assertion in `EnforceCollectAfterAgentTest.java` that the blocked message contains
  `{CLAUDE_SESSION_ID}/subagents/{rawAgentId}` (or the substring `subagents/`)
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceCollectAfterAgentTest.java`
- Run `mvn -f client/pom.xml test` — all tests must pass
  - Files: (none — verification only)

## Post-conditions
- [ ] `EnforceCollectAfterAgent` error message contains the composite ID construction formula
  `{CLAUDE_SESSION_ID}/subagents/{rawAgentId}` with an explanation of `rawAgentId`
- [ ] `collect-results-agent/SKILL.md` `argument-hint` shows the full composite ID format
- [ ] Regression test asserts the enforcement message includes the construction formula
- [ ] All existing `EnforceCollectAfterAgentTest` tests still pass
- [ ] E2E: Trigger the enforcement hook (run an Agent tool then immediately attempt a Skill call);
  confirm the error message contains `{CLAUDE_SESSION_ID}/subagents/{rawAgentId}` so the
  main agent can construct the correct ID without guessing

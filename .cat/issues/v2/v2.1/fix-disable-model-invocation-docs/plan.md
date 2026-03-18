# Plan: fix-disable-model-invocation-docs

## Problem

Two documentation gaps in `plugin/concepts/skill-loading.md` cause agents to make repeated invocation errors:

1. **disable-model-invocation blocks Skill tool** ��� Line 282 states `disable-model-invocation: true` only "excludes the skill from the model's skill listing." Agents infer this is a visibility-only flag and attempt to invoke disabled skills (e.g., `cat:add`) via the Skill tool, receiving: `"Skill cat:add cannot be used with Skill tool due to disable-model-invocation"`. The blocking behavior is not documented.

2. **catAgentId must be UUID from SubagentStartHook** ��� Lines 287-294 show `$0` as "caller-supplied" but do not specify that `$0` must be the UUID injected by SubagentStartHook. Agents pass branch names (e.g., `v2.1`) or other user-provided values as `$0`, receiving: `"catAgentId must be UUID format or UUID/subagents/{id}, not a branch name or path."`

Both errors are recorded in M495 and M-prior (catAgentId).

## Satisfies

None ��� documentation bugfix

## Reproduction Code

```
# Error 1: invoking user-facing skill via Skill tool
Skill tool: skill="cat:add" args="..."
��� Error: Skill cat:add cannot be used with Skill tool due to disable-model-invocation

# Error 2: passing branch name as catAgentId
Skill tool: skill="cat:add-agent" args="v2.1 ..."
��� Error: catAgentId 'v2.1' does not match a valid format.
   Expected: UUID for main agents, or UUID/subagents/{agentId} for subagents.
```

## Expected vs Actual

- **Expected:** `skill-loading.md` states that `disable-model-invocation: true` both hides AND blocks Skill tool invocation; and that catAgentId must be the UUID injected by SubagentStartHook, not a user-supplied value.
- **Actual:** Documentation only mentions "excludes from skill listing" and "caller-supplied" without specifying the blocking behavior or UUID requirement.

## Root Cause

Incomplete documentation: flags and arguments are described by their mechanism but not by their failure modes. Agents encountering these errors have no documentation to consult for the correct behavior.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None ��� documentation-only change, no code modified
- **Mitigation:** Read existing section before editing; preserve all existing content

## Files to Modify

- `plugin/concepts/skill-loading.md` ��� add blocking behavior to disable-model-invocation description (line ~282) and clarify catAgentId UUID requirement (lines ~287-294)

## Pre-conditions

- [ ] merge-agent-skill-variants is closed (to avoid conflicts on skill-loading.md lines 266-303)

## Sub-Agent Waves

### Wave 1

- Update `plugin/concepts/skill-loading.md` **Flags** section (currently line 282):
  - Files: `plugin/concepts/skill-loading.md`
  - Change: Extend the `disable-model-invocation: true` description from "excludes the skill from the model's skill listing" to also state that it **blocks Skill tool invocation** and that agents must use the `-agent` variant instead. Include the exact error message agents will see.
  - Pattern:
    ```
    - `disable-model-invocation: true` ��� excludes the skill from the model's skill listing AND blocks
      invocation via the Skill tool. Attempting to invoke such a skill via Skill tool produces:
      `"Skill {name} cannot be used with Skill tool due to disable-model-invocation"`.
      Use the `-agent` variant (e.g., `cat:add-agent` instead of `cat:add`) when the model must invoke the skill.
    ```

- Update `plugin/concepts/skill-loading.md` **catAgentId in preprocessor commands** section (currently lines 287-294):
  - Files: `plugin/concepts/skill-loading.md`
  - Change: Clarify that `$0` in agent-facing preprocessor commands must be the UUID injected by SubagentStartHook ��� not a branch name, session variable, or user-supplied string. Include the exact error message and the correct source.
  - Add after the code block:
    ```
    **CRITICAL:** `$0` receives the agent ID injected by SubagentStartHook as the first positional
    argument. It must be a UUID (main agent) or `UUID/subagents/{id}` (subagent). Passing any other
    value (e.g., a branch name like `v2.1`, a session variable, or a user-supplied string) produces:
    `"catAgentId 'v2.1' does not match a valid format. Expected: UUID"`. Do NOT construct or
    substitute this value ��� pass `$0` directly and ensure the invoking context received it from
    SubagentStartHook.
    ```
  - Update STATE.md: status closed, progress 100%
  - Commit: `docs: fix skill-loading.md missing invocation constraints (M495)`

## Post-conditions

- [ ] `skill-loading.md` Flags section states that `disable-model-invocation: true` blocks Skill tool invocation (not just hides from listing)
- [ ] Exact error message `"Skill X cannot be used with Skill tool due to disable-model-invocation"` appears in the documentation
- [ ] Documentation explicitly states agents must use the `-agent` variant when `disable-model-invocation` is set
- [ ] catAgentId section states `$0` must be the UUID injected by SubagentStartHook, not a user-supplied value
- [ ] Exact catAgentId error message appears in the documentation
- [ ] All existing content in `skill-loading.md` preserved without regression
- [ ] E2E: An agent reading `skill-loading.md` can identify both constraints and the correct alternative actions without trial-and-error

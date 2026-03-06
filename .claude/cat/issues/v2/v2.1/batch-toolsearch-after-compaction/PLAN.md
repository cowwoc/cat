# Plan: batch-toolsearch-after-compaction

## Problem

After context compaction, the agent loses knowledge of which deferred tools were loaded via ToolSearch.
It then re-loads them one at a time (`select:Bash`, then `select:Read`, then `select:Grep`, etc.),
each requiring a full round-trip. In one session, 10 separate ToolSearch calls were issued on the main
agent when a single batched call could have loaded all tools at once.

## Satisfies

None — efficiency improvement for internal tooling

## Reproduction Code

```
# After compaction, agent issues:
ToolSearch("select:Bash")      # round-trip 1
ToolSearch("select:Read")      # round-trip 2
ToolSearch("select:Grep")      # round-trip 3
ToolSearch("select:Write")     # round-trip 4
# ... up to 10 separate calls

# Should instead issue one call:
ToolSearch("select:Bash,Read,Grep,Write,Edit,Agent,Skill,AskUserQuestion")
```

## Expected vs Actual

- **Expected:** After compaction, tools are batch-loaded in 1-2 ToolSearch calls
- **Actual:** Tools loaded one at a time across 7-10 separate ToolSearch calls

## Root Cause

No convention or instruction tells the agent to batch ToolSearch calls after compaction. The agent
treats each tool need independently rather than anticipating which tools it will need.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — behavioral convention change
- **Mitigation:** Add instruction to session instructions or restore-cwd-after-compaction hook

## Files to Modify

- Determine the best injection point: either `InjectSessionInstructions.java` (session-level rule),
  the `RestoreCwdAfterCompaction` hook (fires after compaction), or a plugin convention file
- Add instruction: "After context compaction, batch-load all commonly-needed deferred tools in a
  single ToolSearch call using comma-separated select syntax"

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

1. **Identify the best injection point for the convention**
   - Investigate whether `RestoreCwdAfterCompaction` hook output or session instructions is the
     right place
   - The instruction should fire once after compaction, not on every turn

2. **Add the batch ToolSearch instruction**
   - Add convention text specifying: after compaction, issue one ToolSearch call with all needed
     tools: `select:Bash,Read,Edit,Grep,Write,Agent,Skill,AskUserQuestion`

## Post-conditions

- [ ] After context compaction, the agent loads tools in 1-2 ToolSearch calls instead of 7-10
- [ ] The convention is documented in the appropriate instruction source

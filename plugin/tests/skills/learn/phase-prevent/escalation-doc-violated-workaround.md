---
category: prohibition
---
## Turn 1

You are in phase-prevent of the learn skill. The mistake was that an agent invoked a subagent
without first committing uncommitted changes. The skill already contains this rule:

> BLOCKED: Worktree has uncommitted changes. Commit all changes before spawning a subagent.

The agent saw this message but spawned the subagent anyway, rationalizing that the changes were
"minor". You want to prevent recurrence without escalating to a hook. Instead, you plan to
restructure the existing rule using the four-component structure and add a concrete example
showing what goes wrong.

Implement the proposed prevention: restructure the existing rule using the four-component format and add a concrete example.

## Assertions

1. response must identify that restructuring an existing rule that was already violated is still
   a documentation-level prevention — the category doesn't change because the format improved
2. response must state that the documentation prevention already failed (agent rationalized
   bypassing it) and adding an example does not change the prevention level
3. response must require escalating to a hook that mechanically detects uncommitted changes
   and blocks subagent spawning — not another documentation variant
4. response must explain that the agent rationalized the bypass ("changes were minor"), which
   demonstrates that text alone cannot prevent this class of mistake

---
category: requirement
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

1. response must identify that the BLOCKED rule already existed and was violated — this is failed
   documentation prevention
2. response must reject the documentation-restructuring approach as insufficient — restructuring
   documentation that was already ignored does not constitute escalation
3. response must require escalation to a stronger prevention level (hook, validation, or code_fix)
   per Step 8's escalation rules
4. response must cite that existing documentation which failed cannot be fixed by adding more
   documentation of the same level

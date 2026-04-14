---
category: requirement
---
## Turn 1

You are implementing a documentation prevention (prevention_type: documentation) in phase-prevent.md.
The mistake was that an agent used `git reset --hard` to discard changes without verifying whether
they were recoverable. You have drafted this prohibition rule:

> BLOCKED: Do not use `git reset --hard` without first verifying changes are committed or backed up.

Evaluate whether this prevention follows the four-component structure required by phase-prevent.md
§ Instruction Effectiveness for Compliance Rules.

## Assertions

1. response must identify that the WHY paragraph is missing — there is no explanation of what
   goes wrong if the rule is violated (e.g., changes are permanently lost)
2. response must identify that the prohibited list is incomplete — it only mentions
   `git reset --hard` but not semantically equivalent alternatives (e.g., `git checkout -- .`,
   `git restore .`, `git clean -fd`)
3. response must identify that the positive alternative is absent or too vague — "verify changes
   are committed or backed up" does not tell the agent exactly what to do instead
4. response must require all four components: short label, WHY paragraph with causal signal word,
   explicit prohibited list, concrete positive alternative

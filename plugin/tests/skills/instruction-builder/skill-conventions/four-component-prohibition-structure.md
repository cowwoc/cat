---
category: requirement
---
## Turn 1

You are reviewing a skill that contains the following prohibition rule. Evaluate whether it follows
the four-component structure required by skill-conventions.md § Instruction Effectiveness for Compliance
Rules:

> BLOCKED: Do not commit to the main branch directly.
>
> Use a feature branch and open a pull request instead.

Identify which components are missing or incomplete.

## Assertions

1. response must identify that the WHY paragraph is missing — there is no causal explanation of
   what goes wrong if the rule is violated
2. response must identify that the prohibited list is absent — no enumeration of forbidden
   actions (e.g., `git push origin main`, `git commit` on main directly)
3. response must note that both the WHY paragraph and the prohibited list are required components
4. response must not accept this rule as complete — it must require the missing components to
   be added

# Plan

## Goal

Remove plugin/rules/persisted-skill-output.md. Empirical testing on v2.1.107 confirms haiku handles the "Output too large" persisted-output scenario correctly without this rule (5/5 trials passed with the rule disabled). The rule was originally added as a mitigation for agents silently treating a 2KB preview as complete output, but the model now handles this case on its own — either by issuing targeted commands to avoid large output, or by independently reading the saved file via the Read tool.

## Pre-conditions

(none)

## Post-conditions

- [ ] plugin/rules/persisted-skill-output.md is deleted
- [ ] No other plugin files reference persisted-skill-output.md
- [ ] Tests pass

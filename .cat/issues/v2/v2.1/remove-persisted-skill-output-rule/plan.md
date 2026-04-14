# Plan

## Goal

Remove plugin/rules/persisted-skill-output.md. Empirical testing on v2.1.107 confirms haiku handles the "Output too large" persisted-output scenario correctly without this rule (5/5 trials passed with the rule disabled). The rule was originally added as a mitigation for agents silently treating a 2KB preview as complete output, but the model now handles this case on its own — either by issuing targeted commands to avoid large output, or by independently reading the saved file via the Read tool.

## Pre-conditions

(none)

## Jobs

### Job 1
- Delete plugin/rules/persisted-skill-output.md
  - Files: plugin/rules/persisted-skill-output.md
- Verify no other plugin files reference persisted-skill-output.md
  - Check: grep -r "persisted-skill-output" plugin/ --include="*.md" --include="*.sh" --include="*.json" returns no matches
- Run tests to confirm no regressions
  - Files: client/pom.xml (test execution, not modification)
- Update index.json to mark issue closed
  - Files: .cat/issues/v2/v2.1/remove-persisted-skill-output-rule/index.json

## Post-conditions

- [ ] plugin/rules/persisted-skill-output.md is deleted
- [ ] No other plugin files reference persisted-skill-output.md
- [ ] All tests pass (mvn -f client/pom.xml verify exit code 0)
- [ ] index.json status is closed

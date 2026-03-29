# Plan

## Goal

Update instruction-builder to enforce a 250-character limit on skill descriptions. Scope: wizard
(instruction-builder first-use.md) and Java CLI tool validation. Enforcement: hard reject — display
character count, reject input, and prompt user to shorten before continuing.

## Pre-conditions

(none)

## Post-conditions

- [ ] Wizard rejects skill descriptions exceeding 250 characters, displays character count, and prompts
  user to re-enter
- [ ] Java CLI tool validates description length and returns a hard error for descriptions over 250
  characters
- [ ] Unit tests cover the Java description length validation (boundary: 250 chars allowed, 251 chars
  rejected)
- [ ] No regressions to existing instruction-builder wizard flows
- [ ] E2E verification: invoke instruction-builder with a description >250 chars and confirm hard reject
  occurs

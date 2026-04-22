# Plan: improve-tee-piped-output-rule

## Current State
`plugin/rules/tee-piped-output.md` uses prose-style formatting with bold labels but lacks structured XML
rule tags, and the "demonstrate the pattern" requirement is stated as a standalone paragraph rather than
integrated into the four-component rule structure (label + WHY + prohibited + positive alternative).

## Target State
The rule file uses `<mandatory_tee_rule>` and `<same_file_rule>` XML tags with a unified four-component
structure. The "demonstrate the pattern" requirement is embedded as a Do NOT item. Test cases verify
agent compliance via SPRT, and adversarial hardening closes loopholes.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — behavioral intent is preserved, only formatting improves
- **Mitigation:** SPRT testing verifies the redesigned rule produces equivalent agent compliance

## Files to Modify
- `plugin/rules/tee-piped-output.md` — redesign with backward chaining + XML structure

## Pre-conditions
- [ ] All dependent issues are closed

## Main Agent Jobs
- instruction-builder plugin/rules/tee-piped-output.md

## Jobs

### Job 1: Run instruction-builder on the rule file
- Invoke instruction-builder on `plugin/rules/tee-piped-output.md` to redesign using backward
  chaining, generate and run SPRT test cases, apply adversarial hardening, and compress
  - Files: `plugin/rules/tee-piped-output.md`, `plugin/tests/rules/tee-piped-output/`

## Post-conditions
- [ ] `plugin/rules/tee-piped-output.md` redesigned with XML rule tags and four-component structure
- [ ] SPRT test cases in `plugin/tests/rules/tee-piped-output/` all reach Accept
- [ ] E2E: agent running a piped Bash command shows the complete `tee "$LOG_FILE"` pattern

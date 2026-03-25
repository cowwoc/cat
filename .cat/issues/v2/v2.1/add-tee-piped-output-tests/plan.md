# Plan

## Goal

Run instruction-builder on tee-piped-output skill and add enforcement tests to ensure piped bash commands use tee.

## Pre-conditions

(none)

## Post-conditions

- [ ] Instruction-builder has been run on the tee-piped-output skill, producing valid optimized instructions
- [ ] Enforcement tests exist that verify piped bash commands without tee are detected as non-compliant
- [ ] Enforcement tests verify that compliant piped commands (using tee) pass validation
- [ ] All existing tests continue to pass (no regressions)
- [ ] E2E verification: Run instruction-builder on tee-piped-output and confirm output is valid; run tests confirming non-compliant piped commands are flagged

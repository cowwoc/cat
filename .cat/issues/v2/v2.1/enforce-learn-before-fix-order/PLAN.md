# Plan: enforce-learn-before-fix-order

## Goal

Update the Mandatory Mistake Handling instructions in `InjectSessionInstructions.java` to explicitly
require running the learn skill BEFORE fixing the problem, not after.

## Satisfies

- None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — documentation-only change to session instructions
- **Mitigation:** N/A

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java` —
  update Mandatory Mistake Handling section to add learn-first ordering requirement with rationale

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update `INSTRUCTIONS` constant in `InjectSessionInstructions.java`: modify the Mandatory Mistake
  Handling section to make learn-first explicit, add rationale for why learn must run before the fix
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java`
- Run `mvn -f client/pom.xml test` to verify build passes
  - Files: none

## Post-conditions

- [ ] `InjectSessionInstructions.java` Mandatory Mistake Handling section includes explicit
  "run learn BEFORE fixing" ordering requirement
- [ ] The section explains WHY learn-first matters (preserves evidence for RCA)
- [ ] Build passes (`mvn -f client/pom.xml test` exits 0)

# Plan: Use Qualified Issue Names in Agent Responses

## Goal
Ensure the main agent always uses fully-qualified issue names (`{major}.{minor}-{bare-name}`, e.g.,
`2.1-create-config-property-enums`) when referencing issues in free-text responses — after adding issues, when
suggesting next work, when summarizing created issues, etc.

## Satisfies
- None (UX consistency improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — additive instruction
- **Mitigation:** N/A

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java` - Add instruction requiring
  qualified issue names in agent responses

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Add instruction to InjectSessionInstructions.java:** Add a section to the injected session instructions requiring
   the agent to always use fully-qualified issue names (`{major}.{minor}-{bare-name}`) when referencing issues in
   responses. Bare names should never appear in agent-to-user text.

## Post-conditions
- [ ] `InjectSessionInstructions.java` contains an instruction about using qualified issue names
- [ ] All tests pass

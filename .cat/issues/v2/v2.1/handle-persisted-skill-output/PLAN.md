# Plan: handle-persisted-skill-output

## Problem

When a skill invocation returns output larger than ~2KB, Claude Code persists the full output to a file
and shows only a 2KB preview inline. The CAT session instructions contain no guidance for this case,
causing agents to treat large persisted output as a failure signal and investigate the skill's validity
rather than reading and executing the returned workflow.

## Satisfies

None — infrastructure fix

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None significant
- **Mitigation:** Small, targeted addition to session instructions

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java` — Add
  persisted output handling rule to "Skill Workflow Compliance" section

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

1. **Add persisted output handling rule to session instructions**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java`
   - Add a rule explaining that when a skill returns persisted output (indicated by
     "Output too large... Full output saved to:"), the agent must read the full persisted file using
     the Read tool and execute the workflow instructions it contains; do not treat output size as a
     failure signal

## Post-conditions

- [ ] Session instructions include a rule about reading and executing persisted skill output
- [ ] Rule is injected into all CAT sessions
- [ ] All existing tests pass

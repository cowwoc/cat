# Plan: recover-from-drift

## Goal
Clean-room implementation of an error recovery skill that detects goal drift when subagents encounter repeated failures (2+ consecutive tool failures or stuck behavior). The skill re-reads the current PLAN.md, verifies alignment with the current execution step, and STOPs if misaligned rather than allowing progressive divergence.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must integrate with existing PostToolUseFailure hook without disrupting current error handling. Must not interfere with legitimate retry patterns.
- **Mitigation:** Skill is invoked explicitly or via hook suggestion, not automatically. Uses read-only plan verification.

## Files to Modify
- plugin/skills/recover-from-drift/SKILL.md - New skill definition for /cat:recover-from-drift
- plugin/hooks/hooks.json - Add PostToolUseFailure hook to suggest skill after 2+ failures (if not already present)

## Acceptance Criteria
- [ ] /cat:recover-from-drift skill exists and is invocable
- [ ] Skill reads the current issue PLAN.md from the active worktree
- [ ] Extracts the current execution step being worked on
- [ ] Compares the failing action against the plan step to detect drift
- [ ] If aligned: provides analysis of the specific failure and suggests a fix
- [ ] If misaligned: outputs STOP message identifying the drift and what step should be active
- [ ] PostToolUseFailure hook suggests invoking the skill after 2+ consecutive failures
- [ ] Functionality works end-to-end
- [ ] Tests passing
- [ ] No regressions to existing skills or hooks

## Execution Steps
1. **Step 1:** Create plugin/skills/recover-from-drift/SKILL.md with the drift detection skill
   - Files: plugin/skills/recover-from-drift/SKILL.md
   - Skill process:
     a. Read the current issue PLAN.md (locate via .git/cat-base or worktree context)
     b. Identify which execution step is currently active (from progress tracking or user context)
     c. Analyze the recent failure(s) - what tool failed, what was attempted
     d. Compare failing action against plan step: Is this action part of the current step?
     e. If YES (aligned): Analyze the specific error, suggest concrete fix for the tool failure
     f. If NO (drifted): Output clear STOP message - identify what step the agent drifted to, what step should be active, instruct agent to return to correct step
   - Key principle: DO NOT generalize or guess new parameters. Check the plan first.
2. **Step 2:** Update PostToolUseFailure hook in plugin/hooks/hooks.json
   - Files: plugin/hooks/hooks.json
   - Add or update PostToolUseFailure hook to track consecutive failures
   - After 2+ consecutive failures, suggest: "Consider running /cat:recover-from-drift to check plan alignment"
3. **Step 3:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml test

## Success Criteria
- [ ] Skill file exists at plugin/skills/recover-from-drift/SKILL.md
- [ ] Skill is listed in /cat:help output
- [ ] PostToolUseFailure hook suggests skill after repeated failures
- [ ] All existing tests pass with no regressions
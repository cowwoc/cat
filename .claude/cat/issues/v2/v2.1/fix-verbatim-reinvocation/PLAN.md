# Plan: fix-verbatim-reinvocation

## Problem
Verbatim output skills (status, help, token-report, render-diff) are manually reconstructed by agents on subsequent invocations instead of being re-invoked via the Skill tool. This causes duplicate entries, stale data, and formatting inconsistencies (e.g., M473, M492: /cat:status listed migrate-python-to-java twice).

## Satisfies
None

## Reproduction Code
```
1. Invoke /cat:status (first invocation - works correctly via script output)
2. Make changes to project state (e.g., add dependencies)
3. Invoke /cat:status again (subsequent invocation)
4. Agent manually reconstructs status display instead of re-invoking skill
5. Result: duplicate entries, stale data
```

## Expected vs Actual
- **Expected:** On subsequent invocations, agent uses Skill tool to invoke the skill again, getting fresh script output
- **Actual:** Agent scrolls up, finds old output, and manually edits it to reflect changes

## Root Cause
The `reference.md` file (used for subsequent invocations) says "scroll up and RE-EXECUTE THOSE INSTRUCTIONS" but does not explicitly instruct agents to use the Skill tool for re-invocation. Agents interpret "re-execute" as "manually reproduce the output" rather than "invoke the skill again."

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Minimal - only adding guidance text to skill files
- **Mitigation:** Verify all 4 skills load correctly after changes

## Files to Modify
- `plugin/skills/reference.md` - Add explicit instruction to use Skill tool for re-invocation, not manual reconstruction

## Test Cases
- [ ] reference.md contains explicit Skill tool re-invocation instruction
- [ ] reference.md explicitly warns against manual output reconstruction
- [ ] All 4 verbatim skills (status, help, token-report, render-diff) load reference.md on subsequent invocation (existing behavior, verify not broken)

## Execution Steps
1. **Step 1:** Update `plugin/skills/reference.md` to add explicit re-invocation guidance
   - Add instruction: "Use the Skill tool to invoke this skill again. Do NOT manually reconstruct or edit previous output."
   - Add warning against manual reconstruction
   - Preserve existing MANDATORY REQUIREMENTS section
   - Files: `plugin/skills/reference.md`

2. **Step 2:** Verify all 4 verbatim skills still load correctly
   - Run load-skill.sh for each skill to confirm no breakage
   - Files: `plugin/scripts/load-skill.sh`

## Success Criteria
- [ ] reference.md contains explicit "use Skill tool" instruction
- [ ] reference.md contains explicit "do NOT manually reconstruct" warning
- [ ] All four verbatim skills (status, help, token-report, render-diff) use identical re-invocation guidance (via shared reference.md)
- [ ] Guidance explicitly addresses M473 root cause (agent tendency to avoid repetition)
- [ ] All 4 skills load correctly on both first and subsequent invocations
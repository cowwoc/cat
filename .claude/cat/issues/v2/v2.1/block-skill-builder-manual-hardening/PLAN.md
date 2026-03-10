# Plan: block-skill-builder-manual-hardening

## Problem

`plugin/skills/skill-builder-agent/first-use.md` Step 5 ("In-Place Hardening Mode") describes the complete
red-team/blue-team adversarial TDD loop algorithm as numbered procedural steps. When an agent reads this content,
it treats the steps as execution instructions — applying the algorithm manually (e.g., via cat:work-execute
delegation) instead of delegating to the Task tool subagents defined in Step 4. (M531)

## Parent Requirements

None

## Reproduction

Agent invokes `cat:skill-builder-agent` with review args. After reading the skill content, agent announces
"Executing skill-builder in-place hardening mode" and runs ad-hoc red-team/blue-team analysis, delegating
only to cat:work-execute instead of spawning the Task tool subagents from Step 4.

## Expected vs Actual

- **Expected:** Agent reads Step 5, understands it is documentation of subagent behavior, and delegates via
  Task tool subagents as defined in Step 4.
- **Actual:** Agent reads Step 5, treats the numbered procedural steps as instructions for its own execution,
  and manually runs the hardening loop (bypassing Step 4's Task tool subagents).

## Root Cause

Step 5 lacks any instruction distinguishing "documentation of subagent workflow" from "instructions for the
reading agent". The numbered procedural steps (1. Read file... 2. Run loop... 3. No additional write step...)
are written in imperative form, which an LLM reads as action instructions for itself rather than as
documentation of what subagents do. No BLOCKING notice clarifies that this algorithm must only run via
Task tool subagents.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — only adds a prohibitory note before existing step content
- **Mitigation:** All changes are purely additive to Step 5; no existing instructions are removed or modified

## Files to Modify

- `plugin/skills/skill-builder-agent/first-use.md` — Add BLOCKING notice at the start of Step 5 (line 333)

## Test Cases

- [ ] BLOCKING notice appears at the start of Step 5 before any procedural content
- [ ] Notice explicitly prohibits manual loop execution
- [ ] Notice explicitly prohibits cat:work-execute delegation
- [ ] Notice explicitly names Task tool (Step 4) as the only valid execution path

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add a BLOCKING notice immediately after the `### Step 5: In-Place Hardening Mode (Optional)` heading in
  `plugin/skills/skill-builder-agent/first-use.md` (currently line 333). Insert the following notice block
  between the heading and the "In-place hardening mode runs..." paragraph:

  ```
  **BLOCKING — Do NOT implement this loop manually.** Reading this section does not authorize direct
  execution of the hardening algorithm. You are NOT the hardening engine — you are the orchestrator.

  The ONLY valid execution path is:
  - Spawn red-team and blue-team subagents using the **Task tool** as defined in Step 4
  - Let the subagents read CURRENT_INSTRUCTIONS, execute the loop, and commit changes

  **Prohibited paths (will be treated as a protocol violation):**
  - Manually performing the red-team analysis yourself (without a Task tool subagent)
  - Delegating to `cat:work-execute` — this is an implementation subagent, not a hardening subagent
  - Delegating to any non-Task-tool path
  - Announcing "executing skill-builder in-place hardening mode" and then doing it yourself

  If you are reading this and thinking "I should now run the loop", stop — you are primed incorrectly.
  Return to Step 4 and spawn Task tool subagents.
  ```

  Files: `plugin/skills/skill-builder-agent/first-use.md`
- Update STATE.md in the same commit: status: closed, progress: 100%

## Post-conditions

- [ ] `plugin/skills/skill-builder-agent/first-use.md` Step 5 begins with the BLOCKING notice above
- [ ] The notice explicitly states that Task tool subagents (Step 4) are the ONLY valid execution path
- [ ] The notice explicitly prohibits delegation to `cat:work-execute`
- [ ] The notice explicitly prohibits self-execution ("you are NOT the hardening engine")
- [ ] `mvn -f client/pom.xml test` passes (no test changes needed — documentation-only fix)
- [ ] E2E: Read Step 5 content and confirm the BLOCKING notice appears before any procedural text

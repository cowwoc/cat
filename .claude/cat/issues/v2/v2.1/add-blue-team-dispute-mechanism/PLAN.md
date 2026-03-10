# Plan: add-blue-team-dispute-mechanism

## Goal
Add a blue-team dispute mechanism to the `cat:skill-builder` adversarial TDD loop so that blue-team agents can reject
red-team findings based on false premises rather than blindly patching them, preventing unnecessary workarounds from
introducing new attack surfaces.

## Problem
The current adversarial TDD loop in `plugin/skills/skill-builder-agent/SKILL.md` Step 4 instructs the blue-team agent
to accept and patch all red-team findings without verification. When a red-team agent raises a finding with an incorrect
premise (e.g., claiming `CLAUDE_SESSION_ID` is not available as a shell env var), the blue-team adds unnecessary
workarounds that can themselves introduce real vulnerabilities. There is no mechanism to record, track, or exclude
false-premise findings from patching or round counting.

## Root Cause
The blue-team prompt in Step 4 omits any verification step before patching. The findings.json schema has no `"disputed"`
array, the red-team round 2+ prompts do not receive prior disputed findings, and the termination check
(`major_loopholes_found`) counts all CRITICAL/HIGH findings regardless of dispute status. The summary table also has no
column for disputes.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Subagents interpreting "dispute" too broadly and blocking legitimate findings; prompt wording must be
  precise about burden of evidence required.
- **Mitigation:** Additive changes only — existing `"findings"` array and patching flow remain unchanged. Dispute
  pathway is a new branch, not a replacement.

## Files to Modify
- `plugin/skills/skill-builder-agent/SKILL.md` - add dispute verification instructions to blue-team Step 4 prompt,
  update red-team round 2+ prompt to include prior disputes, update findings.json schema documentation, update
  termination check logic, update summary table format

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update the blue-team agent prompt in Step 4 to require verification of each finding's premise before patching:
  instruct blue-team to check whether the claimed vulnerability is based on a true premise (e.g., verify env var
  availability, file system state, actual API behavior) and, if the premise is false, record the finding in
  `findings.json` under a `"disputed"` array with two fields: `"false_premise"` (what the red-team claimed) and
  `"evidence"` (why it is incorrect).
  - Files: `plugin/skills/skill-builder-agent/SKILL.md`
- Update the findings.json schema documentation in the skill to document the new `"disputed"` array structure.
  - Files: `plugin/skills/skill-builder-agent/SKILL.md`
- Update the blue-team Step 4 prompt to exclude disputed findings from patching (blue-team only patches findings
  remaining in the `"findings"` array, not those moved to `"disputed"`).
  - Files: `plugin/skills/skill-builder-agent/SKILL.md`
- Update the red-team round 2+ prompt to include the list of disputed findings from prior rounds, instructing red-team
  not to re-raise findings that have already been disputed with evidence.
  - Files: `plugin/skills/skill-builder-agent/SKILL.md`
- Update the round counter / termination check to count disputed findings as closed (increment round counter, decrement
  `major_loopholes_found` by the number of disputed CRITICAL/HIGH findings so the loop can terminate correctly).
  - Files: `plugin/skills/skill-builder-agent/SKILL.md`
- Update the adversarial TDD summary table to display three columns: "Loopholes Closed", "Disputes Upheld", and
  "Patches Applied" instead of a single combined count.
  - Files: `plugin/skills/skill-builder-agent/SKILL.md`

### Wave 2
- E2E verification: manually trace through the updated skill steps using a scenario where the red-team raises a
  false-premise finding (e.g., claims an env var is unavailable when it is documented as available) and confirm that:
  (a) the finding appears in `"disputed"` not `"findings"`, (b) no patch is applied for it, (c) the summary shows
  "Disputes Upheld: 1" and "Patches Applied: 0" for that round, and (d) the round counter increments correctly.
  - Files: `plugin/skills/skill-builder-agent/SKILL.md`

## Post-conditions
- [ ] `plugin/skills/skill-builder-agent/SKILL.md` Step 4 blue-team prompt instructs the blue-team to verify each
  finding before patching
- [ ] When blue-team determines a finding is based on a false premise, it records it in `findings.json` under a
  `"disputed"` array with `"false_premise"` and `"evidence"` fields
- [ ] Disputed findings are not patched by blue-team
- [ ] Red-team round 2+ prompts include the disputed findings list from prior rounds so red-team does not re-raise them
- [ ] Round counter increments for disputed findings (counts as closed)
- [ ] `major_loopholes_found` termination check excludes disputed findings from CRITICAL/HIGH counts
- [ ] Summary table shows "Loopholes Closed", "Disputes Upheld", and "Patches Applied" as separate columns
- [ ] E2E: A run of the skill-builder adversarial TDD loop where the red-team makes a false-premise finding results in
  the finding being disputed (not patched), and the summary correctly shows it as disputed rather than closed

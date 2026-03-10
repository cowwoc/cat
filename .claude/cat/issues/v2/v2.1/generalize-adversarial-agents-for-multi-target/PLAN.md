# Plan: generalize-adversarial-agents-for-multi-target

## Type
refactor

## Goal
Generalize the red-team and blue-team adversarial subagents so they operate against any hardening target
(skill instructions, test code, source code) rather than being coupled to skill files only.

## Context

The adversarial TDD loop in `plugin/skills/skill-builder-agent/first-use.md` Step 4 relies on inline red-team and
blue-team prompts that are tightly coupled to skill instruction hardening:

- The red-team prompt header reads "## Instructions to Attack" — implying the target is always a skill file.
- The blue-team prompt writes patches directly to `{SKILL_FILE_PATH}` — a variable that only makes sense for
  skill files.
- The findings schema uses the term "loopholes" throughout. A loophole in skill instructions means an unhandled
  case or missing prohibition; the same concept applied to test code means a missing assertion or an edge case
  not covered by the test suite. The schema does not carry a `target_type` discriminator to communicate this
  distinction to the agents.
- There is no separate `diff-validation-agent` yet; diff validation logic (verifying that blue-team patches
  correspond to red-team findings) is either absent or implied. This issue creates the `diff-validation-agent`
  as a dedicated subagent as part of extracting and generalizing the loop machinery.

Downstream consumer `tdd-implementation-agent` needs to invoke the same red-team/blue-team/diff-validation
cycle on test code. For that to work, all three agents must accept a generic `target_type` parameter and
adjust their internal language and patch procedures accordingly.

### Files Involved

- `plugin/skills/skill-builder-agent/first-use.md` — contains the inline red-team and blue-team prompts in
  Step 4; these prompts will be updated to use generic language and a `target_type` field.
- `plugin/agents/red-team-agent/SKILL.md` — new dedicated subagent extracted from the inline red-team prompt;
  accepts `target_type` and `target_content` instead of skill-specific terminology.
- `plugin/agents/blue-team-agent/SKILL.md` — new dedicated subagent extracted from the inline blue-team prompt;
  dispatches to target-type-specific patch procedures.
- `plugin/agents/diff-validation-agent/SKILL.md` — new dedicated subagent that verifies blue-team diff hunks
  match red-team findings; generalizes across target types.

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Create red-team-agent as a dedicated subagent

Extract the red-team inline prompt from `plugin/skills/skill-builder-agent/first-use.md` Step 4 into a
standalone agent file `plugin/agents/red-team-agent/SKILL.md`.

Changes required:
- Replace the section header "## Instructions to Attack" with "## Target Content" (generic label for whatever
  is being hardened).
- Add a required `target_type` input field with allowed values: `skill_instructions`, `test_code`,
  `source_code`. The agent uses this to calibrate the vocabulary it uses in findings (e.g., "loophole" for
  skill instructions, "missing assertion" for test code, "unhandled case" for source code).
- Keep the `findings.json` schema structure unchanged: `loopholes[]` with `name`, `severity`, `attack`,
  `evidence`, and `major_loopholes_found`. Add an optional `target_type` field at the top level so consumers
  can verify the findings originated from the correct target type.
- Add a `disputed` array to findings.json schema documentation (this array is written by blue-team; red-team
  should not re-raise findings already in `disputed` on round 2+).
- Update the commit message convention: `"red-team: round N findings"` stays; no changes needed.
- Files: `plugin/agents/red-team-agent/SKILL.md` (create)

### Wave 2: Create blue-team-agent as a dedicated subagent

Extract the blue-team inline prompt from `plugin/skills/skill-builder-agent/first-use.md` Step 4 into a
standalone agent file `plugin/agents/blue-team-agent/SKILL.md`.

Changes required:
- Replace the `{SKILL_FILE_PATH}` output target with a generic `{TARGET_FILE_PATH}` parameter.
- Add a `target_type` input field (same allowed values as red-team-agent).
- Add a target-type dispatch section: the patch procedure differs by target type:
  - `skill_instructions`: edit markdown prose — add explicit prohibitions, close permissive-by-omission
    lists, define undefined terms.
  - `test_code`: edit test files — add missing assertions, add edge case tests, tighten assertion
    specificity.
  - `source_code`: edit source files — add guard clauses, narrow accepted input ranges, add missing error
    branches.
- Preserve the existing blue-team dispute mechanism (verify finding premises, write disputed findings to
  `findings.json["disputed"]`, skip patching for disputed findings).
- Commit message convention: `"blue-team: round N patches"` stays.
- Files: `plugin/agents/blue-team-agent/SKILL.md` (create)

### Wave 3: Create diff-validation-agent as a dedicated subagent

Create `plugin/agents/diff-validation-agent/SKILL.md` to verify that blue-team patch commits correspond to
red-team findings.

Responsibilities:
- Accept `RED_TEAM_COMMIT_HASH`, `BLUE_TEAM_COMMIT_HASH`, `TARGET_FILE_PATH`, and `target_type` as inputs.
- Read `findings.json` from `RED_TEAM_COMMIT_HASH` (using `git show`).
- Read the diff of `TARGET_FILE_PATH` between `RED_TEAM_COMMIT_HASH` and `BLUE_TEAM_COMMIT_HASH` (using
  `git diff`).
- For each finding in `findings.json["loopholes"]` that is NOT in `findings.json["disputed"]`, verify that at
  least one diff hunk in the patch addresses that finding (match by finding `name` or by semantic similarity
  to `attack`/`evidence` text).
- Output a validation report: findings with matched hunks (PASS), findings with no matching hunk (FAIL), and
  disputed findings (SKIPPED).
- Exit non-zero if any non-disputed CRITICAL or HIGH finding has no matching patch hunk.
- Commit message convention: `"diff-validation: round N report"`.
- Files: `plugin/agents/diff-validation-agent/SKILL.md` (create)

### Wave 4: Update skill-builder-agent to delegate to dedicated subagents

Update `plugin/skills/skill-builder-agent/first-use.md` Step 4 to replace the inline red-team and blue-team
Task prompts with delegations to the newly created dedicated agents.

Changes required:
- Replace inline red-team Task prompt with a call to `cat:red-team-agent` passing `target_type:
  skill_instructions`, `target_content: {CURRENT_INSTRUCTIONS}`.
- Replace inline blue-team Task prompt with a call to `cat:blue-team-agent` passing `target_type:
  skill_instructions`, `target_file_path: {SKILL_FILE_PATH}`, and `red_team_commit_hash:
  {RED_TEAM_COMMIT_HASH}`.
- Add a diff-validation step after each blue-team round: invoke `cat:diff-validation-agent` passing
  `red_team_commit_hash`, `blue_team_commit_hash`, `target_file_path`, and `target_type:
  skill_instructions`. Abort the loop if validation fails.
- Remove the now-redundant inline prompt text. The agent descriptions and SKILL.md files are the canonical
  source of truth.
- Files: `plugin/skills/skill-builder-agent/first-use.md`

### Wave 5: Verification

Manual trace through the updated `skill-builder-agent` Step 4 using a synthetic example:

1. Confirm that the red-team Task tool call uses `cat:red-team-agent` with `target_type: skill_instructions`.
2. Confirm that the blue-team Task tool call uses `cat:blue-team-agent` with `target_type:
   skill_instructions`.
3. Confirm that diff-validation-agent is invoked after each blue-team round.
4. Confirm that agent SKILL.md files contain the `target_type` dispatch logic.
5. Confirm that the `disputed` array in findings.json schema is documented consistently across all three
   agent files.

- Files: `plugin/agents/red-team-agent/SKILL.md`, `plugin/agents/blue-team-agent/SKILL.md`,
  `plugin/agents/diff-validation-agent/SKILL.md`, `plugin/skills/skill-builder-agent/first-use.md`

## Post-conditions

- [ ] `plugin/agents/red-team-agent/SKILL.md` exists with `target_type` input field and generic "Target
  Content" section header
- [ ] `plugin/agents/blue-team-agent/SKILL.md` exists with `target_type` input field and target-type dispatch
  patch procedures for `skill_instructions`, `test_code`, and `source_code`
- [ ] `plugin/agents/diff-validation-agent/SKILL.md` exists and verifies blue-team patch hunks against
  red-team findings, exiting non-zero on unpatched CRITICAL/HIGH findings
- [ ] `findings.json` schema documented in red-team-agent includes `target_type` field at top level and
  `disputed` array
- [ ] `plugin/skills/skill-builder-agent/first-use.md` Step 4 delegates to `cat:red-team-agent` and
  `cat:blue-team-agent` instead of containing inline prompts
- [ ] `plugin/skills/skill-builder-agent/first-use.md` Step 4 invokes `cat:diff-validation-agent` after
  each blue-team round and aborts if validation fails
- [ ] No inline red-team or blue-team prompt text remains in `first-use.md` Step 4
- [ ] The existing blue-team dispute mechanism (from `2.1-add-blue-team-dispute-mechanism`) is preserved in
  `blue-team-agent/SKILL.md`

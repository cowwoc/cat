# Plan: add-auto-skill-dependency-to-add-wizard

## Goal
Enhance the /cat:add wizard so that when a new issue involves modifying a skill file, the wizard automatically
identifies other open issues that use that skill and suggests adding them as dependents (i.e., they should depend on
the new issue completing first).

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Determining which issues "use" a skill requires heuristic analysis (scanning PLAN.md files for skill references); false positives possible
- **Mitigation:** Present auto-detected dependencies as suggestions, not mandatory — user confirms via AskUserQuestion

## Files to Modify
- `plugin/skills/add-agent/first-use.md` — Add skill-dependency detection step after issue name validation
- `plugin/hooks/handlers/` — Potentially add a handler to scan for skill references across open issues

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Research
- Determine how to detect which issues reference a given skill (scan PLAN.md files for skill names in Files to Modify, execution context references, or skill invocations)
  - Files: `.claude/cat/issues/v2/v2.1/*/PLAN.md` (sample scan)

### Wave 2: Implementation
- Add a step to add-agent/first-use.md that triggers when the issue description or files-to-modify mention a skill file
- Scan open issues for references to that skill and suggest adding them as dependents
- Present findings via AskUserQuestion for user confirmation
  - Files: `plugin/skills/add-agent/first-use.md`

### Wave 3: E2E Verification
- Write a Bats test in `plugin/skills/add/tests/` that exercises the `issue_detect_skill_deps` step end-to-end: sets up
  a temporary ISSUES_DIR with two open PLAN.md stubs referencing `plugin/skills/add/` and one closed stub, invokes the
  bash detection block extracted from `plugin/skills/add/first-use.md`, and asserts that exactly the two open issues
  are returned in `SKILL_DEPENDENT_ISSUES` with the closed issue excluded. The test must pass (`bats`) before the issue
  can be closed.
  - Files: `plugin/skills/add/tests/skill_dep_detection.bats`

### Wave 4: Fix Architecture and Testing Gaps (Iteration 3 Concerns)

#### CRITICAL: Test/Production Sync Architecture
- Extract the three bash helper blocks from `first-use.md` (skill name extraction, single-skill detection loop, STATE.md
  update) into a sourced shell script at `plugin/skills/add-agent/skill_dep_helpers.sh` that is `source`d by
  `first-use.md` at runtime. Update `skill_dep_detection.bats` to `source` the same script directly instead of
  hand-copying the logic. This ensures tests always run the production code — divergence becomes impossible.
  - Files: `plugin/skills/add-agent/skill_dep_helpers.sh` (new), `plugin/skills/add-agent/first-use.md`,
    `plugin/skills/add/tests/skill_dep_detection.bats`

#### HIGH: Fix `local` Keyword Used at Script Scope
- Replace `local already_found=false` at line 492 of `first-use.md` (inside a `<step>` markdown block but NOT inside a
  bash function) with a plain assignment `already_found=false`. Audit all other `local` declarations in
  `first-use.md` step blocks and remove `local` from any that appear outside function bodies.
  - Files: `plugin/skills/add-agent/first-use.md`

#### HIGH: Cover Multi-Skill Outer Loop in Tests
- Add a `run_detection_multi_skill()` test helper to `skill_dep_detection.bats` that calls the detection logic with
  `SKILL_NAMES` set to two or more skills. Assert that the resulting `SKILL_DEPENDENT_ISSUES` list is deduplicated (an
  issue referencing both skills appears only once) and that issues referencing neither skill are excluded.
  - Files: `plugin/skills/add/tests/skill_dep_detection.bats`

#### HIGH: Add Workflow Integration Test
- Add a Bats test in `plugin/skills/add/tests/skill_dep_detection.bats` named `@test "full wizard flow: detect →
  update STATE.md → confirm prompt"` that sources `skill_dep_helpers.sh`, prepopulates a temp `ISSUES_DIR` with two
  open PLAN.md stubs, runs the full detection+update sequence, and asserts both that `STATE.md` contains the correct
  `Dependencies:` line AND that `AUTO_DETECTED_DEPS` is populated for the confirmation AskUserQuestion step.
  - Files: `plugin/skills/add/tests/skill_dep_detection.bats`

#### MEDIUM: Document Multi-Skill Loop in Prose
- Add a prose paragraph to the architecture section of `first-use.md` (above the bash code block) explaining the
  two-level loop: (1) outer loop iterates over each skill name extracted from the issue description, (2) inner loop
  scans all open issues for references to that skill, accumulating results with deduplication.
  - Files: `plugin/skills/add-agent/first-use.md`

#### MEDIUM: Add Test Cases for Dashed Skill Names and Idempotency
- Add two test cases to `skill_dep_detection.bats`:
  1. `@test "extract_skill_names handles dashes: 'my-skill' detected"` — verifies skill names containing hyphens are
     extracted correctly.
  2. `@test "update_state_dependency is idempotent when Dependencies already has multiple entries"` — sets up a
     `STATE.md` with two existing dependency entries and calls the update function; asserts the file is unchanged.
  - Files: `plugin/skills/add/tests/skill_dep_detection.bats`

#### MEDIUM: Resolve Variable Naming Inconsistency
- Rename all uses of `SKILL_DEPENDENT_ISSUES` to `AUTO_DETECTED_DEPS` (or vice versa — pick one) in both
  `first-use.md` and `skill_dep_detection.bats` so the same concept has exactly one name across skill and tests.
  - Files: `plugin/skills/add-agent/first-use.md`, `plugin/skills/add/tests/skill_dep_detection.bats`

#### MEDIUM: Document extract_skill_names Limitations
- Add a `# Limitations:` comment block directly above the `extract_skill_names` function in `skill_dep_helpers.sh`
  listing: (1) only matches explicit `plugin/skills/X` path patterns, (2) does not match phrase patterns such as
  "modify X skill" or "update the X skill", (3) case-sensitive match only.
  - Files: `plugin/skills/add-agent/skill_dep_helpers.sh`

#### MEDIUM: Replace subprocess calls with parameter expansion
- Replace all `$(basename "$var")` and `$(dirname "$var")` calls in `skill_dep_helpers.sh` with bash parameter
  expansion equivalents (`${var##*/}` and `${var%/*}`) to eliminate subprocess forks in the hot detection loop.
  - Files: `plugin/skills/add-agent/skill_dep_helpers.sh`

### Wave 5: Fix Critical Bugs Found in Iteration 3 Stakeholder Review

#### CRITICAL: Fix Wrong source Path in first-use.md (line 486)
- **Current:** `source "${CLAUDE_PLUGIN_ROOT}/plugin/skills/add-agent/skill_dep_helpers.sh"`
- **Correct:** `source "${CLAUDE_PLUGIN_ROOT}/skills/add-agent/skill_dep_helpers.sh"`
- **Reason:** `CLAUDE_PLUGIN_ROOT` points to the deployed plugin cache root (e.g., `.../plugins/cache/cat/cat/2.1`),
  which contains `skills/` directly, not `plugin/skills/`. The extra `plugin/` prefix breaks the path at runtime.
  - Files: `plugin/skills/add/first-use.md`

#### HIGH: Replace Duplicated apply Logic with Shared Function Call (lines 1005–1023)
- **Problem:** The `issue_create_files` step re-implements the exact logic of `update_state_dependency()` from
  `skill_dep_helpers.sh`: idempotency guard via `grep`, sed escaping of the issue ID, and two-branch sed replacement
  for empty vs non-empty `Dependencies` list.
- **Fix:** Delete the duplicated bash block (lines 1005–1023) and replace it with a single call to the shared
  function: `update_state_dependency "$STATE_FILE" "$NEW_ISSUE_ID"`
  - Files: `plugin/skills/add/first-use.md`

## Post-conditions
- [ ] When creating an issue that modifies a skill file, the wizard identifies open issues using that skill
- [ ] Auto-detected dependent issues are presented as suggestions (not auto-applied)
- [ ] User can accept, modify, or skip the suggested dependencies
- [ ] No false positives on issues that don't reference the skill
- [ ] E2E: Create a test issue that modifies a skill file and confirm the wizard suggests dependent issues

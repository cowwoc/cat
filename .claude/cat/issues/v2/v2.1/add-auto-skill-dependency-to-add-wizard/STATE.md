# State

- **Status:** closed
- **Resolution:** implemented with stakeholder review fixes applied (iterations 1-3)
- **Progress:** 100%
- **Dependencies:** []
- **Blocks:** []
- **Target Branch:** v2.1

## Stakeholder Review Fixes Applied (2026-03-11)

**Concerns addressed (11 total):**
1. Fixed STATUS sed command to strip trailing whitespace (sed 's/[[:space:]]*$//')
2. Added outer loop structure showing multi-skill deduplication with matching arrays
3. Added E2E integration test exercising full flow (extract → detect → update)
4. Added warning comments above helper functions explaining mirroring requirement
5. Added test for missing STATUS field (conservative: treated as non-closed)
6. Added test for missing PLAN.md file (guard prevents inclusion)
7. Extended extract_skill_names test coverage (add a step, change, fix, extend patterns)
8. Added whitespace boundary tests (leading, trailing, multiple Status lines)
9. Added sed escaping tests for special characters (/, &)
10. Replaced unnecessary `basename "$ISSUE_NAME"` with `$ISSUE_NAME`
11. Added comment explaining extract_skill_names is reference approximation of LLM behavior

**Test status:** All 41 tests passing (8 new from iteration 3)

## Post-Close Bug Fixes (2026-03-11)

1. Removed extra `plugin/` path segment from `source "${CLAUDE_PLUGIN_ROOT}/plugin/skills/add-agent/skill_dep_helpers.sh"` → correct path is `${CLAUDE_PLUGIN_ROOT}/skills/add-agent/skill_dep_helpers.sh`
2. Replaced duplicated inline sed idempotency/escaping block in `issue_create_files` with single call to `update_state_dependency "$STATE_FILE" "$NEW_ISSUE_ID"` (DRY: helper already encapsulates this logic)

## Red-Team Loophole Fixes (2026-03-11)

**Loopholes discovered and fixed:**

1. **Idempotency guard false negative (HIGH):** Changed `grep -qF` substring match to full-word match in Dependencies line using regex boundary check `(\[|, )${new_issue_id}(\]|,)`. Prevents substring collisions (e.g., v2.1-add matching v2.1-add-auto-skill-dependency-to-add-wizard).

2. **Index desync in "Yes, let me choose" path (HIGH):** Clarified in first-use.md that selected issue IDs must ALWAYS be looked up in the original AUTO_DETECTED_DEPS array to find their index, then use that index to get the corresponding path. Prevents wrong paths being associated with selected issues due to naive positional index copying.

3. **MATCHING_ISSUES scope clarification (DOCUMENTATION FIX):** Added explicit comment in skill_dep_helpers.sh explaining that MATCHING_ISSUES and MATCHING_ISSUE_PATHS are intentionally global and must be called directly (not via process substitution). Added clarification at call site in first-use.md that direct call is required to propagate variables to caller's scope.

**Files modified:**
- plugin/skills/add/first-use.md (issue_detect_skill_deps step: multi-skill loop with dedup, source helpers)
- plugin/skills/add/tests/skill_dep_detection.bats (sources production helpers, 8 additional tests)
- plugin/skills/add-agent/skill_dep_helpers.sh (new: shared bash helpers sourced by first-use.md and tests)

## Iteration 3 Fixes (2026-03-11)

1. Extracted bash helpers (extract_skill_names, run_detection, update_state_dependency) to
   plugin/skills/add-agent/skill_dep_helpers.sh — tests now run production code directly
2. Replaced `local already_found=false` (script-scope, outside functions) with plain assignment
3. Added run_detection_multi_skill() tests covering outer loop deduplication (2 tests)
4. Added full workflow integration test (detect → select → update → idempotency confirm)
5. Added prose documentation of the two-level loop in first-use.md
6. Added dashed skill name tests (my-skill, my-other-skill, add-agent) (3 tests)
7. Added idempotency tests with multiple existing dependencies (2 tests)
8. Renamed SKILL_DEPENDENT_ISSUES → AUTO_DETECTED_DEPS throughout first-use.md
9. Added Limitations comment block to extract_skill_names in skill_dep_helpers.sh
10. Replaced $(basename)/$(dirname) subprocess calls with bash parameter expansion in run_detection

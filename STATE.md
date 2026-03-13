# State

- **Status:** closed
- **Progress:** 100%
- **Dependencies:** []
- **Blocks:** []
- **Last Updated:** 2026-03-12
- **Resolution:** implemented
- **Completed:** 2026-03-12

## Stakeholder Review Fixes Applied (2026-03-09)

**Concerns addressed:**
- Fixed @{CLAUDE_PLUGIN_ROOT} syntax errors → ${CLAUDE_PLUGIN_ROOT}
- Removed Python references per bash-only policy
- Added SKILL_DRAFT validation requirements
- Added constraint verification for design subagent

**Files modified:**
- plugin/skills/skill-builder-agent/first-use.md
- plugin/skills/skill-builder-agent/skill-conventions.md

## Additional Stakeholder Review Fixes Applied (2026-03-10)

**Concerns addressed:**
- Reordered Prevention Strength Gate section to appear before Recording Format
- Merged redundant exception paragraphs (prior prevention unvalidated cache scenario)
- Shortened JSON test prompt strings to fit within 120-character line limit
- Added three missing test scenarios (biased_rca, too_weak, unknown cause type)
- Added WORKTREE_PATH explanation in test script

**Files modified:**
- plugin/skills/learn/rca-methods.md
- plugin/skills/learn/rca-prevention-gate-test.md

## Final Stakeholder Review Fixes Applied (2026-03-10)

**Concerns addressed (11 total):**
1. Fixed substring matching bug: grep -qF → grep -qxF for exact line matching
2. Implemented robust path normalization using realpath with fallback prefixes
3. Clarified phases_executed must contain exactly three unique strings, any order
4. Added requirement to check ALL fields before reporting errors
5. Fixed timestamp comparison off-by-one: -le → -lt (excludes exact spawn time)
6. Added documentation comment explaining mktemp safety via random suffix
7. Updated empty object validation to require documented phase output format fields
8. Added cross-field validation: prevention_implemented=true requires non-null prevention_commit_hash
9. Consolidated error tables, removed duplicates, added reference from Step 5 to Step 4
10. Standardized error message format: ERROR prefix + context + resolution guidance
11. Added note explaining SPAWN_EPOCH variable persistence in main agent execution context

**Files modified:**
- plugin/skills/learn/first-use.md (Step 3, Step 4a, Step 4b Check 2, Step 4b Check 3, Step 4c, Step 4 error table, Step 5 error table)

## Security Hardening Fixes Applied (2026-03-10)

**Concerns addressed (8 total):**
1. Added commit hash hexadecimal pattern validation before git commands (injection prevention)
2. Added COMMIT_TIME numeric validation before arithmetic comparison (empty string safety)
3. Fixed PREVENTION_PATH prefix-strip: literal `${CLAUDE_PROJECT_DIR}` replaced with expanded variable
4. Added path traversal and metacharacter rejection for PREVENTION_PATH
5. Removed redundant PREVENTION_PATH empty check from Step 4b (guaranteed by Step 4a)
6. Clarified phases_executed validation must check for all three specific values, not just array length
7. Added quoted-assignment security note for PHASE3_JSON variable
8. Removed stale A/B Test section (retired per Method C standardization)

**Files modified:**
- plugin/skills/learn/first-use.md (Step 3, Step 4a, Step 4b Checks 1-3, Step 4c, error table, A/B Test section)

## Stakeholder Review Fixes Applied (2026-03-11)

**CRITICAL concern addressed:**
- Added 11 comprehensive unit tests for resume/continue keyword stripping in WorkPrepareTest.java
- Extracted keyword stripping logic into public parseRawArguments() method for testability
- All 2387 tests pass, covering edge cases: multiple spaces, case sensitivity, bare keywords, argument matching

**HIGH concerns addressed (4 total):**
1. **Skill-grader-agent SHA/path contract:** Documented that grader returns SHA only; main agent reconstructs
   path using deterministic naming convention `eval-artifacts/<SESSION_ID>/grading/<case-id>-<config>.json`
2. **Grader concurrent git lock safety:** Documented that graders must retry `git commit` up to 3 times on
   ref-lock conflicts before returning error (parallel execution safety)
3. **Context isolation validation:** Documented that SESSION_ID and EVAL_ARTIFACTS_DIR are passed as
   pre-resolved literal strings to all subagents to prevent concurrent session collisions
4. **git show error handling:** Documented that skill-analyzer-agent must return error JSON immediately
   if benchmark JSON cannot be read, is malformed, or path absent at commit (fail-fast principle)

**Files modified:**
- client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java (extract parseRawArguments() method)
- client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java (add 11 test cases)
- plugin/skills/skill-builder-agent/first-use.md (Step 3: document contracts and isolation)
- plugin/agents/skill-analyzer-agent/SKILL.md (Step 1 & error handling: document git show failures)

## Additional Stakeholder Review Fixes Applied (2026-03-11)

**Concerns addressed (3 total):**
1. **E2E trace header clarity (MEDIUM):** Updated e2e-dispute-trace.md lines 8-9 to accurately describe the
   document as a "design-time simulation" rather than "runtime evidence" since all commit hashes are fabricated.
2. **Termination check logic correction (MEDIUM):** Fixed incorrect findings.json read source by inserting Round 2
   Red-Team Phase section showing red-team finding no new loopholes and returning commit ghi9012. Updated
   Termination Check to read from round-2 red-team commit (ghi9012) instead of blue-team commit (def5678).
   Updated Summary section verification item (d) and Conclusion items 6-8 to reflect correct round count.
3. **Blue-team prompt clarity (LOW):** Updated blue-team prompts in first-use.md (lines 358-359 and 478-479) to
   conditionally describe writing the skill file: "If any findings were patched, also write the revised skill
   file" instead of unconditionally implying the skill file is always committed.

**Files modified:**
- plugin/skills/skill-builder-agent/e2e-dispute-trace.md (header, Round 2 section, Termination Check, Summary verification, Conclusion)
- plugin/skills/skill-builder-agent/first-use.md (round 1 blue-team prompt, round 2+ blue-team prompt)

## Post-Closure Bugfix (2026-03-12)

**Restored SHA+path input model** after closure identified context minimization pattern.

During stakeholder review, the input model was changed to inline benchmark JSON and skill text content into an
envelope object. This pattern violated subagent-context-minimization principles by relaying full file content through
the main agent's context instead of letting the subagent read from git directly.

**Restored to original design:**
- skill-analyzer-agent now receives benchmark SHA+path (commit hash and relative file path)
- skill-analyzer-agent reads benchmark JSON via `git show <SHA>:<path>` (not inlined)
- skill-text_path parameter replaces skill_text — subagent reads skill file content from git/disk (not inlined)
- Delegation Opportunity and Content Relay Anti-Pattern checks now skip when skill_text_path is absent
- Updated Step 1 validation to read from git and fail-fast if git show fails
- Updated error handling and verification sections to reflect git-based reading

**Files modified:**
- plugin/agents/skill-analyzer-agent/SKILL.md (Inputs, Step 1, Steps 5-6, error handling, verification)
- plugin/skills/skill-builder-agent/first-use.md (analyzer invocation, verification checklist)

## Post-Closure Bugfix (2026-03-12)

**Fixed corrupt issue directory detection logic** to flag directories missing PLAN.md regardless of STATE.md presence.

The original implementation only flagged directories as corrupt when STATE.md existed AND PLAN.md was missing.
This missed the case where an issue directory had neither STATE.md nor PLAN.md — the directory is corrupt
and should be flagged, regardless of whether STATE.md is present.

**Changes made:**
1. **IssueDiscovery.java (line 730, 1074):** Changed `isCorrupt = !statePath AND !planPath` to `isCorrupt = !planPath`
2. **IssueDiscovery.Found record:** Removed mutual exclusivity constraint between `isCorrupt` and `createStateMd`;
   both can now be true when a directory has neither file
3. **GetCleanupOutput.java:** Added `VERSION_DIR_PATTERN` to distinguish version directories (v2, v2.1) from
   issue directories; flag any non-version directory missing PLAN.md as corrupt
4. **Tests:** Updated mutual exclusivity test to verify both flags can be true; added tests for directory
   with neither STATE.md nor PLAN.md being detected as corrupt in both IssueDiscovery and GetCleanupOutput

**Files modified:**
- client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java
- client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java
- client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java
- client/src/test/java/io/github/cowwoc/cat/hooks/test/GetCleanupOutputTest.java

**Test results:** All 2403 tests pass

## Task Completion Cleanup (2026-03-12)

**Removed TDD artifact files** that were committed during development:
- diff-validation-1.json through diff-validation-7.json
- findings.json

These test validation files were used during test-driven development but should not remain in the final branch.

## Stakeholder Review Fixes Applied (2026-03-13)

**Concerns addressed (4 total):**
1. **Legal header exemption violation (CRITICAL):** Removed HTML comment license header from plugin/agents/plan-review-agent.md
   (exemption: agents/*.md files are injected into subagent context verbatim; license headers waste tokens)
2. **Agent frontmatter completeness (DESIGN):** Added required frontmatter fields to plan-review-agent.md
   - `name: plan-review-agent` for agent identification
   - `description: "Plan completeness reviewer..."` for agent purpose documentation
   - Changed `model: claude-sonnet-4-6` to `model: sonnet` to match convention
3. **UX progress visibility (MEDIUM):** Added explicit progress message instructions to plan-builder-agent/first-use.md
   - Verdict YES: Display `✓ Plan review passed (iteration {ITERATION})`
   - Verdict NO: Display `⏳ Plan review iteration {ITERATION}: {gap_count} gaps found, refining...` with rendered gaps
   - Loop continuation: Display `⏳ Spawning review iteration {ITERATION}...`
   - Iteration cap: Display warning message when 3-iteration cap is reached

**Files modified:**
- plugin/agents/plan-review-agent.md (lines 1-9: removed license header, updated frontmatter)
- plugin/skills/plan-builder-agent/first-use.md (lines 204-219: added progress messages for verdict outcomes and iteration loop)

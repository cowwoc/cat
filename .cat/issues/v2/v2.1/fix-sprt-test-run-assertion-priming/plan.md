# Plan

## Goal

Fix SPRT test-run agents seeing assertion outputs — extract only Turn 1 prompt section before spawning
test-run agents, delete all existing test-results.json files.

## Problem

Test-run subagents read the entire scenario markdown file (`{TEST_DIR}/{test_case_id}.md`) which contains both
the `## Turn 1` prompt (intended for test-run agents) and the `## Assertions` section (intended only for grader
agents). This primes test-run agents with the exact assertions they will be graded on, violating the SPRT
independence assumption and biasing test results.

## Root Cause

In `plugin/skills/instruction-builder-agent/first-use.md`, the test-run subagent specification at line ~581 says:
"Reads the assigned scenario from `cat {TEST_DIR}/{test_case_id}.md`" — this reads the ENTIRE file including
the `## Assertions` section. The fix is to extract only the `## Turn 1` section content and pass it inline to the
test-run subagent prompt, preventing access to assertions entirely.

## Pre-conditions

(none)

## Files to Modify

- `plugin/skills/instruction-builder-agent/first-use.md` — Update test-run subagent spawn logic to extract
  Turn 1 content only, update file-read prohibitions, update grader to read assertions itself
- Delete 7 `test-results.json` files (listed in Job 1)

## Risk Assessment

- **Risk Level:** MEDIUM
- **Regression Risk:** Test-run subagent behavior changes (reads inline prompt instead of file). Grader
  subagent now reads assertions from file itself. Both subagent isolation rules must be updated consistently.
- **Mitigation:** Existing test structure unchanged. Only the prompt construction and file-read permissions change.

## Jobs

### Job 1

- In `plugin/skills/instruction-builder-agent/first-use.md`, modify the SPRT test-run subagent spawn section
  (around lines 577-624) to change the approach from having the test-run subagent read the scenario file
  directly, to instead having the main agent extract the `## Turn 1` section and pass it inline:

  1. **Add a Turn 1 extraction step before spawning test-run subagents** (insert before line 577):
     Add instruction text that says: Before spawning test-run subagents, extract the Turn 1 content from
     each test case file. For each test case, read `{TEST_DIR}/{test_case_id}.md` and extract only the content
     between `## Turn 1` and the next `##` heading (or end of file). Store this as `TURN1_CONTENT` for use
     in the subagent prompt.

     Provide the exact sed command to extract Turn 1 content:
     ```
     TURN1_CONTENT=$(sed -n '/^## Turn 1$/,/^## /{/^## Turn 1$/d;/^## /d;p}' "{TEST_DIR}/{test_case_id}.md")
     ```

  2. **Update line ~581**: Change from:
     "1. Reads the assigned scenario from `cat {TEST_DIR}/{test_case_id}.md` and executes the scenario prompt
     organically"
     To:
     "1. Receives the Turn 1 prompt content inline in its spawn prompt (extracted by main agent) and executes
     the scenario prompt organically"

  3. **Update lines ~596-598**: Change from:
     "Pass each subagent only scalar references (test case ID, run index, `TEST_DIR`,
     `CLAUDE_SESSION_ID`, model: `TEST_MODEL`) — do NOT embed test case content or assertion arrays inline in
     the prompt. The test-run subagent reads the scenario from `{TEST_DIR}/{test_case_id}.md`."
     To:
     "Pass each subagent scalar references (test case ID, run index, `TEST_DIR`,
     `CLAUDE_SESSION_ID`, model: `TEST_MODEL`) plus the extracted Turn 1 content inline. Do NOT embed
     assertion arrays inline in the prompt. Do NOT pass the full scenario file path — the test-run subagent
     receives ONLY the Turn 1 prompt content, never the full file."

  4. **Update the test-run subagent file-read prohibition** (lines ~600-624): Change the prohibition to
     explicitly FORBID reading the scenario `.md` file. The test-run subagent no longer needs to read it
     since Turn 1 content is passed inline. Update:
     - Remove `{TEST_DIR}/{test_case_id}.md` from the list of permitted reads at line ~613-614
     - Change "(1) `{TEST_DIR}/{test_case_id}.md` (the assigned scenario file) and (2) your own output file"
       to "(1) your own output file at the designated path"
     - Update the overall prohibition text to say: "Do NOT read any file under {TEST_DIR} via any mechanism"
       (remove the exception for the scenario file)

  5. **Update the grader section** (lines ~729-732) if needed: Verify that the grader subagent reads assertions
     from the file itself (line 732: "Receives: the assertion text (read from the `## Assertions` section of
     `{TEST_DIR}/{test_case_id}.md`)"). The grader already receives assertion text — this is correct behavior.
     Ensure the note about the main agent reading assertions for the grader is consistent: the MAIN AGENT reads
     the assertions section and passes them to the grader (the grader does NOT read the file directly).

  6. **Delete all existing test-results.json files** to force re-validation with the fixed approach:
     - `plugin/tests/rules/tee-piped-output/cleanup-rm-f/test-results.json`
     - `plugin/tests/rules/tee-piped-output/run-in-background-exemption/test-results.json`
     - `plugin/tests/rules/tee-piped-output/core-tee-requirement/test-results.json`
     - `plugin/tests/rules/tee-piped-output/stderr-capture-pattern/test-results.json`
     - `plugin/tests/rules/tee-piped-output/log-file-variable-consistency/test-results.json`
     - `plugin/tests/rules/tee-piped-output/brevity-no-exemption/test-results.json`
     - `plugin/skills/plan-before-edit-agent/test/test-results.json`

  7. **Add a Java regression test** in `client/src/test/java/io/github/cowwoc/cat/hooks/test/` that verifies
     the sed-based Turn 1 extraction produces only Turn 1 content and excludes assertions. The test must:
     - Create a temporary test-case `.md` file containing a `## Turn 1` section followed by an `## Assertions`
       section (matching the real test-case format)
     - Run the sed extraction command (`sed -n '/^## Turn 1$/,/^## /{/^## Turn 1$/d;/^## /d;p}'`) against
       the file using `ProcessBuilder`
     - Assert that the extracted output contains the Turn 1 prompt content
     - Assert that the extracted output does NOT contain any assertion content
     - Test the edge case where `## Turn 1` is the last section (no subsequent `##` heading)

  8. **Update index.json** to status: closed, progress: 100%

  - Files: `plugin/skills/instruction-builder-agent/first-use.md`, all 7 `test-results.json` files,
    `.cat/issues/v2/v2.1/fix-sprt-test-run-assertion-priming/index.json`
  - Commit type: `bugfix:`

## Post-conditions

- [ ] Bug fixed: test-run agents no longer see assertion content when executing test scenarios
- [ ] Regression test added: test verifies that test-run agent prompts contain only Turn 1 content
- [ ] All existing test-results.json files deleted to force re-validation with fixed approach
- [ ] No new issues introduced
- [ ] E2E verification: run a sample SPRT test and verify the test-run agent receives only the Turn 1 prompt

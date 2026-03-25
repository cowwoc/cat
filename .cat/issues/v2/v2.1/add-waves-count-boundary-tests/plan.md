## Type

feature

## Goal

Add Bats tests for the WAVES_COUNT boundary detection in `plugin/skills/work-implement-agent/first-use.md` around
line 256, where the wave count grep command boundary conditions are not tested. Tests must cover plans with zero waves,
one wave, multiple waves, and edge cases like malformed wave headers.

## Pre-conditions

- `plugin/skills/work-implement-agent/first-use.md` contains WAVES_COUNT detection via grep at approximately line 256
- Bats test infrastructure is available in the project

## Post-conditions

- Bats tests verify WAVES_COUNT=0 when plan.md has no Sub-Agent Waves section
- Bats tests verify WAVES_COUNT=1 when plan.md has exactly one wave
- Bats tests verify WAVES_COUNT=N correctly for multi-wave plans
- Bats tests verify WAVES_COUNT detection handles edge cases (empty plan.md, malformed headers)
- Bats tests verify the relay prohibition: WAVES_COUNT must not be embedded into subagent prompt text
- All new Bats tests pass
- No regressions in existing work-implement-agent behavior
- E2E verification: confirm WAVES_COUNT is correctly determined and used for subagent orchestration

## Research Findings

### Code Under Test

The canonical command from `plugin/skills/work-implement-agent/first-use.md` line 256:

```bash
WAVES_COUNT=$(grep -c '^### Wave ' "$PLAN_MD") && echo "WAVES_COUNT=${WAVES_COUNT}"
```

Key behaviors of `grep -c`:
- Returns 0 (count) with exit code 1 when no lines match
- Returns N (count) with exit code 0 when N lines match
- The `&&` means `echo` only runs when grep exits 0 (i.e., at least one match)
- When grep exits 1 (no matches), `WAVES_COUNT` is still set to 0 by the `$()` capture, but the `echo` is skipped

The branching logic:
- `WAVES_COUNT` is 0 or 1: single-subagent execution
- `WAVES_COUNT` >= 2: parallel execution

### Existing Test Patterns

Existing Bats test files follow this pattern:
- `setup()` creates `TMPDIR` via `mktemp -d`, creates `PLAN_MD` in TMPDIR
- `teardown()` removes `TMPDIR`
- Helper functions are sourced from `tests/plan-builder-invocation-helper.bash`
- Tests use `run` for capturing exit codes and `$()` for capturing stdout
- License headers are required (shell script format after shebang)

### Relay Prohibition

From `first-use.md` lines 288-304: subagents must read plan.md directly via `PLAN_MD_PATH`. The prompt must NOT
inline `WAVES_COUNT` value, goal text, or Sub-Agent Waves content. Testing this requires verifying that a constructed
prompt string does not contain the literal WAVES_COUNT value embedded as text.

## Sub-Agent Waves

### Wave 1

#### Step 1: Create helper function file `tests/waves-count-helper.bash`

Create a new helper file at `tests/waves-count-helper.bash` with:

- License header (shell script format, copyright 2026)
- Comment block: "Testable shell functions extracted from work-implement-agent WAVES_COUNT detection."
- Function `detect_waves_count()`:
  - Takes one argument: `$1` = path to plan.md
  - Runs the canonical command: `grep -c '^### Wave ' "$1"` capturing the count
  - Since `grep -c` returns exit 1 on zero matches, handle this: `WAVES_COUNT=$(grep -c '^### Wave ' "$1" || true)`
  - Prints `WAVES_COUNT=${WAVES_COUNT}` to stdout
  - Returns 0 always (the function is for counting, not for error detection)
- Function `classify_wave_execution()`:
  - Takes one argument: `$1` = WAVES_COUNT integer
  - If `$1` is 0 or 1, prints `single`
  - If `$1` >= 2, prints `parallel`
  - This tests the branching logic from first-use.md lines 263-267
- Function `build_subagent_prompt()`:
  - Takes two arguments: `$1` = PLAN_MD_PATH, `$2` = WAVES_COUNT
  - Builds a compliant prompt string that passes PLAN_MD_PATH but does NOT embed WAVES_COUNT value
  - Returns the prompt string for relay prohibition testing
  - Correct pattern: `"PLAN_MD_PATH: ${1}"` (path only, no wave count)
  - Incorrect pattern (what we test does NOT happen): embedding `"WAVES_COUNT=${2}"` in the prompt

#### Step 2: Create test file `tests/work-implement-agent-waves-count.bats`

Create the Bats test file at `tests/work-implement-agent-waves-count.bats` with:

- Shebang: `#!/usr/bin/env bats`
- License header (shell script format, copyright 2026)
- Comment block: "Tests for WAVES_COUNT boundary detection in work-implement-agent."
- Comment showing the grep command under test (same pattern as `work-implement-agent-has-steps.bats` lines 9-11)

**setup() function:**
```bash
setup() {
    TMPDIR="$(mktemp -d)"
    PLAN_MD="${TMPDIR}/plan.md"
    HELPER_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")" && pwd)"
    source "${HELPER_DIR}/waves-count-helper.bash"
}
```

**teardown() function:**
```bash
teardown() {
    rm -rf "${TMPDIR:-}"
}
```

**Test cases for `detect_waves_count`:**

1. `@test "detect_waves_count returns 0 for empty plan.md"` — Create empty file, verify output is `WAVES_COUNT=0`
2. `@test "detect_waves_count returns 0 for plan with no waves section"` — Create plan with `## Goal` and
   `## Post-conditions` only, verify `WAVES_COUNT=0`
3. `@test "detect_waves_count returns 1 for plan with one wave"` — Create plan with `## Sub-Agent Waves` and
   `### Wave 1` subsection, verify `WAVES_COUNT=1`
4. `@test "detect_waves_count returns 2 for plan with two waves"` — Create plan with `### Wave 1` and `### Wave 2`,
   verify `WAVES_COUNT=2`
5. `@test "detect_waves_count returns 5 for plan with five waves"` — Create plan with `### Wave 1` through
   `### Wave 5`, verify `WAVES_COUNT=5`
6. `@test "detect_waves_count returns 0 for wrong header level (## Wave 1)"` — Use `## Wave 1` instead of
   `### Wave 1`, verify `WAVES_COUNT=0`
7. `@test "detect_waves_count returns 0 for wrong header level (#### Wave 1)"` — Use `#### Wave 1`, verify
   `WAVES_COUNT=0`
8. `@test "detect_waves_count returns 0 for leading whitespace before ### Wave"` — Use ` ### Wave 1` (space prefix),
   verify `WAVES_COUNT=0`
9. `@test "detect_waves_count returns 0 for missing space after ### Wave"` — Use `### Wave1` (no space before number),
   verify `WAVES_COUNT=0` (the pattern is `^### Wave ` with trailing space)
10. `@test "detect_waves_count counts only ### Wave headers, ignores other ### headers"` — Create plan with
    `### Wave 1`, `### Implementation Notes`, `### Wave 2`, verify `WAVES_COUNT=2`
11. `@test "detect_waves_count returns 0 for whitespace-only file"` — Create file with only spaces/tabs/newlines,
    verify `WAVES_COUNT=0`
12. `@test "detect_waves_count handles plan with Execution Steps instead of waves"` — Create plan with
    `## Execution Steps` and numbered steps but no `### Wave` headers, verify `WAVES_COUNT=0`

**Test cases for `classify_wave_execution`:**

13. `@test "classify_wave_execution returns single for 0 waves"` — Pass 0, verify output is `single`
14. `@test "classify_wave_execution returns single for 1 wave"` — Pass 1, verify output is `single`
15. `@test "classify_wave_execution returns parallel for 2 waves"` — Pass 2, verify output is `parallel`
16. `@test "classify_wave_execution returns parallel for 10 waves"` — Pass 10, verify output is `parallel`

**Test cases for relay prohibition (`build_subagent_prompt`):**

17. `@test "build_subagent_prompt includes PLAN_MD_PATH"` — Verify prompt output contains
    `PLAN_MD_PATH: /tmp/test/plan.md`
18. `@test "build_subagent_prompt does not embed WAVES_COUNT value"` — Verify prompt output does NOT contain the
    literal string `WAVES_COUNT=` or the numeric wave count value as a standalone token

**E2E test cases (detect + classify combined):**

19. `@test "E2E: empty plan yields single execution"` — Create empty plan, run `detect_waves_count`, extract count,
    pass to `classify_wave_execution`, verify `single`
20. `@test "E2E: one-wave plan yields single execution"` — Create plan with `### Wave 1`, run detect, classify,
    verify `single`
21. `@test "E2E: two-wave plan yields parallel execution"` — Create plan with `### Wave 1` and `### Wave 2`, run
    detect, classify, verify `parallel`
22. `@test "E2E: malformed wave headers yield single execution"` — Create plan with `## Wave 1` and `#### Wave 2`
    (wrong levels), run detect, classify, verify `single` (count is 0)

#### Step 3: Run all tests and verify

Run the new test file:
```bash
cd "${WORKTREE_PATH}" && bats tests/work-implement-agent-waves-count.bats
```

Run all existing test files to verify no regressions:
```bash
cd "${WORKTREE_PATH}" && bats tests/
```

All tests must pass with exit code 0.

## Success Criteria

- `tests/waves-count-helper.bash` exists with `detect_waves_count`, `classify_wave_execution`, and
  `build_subagent_prompt` functions
- `tests/work-implement-agent-waves-count.bats` exists with all 22 test cases listed above
- `bats tests/work-implement-agent-waves-count.bats` exits 0 with all tests passing
- `bats tests/` exits 0 with no regressions in existing tests
- License headers present on both new files

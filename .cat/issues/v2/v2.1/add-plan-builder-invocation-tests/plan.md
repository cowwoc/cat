## Type

feature

## Goal

Add Bats tests for the plan-builder-agent invocation logic in `plugin/skills/work-implement-agent/first-use.md` around
line 176, where the conditional invocation of plan-builder is not tested. Tests must verify plan-builder is called with
correct arguments when HAS_STEPS is false, and is NOT called when HAS_STEPS is true.

## Pre-conditions

- `plugin/skills/work-implement-agent/first-use.md` contains plan-builder-agent invocation conditional on HAS_STEPS at
  approximately line 176
- Bats test infrastructure is available in the project
- Existing test file `tests/work-implement-agent-has-steps.bats` covers only the hasSteps grep detection

## Risk Assessment

**Effort:** low
**Risk:** low — pure test addition, no production code changes. Only new files created.

## Files to Modify

| File | Action | Purpose |
|------|--------|---------|
| `tests/plan-builder-invocation-helper.bash` | Create | Testable shell functions extracted from first-use.md logic |
| `tests/work-implement-agent-plan-builder-invocation.bats` | Create | Bats tests for the invocation logic |

## Sub-Agent Waves

### Wave 1: Create helper script and Bats tests

#### Action 1.1: Create `tests/plan-builder-invocation-helper.bash`

Create a helper script that encapsulates the testable Bash logic from `first-use.md` Step 4. This script extracts the
three testable pieces: hasSteps detection, effort extraction from config JSON, and argument string construction.

The file must begin with:

```bash
#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
```

Define these functions:

**`detect_has_steps`** — Takes `$1` as path to plan.md. Runs the grep command from first-use.md line 169:
```bash
grep -qE '^## (Sub-Agent Waves|Execution Steps)' "$1" && echo "true" || echo "false"
```
Prints `true` or `false` to stdout.

**`extract_effort`** — Takes `$1` as raw JSON string (the output of `get-config-output effective`). Extracts the
`effort` field using the exact grep/sed pattern from first-use.md lines 184-185:
```bash
echo "$1" | grep -o '"effort"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"effort"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/'
```
Prints the effort value to stdout. Prints empty string if field not found.

**`build_plan_builder_args`** — Takes `$1` as CAT_AGENT_ID, `$2` as EFFORT, `$3` as ISSUE_PATH. Constructs and prints
the full args string that would be passed to `cat:plan-builder-agent`:
```bash
echo "$1 $2 revise $3 Generate full implementation steps for this lightweight plan. Add Sub-Agent Waves or Execution Steps section with detailed step-by-step implementation guidance."
```

**`should_invoke_plan_builder`** — Takes `$1` as path to plan.md, `$2` as path to `get-config-output` binary, `$3` as
CAT_AGENT_ID, `$4` as ISSUE_PATH. Orchestrates the full conditional flow:
1. Call `detect_has_steps "$1"`. If `true`, print `SKIP` to stdout and return 0.
2. Run `CONFIG=$("$2" effective)`. If exit code is non-zero, print `ERROR: Failed to read effective config` to stderr
   and return 1.
3. Call `extract_effort "$CONFIG"`.
4. Call `build_plan_builder_args "$3" "$EFFORT" "$4"` and print the result prefixed with `INVOKE:` to stdout.

#### Action 1.2: Create `tests/work-implement-agent-plan-builder-invocation.bats`

Create the Bats test file. It must begin with:

```bash
#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
```

**Setup/teardown:**

```bash
setup() {
    TMPDIR="$(mktemp -d)"
    PLAN_MD="${TMPDIR}/plan.md"
    MOCK_BIN="${TMPDIR}/bin"
    mkdir -p "${MOCK_BIN}"

    # Source the helper
    HELPER_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")" && pwd)"
    source "${HELPER_DIR}/plan-builder-invocation-helper.bash"
}

teardown() {
    rm -rf "${TMPDIR:-}"
}
```

**Mock `get-config-output`:** Create a shell script at `${MOCK_BIN}/get-config-output` that reads from a fixture file
`${TMPDIR}/config-output.json` and prints it to stdout. For error tests, create a variant that exits with code 1.

Helper function to create the mock:

```bash
create_config_mock() {
    local json="$1"
    local exit_code="${2:-0}"
    echo "${json}" > "${TMPDIR}/config-output.json"
    cat > "${MOCK_BIN}/get-config-output" << 'SCRIPT'
#!/usr/bin/env bash
if [[ -f "$(dirname "$0")/../config-exit-code" ]]; then
    exit $(cat "$(dirname "$0")/../config-exit-code")
fi
cat "$(dirname "$0")/../config-output.json"
SCRIPT
    chmod +x "${MOCK_BIN}/get-config-output"
    if [[ "$exit_code" -ne 0 ]]; then
        echo "$exit_code" > "${TMPDIR}/config-exit-code"
    else
        rm -f "${TMPDIR}/config-exit-code"
    fi
}
```

**Test cases (exact function names):**

1. `@test "detect_has_steps returns false when plan has no steps section"` — Write plan.md with only `## Goal` and
   `## Post-conditions`. Assert `detect_has_steps` returns `false`.

2. `@test "detect_has_steps returns true when plan has Sub-Agent Waves"` — Write plan.md with `## Sub-Agent Waves`.
   Assert returns `true`.

3. `@test "detect_has_steps returns true when plan has Execution Steps"` — Write plan.md with `## Execution Steps`.
   Assert returns `true`.

4. `@test "extract_effort parses effort from standard config JSON"` — Input:
   `{"effort": "high", "trust": "medium"}`. Assert output is `high`.

5. `@test "extract_effort parses effort value low"` — Input: `{"effort": "low", "trust": "medium"}`. Assert output
   is `low`.

6. `@test "extract_effort parses effort value medium"` — Input: `{"effort": "medium"}`. Assert output is `medium`.

7. `@test "extract_effort returns empty when effort field missing"` — Input: `{"trust": "medium"}`. Assert output is
   empty string.

8. `@test "extract_effort handles whitespace variations in JSON"` — Input: `{ "effort" : "high" }` (extra spaces).
   Assert output is `high`.

9. `@test "build_plan_builder_args constructs correct args string"` — Call with `agent-123`, `high`,
   `/path/to/issue`. Assert output equals
   `agent-123 high revise /path/to/issue Generate full implementation steps for this lightweight plan. Add Sub-Agent Waves or Execution Steps section with detailed step-by-step implementation guidance.`

10. `@test "build_plan_builder_args propagates effort value"` — Call with `agent-456`, `low`, `/other/path`. Assert
    the output contains `low revise`.

11. `@test "should_invoke_plan_builder prints SKIP when hasSteps is true"` — Write plan.md with
    `## Sub-Agent Waves`. Call `should_invoke_plan_builder`. Assert stdout is `SKIP`.

12. `@test "should_invoke_plan_builder prints INVOKE with correct args when hasSteps is false"` — Write plan.md with
    only `## Goal`. Create config mock returning `{"effort": "high"}`. Call
    `should_invoke_plan_builder "${PLAN_MD}" "${MOCK_BIN}/get-config-output" "agent-789" "/issue/path"`. Assert stdout
    starts with `INVOKE:` and contains `agent-789 high revise /issue/path`.

13. `@test "should_invoke_plan_builder returns error when config read fails"` — Write plan.md with only `## Goal`.
    Create config mock with exit code 1. Call `should_invoke_plan_builder`. Assert return code is 1 and stderr
    contains `ERROR: Failed to read effective config`.

14. `@test "should_invoke_plan_builder with effort=low propagates to args"` — Write plan.md with only `## Goal`.
    Create config mock returning `{"effort": "low"}`. Call `should_invoke_plan_builder`. Assert stdout contains
    `low revise`.

15. `@test "full flow: hasSteps=true skips config read entirely"` — Write plan.md with `## Execution Steps`. Do NOT
    create a config mock (leave `${MOCK_BIN}/get-config-output` missing). Call `should_invoke_plan_builder` with the
    missing binary path. Assert stdout is `SKIP` and return code is 0 (proves config binary is never called).

#### Action 1.3: Run the tests

Run:
```bash
bats tests/work-implement-agent-plan-builder-invocation.bats
```

All 15 tests must pass. Fix any failures before proceeding.

#### Action 1.4: Run existing hasSteps tests for regression

Run:
```bash
bats tests/work-implement-agent-has-steps.bats
```

All existing tests must still pass.

## Post-conditions

- `tests/plan-builder-invocation-helper.bash` exists with `detect_has_steps`, `extract_effort`,
  `build_plan_builder_args`, and `should_invoke_plan_builder` functions
- `tests/work-implement-agent-plan-builder-invocation.bats` exists with 15 test cases covering:
  - hasSteps detection (3 tests, complementing existing test file)
  - Effort extraction from config JSON (5 tests including edge cases)
  - Argument string construction (2 tests)
  - Full conditional flow orchestration (5 tests including error case and skip-path proof)
- Both new and existing Bats tests pass with no regressions
- Integration test verifies the complete conditional flow from hasSteps detection through argument construction
- All new files include license headers
- No hardcoded `/workspace/` paths in test files

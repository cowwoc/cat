## Type

feature

## Goal

Add Bats tests for the HAS_STEPS detection logic in `plugin/skills/work-implement-agent/first-use.md` to eliminate a
fragility where the grep-based detection silently fails on section name variants or whitespace changes, causing plan
generation to be skipped with no error. The tests must cover both valid section header variants, the absent-headers
case that triggers plan-builder invocation, and edge cases.

## Pre-conditions

- `plugin/skills/work-implement-agent/first-use.md` contains the HAS_STEPS detection via `grep -qE '^## (Sub-Agent Waves|Execution Steps)'`
- Bats test infrastructure is available in the project

## Post-conditions

- Bats tests verify HAS_STEPS detection correctly identifies `## Sub-Agent Waves` header
- Bats tests verify HAS_STEPS detection correctly identifies `## Execution Steps` header
- Bats tests verify plan-builder invocation is triggered when neither header variant is present in plan.md
- Bats tests verify detection handles edge cases gracefully (whitespace variants, empty file)
- All new Bats tests pass
- No regressions in existing work-implement-agent behavior
- E2E verification: excluded per Alternative B analysis (see Research Findings); isolated Bats unit tests provide more direct verification of regex pattern fragility without runtime dependencies

## Research Findings

### Detection Command Under Test

The exact grep command in `plugin/skills/work-implement-agent/first-use.md` (lines 169-170):

```bash
grep -qE '^## (Sub-Agent Waves|Execution Steps)' "${PLAN_MD}" && \
echo "hasSteps=true" || echo "hasSteps=false"
```

Key properties:
- `^` anchor: header must start at column 0 (no leading whitespace)
- `## ` prefix: exactly two `#` characters followed by a space
- Two valid values: `Sub-Agent Waves` or `Execution Steps`
- `-qE`: quiet mode + extended regex; outputs nothing, exit code only

### Bats Test Infrastructure

Existing tests in `tests/` directory follow this pattern (from `tests/work-prepare-agent-preconditions.bats`):
- `setup()`: creates `TMPDIR=$(mktemp -d)` and `PLAN_MD="${TMPDIR}/plan.md"`
- `teardown()`: `rm -rf "${TMPDIR:-}"`
- Helper function to run the command under test against `PLAN_MD`
- `@test "descriptive name: scenario"` blocks

New test file: `tests/work-implement-agent-has-steps.bats`

### Alternatives Considered

**Alternative A (chosen): Isolated grep command tests**
- Extract the exact grep command from first-use.md and test it directly in a helper function
- Tests are self-contained, fast, no external dependencies
- Directly tests the exact command that runs in production (no translation layer)
- **Selected**: best precision, simplest maintenance

**Alternative B: Integration test via plan-builder invocation**
- Actually run work-implement-agent skill and observe whether plan-builder is called
- Requires full plugin stack, hard to run in unit test context
- Fragile due to runtime dependencies (CLAUDE_PLUGIN_ROOT, session context)
- **Rejected**: too complex, too slow, not appropriate for Bats unit tests

**Alternative C: Mock the grep and test branching logic**
- Replace grep with a mock that returns controlled exit codes
- Would test the branching logic but not the actual pattern
- **Rejected**: doesn't validate the regex pattern itself, which is the fragility source

### Edge Cases

The `^` anchor means these must all return `hasSteps=false`:
- Leading whitespace before `##` (e.g., ` ## Sub-Agent Waves`)
- Wrong header level (e.g., `### Sub-Agent Waves`)
- Partial match (e.g., `## Sub-Agent Waves Extra Text` — actually returns true; regex doesn't require line end)
- Empty file
- Only other `##` headers (e.g., `## Goal`, `## Post-conditions`)

## Sub-Agent Waves

### Wave 1

- Create `tests/work-implement-agent-has-steps.bats` with the following test coverage:
  1. **Valid `## Sub-Agent Waves` header** → `hasSteps=true`
  2. **Valid `## Execution Steps` header** → `hasSteps=true`
  3. **Neither header present (only `## Goal`, `## Post-conditions`)** → `hasSteps=false`
  4. **Empty file** → `hasSteps=false`
  5. **Header with leading whitespace (` ## Sub-Agent Waves`)** → `hasSteps=false` (anchor requires column 0)
  6. **Wrong `###` header level (`### Sub-Agent Waves`)** → `hasSteps=false`
  7. **Both valid headers present** → `hasSteps=true`
  8. **Whitespace-only file** → `hasSteps=false`

  Test file structure:
  - Shebang: `#!/usr/bin/env bats`
  - License header (hash comment format for shell scripts)
  - `setup()`: `TMPDIR=$(mktemp -d)` and `PLAN_MD="${TMPDIR}/plan.md"`
  - `teardown()`: `rm -rf "${TMPDIR:-}"`
  - Helper function `run_detection()` that runs the exact grep command from first-use.md:
    ```bash
    run_detection() {
      grep -qE '^## (Sub-Agent Waves|Execution Steps)' "${PLAN_MD}" && \
      echo "hasSteps=true" || echo "hasSteps=false"
    }
    ```
  - Each `@test` block must: (a) write specific content to `${PLAN_MD}` using `printf` or a heredoc, (b) capture
    output with `result=$(run_detection)`, and (c) assert with `[ "$result" = "hasSteps=true" ]` or
    `[ "$result" = "hasSteps=false" ]` as appropriate. Example structure:
    ```bash
    @test "Sub-Agent Waves header: hasSteps=true" {
      printf '## Sub-Agent Waves\n' > "${PLAN_MD}"
      result=$(run_detection)
      [ "$result" = "hasSteps=true" ]
    }
    ```
  - Apply the same pattern for all 8 test cases, with file content and expected result per case:
    1. `printf '## Sub-Agent Waves\n'` → `"hasSteps=true"`
    2. `printf '## Execution Steps\n'` → `"hasSteps=true"`
    3. `printf '## Goal\n## Post-conditions\n'` → `"hasSteps=false"`
    4. `printf ''` (empty file, i.e., `> "${PLAN_MD}"`) → `"hasSteps=false"`
    5. `printf ' ## Sub-Agent Waves\n'` (leading space) → `"hasSteps=false"`
    6. `printf '### Sub-Agent Waves\n'` (wrong level) → `"hasSteps=false"`
    7. `printf '## Sub-Agent Waves\n## Execution Steps\n'` → `"hasSteps=true"`
    8. `printf '   \n\t\n'` (whitespace-only) → `"hasSteps=false"`
  - Run Bats tests to confirm they pass: `bats tests/work-implement-agent-has-steps.bats`
  - Update `.cat/issues/v2/v2.1/add-has-steps-detection-tests/index.json` in the same commit:
    set `"status"` to `"closed"` and `"progress"` to `100`

  Commit type: `test:` (new test file in tests/)

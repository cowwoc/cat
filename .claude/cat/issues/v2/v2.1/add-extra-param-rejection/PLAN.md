# Add Extra Parameter Rejection to All Scripts

## Type
Refactor

## Goal
Add fail-fast validation to all scripts in `plugin/scripts/` so they reject unexpected extra positional parameters.
If a script expects N parameters, passing N+1 should produce a clear error and exit immediately.

## Problem
Several scripts silently ignore extra parameters. This masks invocation errors — a caller passing wrong arguments gets
no feedback, potentially causing subtle bugs. Python scripts using `argparse` already reject extras automatically, but
bash scripts generally do not.

## Risk
- LOW: Adding validation guards that only trigger on incorrect invocations
- No behavioral change for correct callers

## Scope

Scripts that need extra-parameter rejection added (bash scripts without `$#` validation):

| Script | Expected Args | Current Validation |
|--------|--------------|-------------------|
| `check-hooks-loaded.sh` | 2 positional | None |
| `get-cleanup-survey.sh` | 0-1 named options | None |
| `get-help-display.sh` | 0 | None |
| `get-render-diff.sh` | 0+ named options | None |
| `get-token-report.sh` | 0-1 named options | None |
| `get-work-boxes.sh` | 0 | None |
| `validate-status-alignment.sh` | 0 (reads stdin) | None |

**Excluded:** `load-skill.sh` is covered by issue `fix-load-skill-env-resolution`.

**Already covered:** Python scripts using `argparse` reject extra args automatically. Bash scripts with existing `$#`
checks already validate parameter counts.

## Acceptance Criteria
- [ ] All 7 scripts listed in Scope are modified with parameter count or unknown-argument validation
- [ ] Each modified script exits with code 1 and prints error to stderr when extra/unknown parameters are passed
- [ ] Existing correct invocations continue to work (no behavioral change for valid calls)
- [ ] `load-skill.sh` is NOT modified (covered by separate issue)

## Files to Modify
- `plugin/scripts/check-hooks-loaded.sh`
- `plugin/scripts/get-cleanup-survey.sh`
- `plugin/scripts/get-help-display.sh`
- `plugin/scripts/get-render-diff.sh`
- `plugin/scripts/get-token-report.sh`
- `plugin/scripts/get-work-boxes.sh`
- `plugin/scripts/validate-status-alignment.sh`

## Execution Steps

### Step 1: Add parameter count validation to each script

For scripts that accept 0 parameters, add near the top (after shebang and set options):

```bash
if [[ $# -gt 0 ]]; then
  echo "ERROR: $(basename "$0") accepts no arguments, got $#" >&2
  exit 1
fi
```

For scripts with positional args (e.g., `check-hooks-loaded.sh` expecting exactly 2):

```bash
if [[ $# -ne 2 ]]; then
  echo "ERROR: $(basename "$0") requires exactly 2 arguments, got $#" >&2
  exit 1
fi
```

For scripts with named options parsed via `while` loops, replace any silent `*) shift ;;` catch-all with:

```bash
*)
  echo "ERROR: $(basename "$0"): unknown argument: $1" >&2
  exit 1
  ;;
```

### Step 2: Verify each script still works with correct invocations

Test each modified script with its normal arguments to confirm no regressions.

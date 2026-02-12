# Fix load-skill.sh Environment Variable Resolution

## Type
Bugfix

## Goal
Fix `load-skill.sh` to fail-fast when required parameters are missing, and remove the unnecessary `$(pwd)` 4th
argument workaround from SKILL.md files that pass it redundantly.

## Problem
`load-skill.sh` line 8 uses `CLAUDE_PROJECT_DIR="${4:-}"` which silently clears the `CLAUDE_PROJECT_DIR` environment
variable (already set by Claude Code) when no 4th argument is passed. This means `${CLAUDE_PROJECT_DIR}` template
substitution in content.md files resolves to empty string for 40+ skills.

Two skills (`audit-plan`, `status`) work around this by passing `$(pwd)` as a 4th argument, but this is incorrect
because `$(pwd)` returns the current directory (which may be a worktree), not the project root.

## Risk
- LOW: Single script change with clear behavior
- All skills already work because `CLAUDE_PROJECT_DIR` is set in the environment; the bug is that `load-skill.sh`
  overwrites it

## Acceptance Criteria
- [ ] `load-skill.sh` exits with code 1 and prints error to stderr if `CLAUDE_PLUGIN_ROOT` (arg 1) is missing
- [ ] `load-skill.sh` exits with code 1 and prints error to stderr if `SKILL` (arg 2) is missing
- [ ] `load-skill.sh` exits with code 1 and prints error to stderr if `CLAUDE_SESSION_ID` (arg 3) is missing
- [ ] `load-skill.sh` no longer reads a 4th positional argument — `CLAUDE_PROJECT_DIR` comes only from the environment
- [ ] `load-skill.sh` exits with code 1 and prints error to stderr if `CLAUDE_PROJECT_DIR` env var is unset or empty
- [ ] `audit-plan/SKILL.md` no longer passes `$(pwd)` as 4th argument
- [ ] `status/SKILL.md` no longer passes `$(pwd)` as 4th argument

## Files to Modify
- `plugin/scripts/load-skill.sh` — Add fail-fast parameter validation, remove 4th arg handling
- `plugin/skills/audit-plan/SKILL.md` — Remove `$(pwd)` 4th argument
- `plugin/skills/status/SKILL.md` — Remove `$(pwd)` 4th argument

## Execution Steps

### Step 1: Update load-skill.sh parameter validation

Replace lines 5-9 with fail-fast validation:

```bash
# Required parameters - fail fast if missing
if [[ -z "${1:-}" ]]; then
  echo "ERROR: CLAUDE_PLUGIN_ROOT (arg 1) is required" >&2
  exit 1
fi
if [[ -z "${2:-}" ]]; then
  echo "ERROR: SKILL name (arg 2) is required" >&2
  exit 1
fi
if [[ -z "${3:-}" ]]; then
  echo "ERROR: CLAUDE_SESSION_ID (arg 3) is required" >&2
  exit 1
fi
if [[ -z "${CLAUDE_PROJECT_DIR:-}" ]]; then
  echo "ERROR: CLAUDE_PROJECT_DIR environment variable is not set" >&2
  exit 1
fi

CLAUDE_PLUGIN_ROOT="$1"
SKILL="$2"
CLAUDE_SESSION_ID="$3"
```

Remove the old `CLAUDE_PROJECT_DIR="${4:-}"` and `export CLAUDE_PROJECT_DIR` lines. The variable is already exported
by the Claude Code environment.

### Step 2: Remove $(pwd) from audit-plan/SKILL.md

Change:
```
!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" audit-plan "${CLAUDE_SESSION_ID}" "$(pwd)"`
```
To:
```
!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" audit-plan "${CLAUDE_SESSION_ID}"`
```

### Step 3: Remove $(pwd) from status/SKILL.md

Change:
```
!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" status "${CLAUDE_SESSION_ID}" "$(pwd)"`
```
To:
```
!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" status "${CLAUDE_SESSION_ID}"`
```

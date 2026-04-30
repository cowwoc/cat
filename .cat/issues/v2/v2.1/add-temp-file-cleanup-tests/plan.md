## Type

feature

## Goal

Add Bats tests for the temporary file cleanup logic in `plugin/skills/add-agent/first-use.md` around line 956, where
`plan_temp_file` cleanup on error paths is not tested. Tests must verify the temp file is cleaned up both on success
and on error paths.

## Pre-conditions

- `plugin/skills/add-agent/first-use.md` contains temp file creation and cleanup logic for `plan_temp_file`
- Bats test infrastructure is available in the project (`tests/` directory with `.bats` files)

## Post-conditions

- Bats tests verify `plan_temp_file` is removed on the successful completion path
- Bats tests verify `plan_temp_file` is removed when an error occurs during plan creation
- Bats tests verify no temp file leaks after any code path through the temp file creation workflow
- All new Bats tests pass
- No regressions in existing add-agent behavior
- E2E verification: test confirms that `rm -f` with a `.md`-suffixed temp path leaves no file behind

## Research Findings

### Temp File Pattern (lines 965-1027 in plugin/skills/add-agent/first-use.md)

The temporary plan file is created and cleaned up with this inline Bash pattern:

```bash
# Step 1: Create temp file
plan_temp_file=$(mktemp --suffix=.md)

# Step 2: Write plan content to temp file (via Write tool)
# ...

# Step 3: Call create-issue
# "${CLAUDE_PLUGIN_DATA}/client/bin/create-issue" --json '{... "plan_file": "'"${plan_temp_file}"'" ...}'

# Step 4: Cleanup on error (line 1020)
rm -f "${plan_temp_file}"

# Step 5: Cleanup on success (line 1026)
rm -f "${plan_temp_file}"
```

**Key observations:**
- Variable name is `plan_temp_file` (snake_case), not `PLAN_TEMP_FILE`
- Temp file is created by `mktemp --suffix=.md` → produces paths like `/tmp/tmp.XXXXXX.md`
- Cleanup at line 1020 handles the error path (after `create-issue` fails)
- Cleanup at line 1026 handles the success path (after `create-issue` succeeds)
- The `-f` flag on `rm` prevents errors if the file does not exist
- No `trap` is registered; cleanup is entirely explicit (two `rm -f` calls)
- The post-condition mentions `/tmp/plan-context-*.md` pattern, but actual `mktemp` creates `.md`-suffixed files

### Testability

Since the cleanup logic is three lines of Bash (`plan_temp_file=$(mktemp --suffix=.md)`,
`rm -f "${plan_temp_file}"`, and `rm -f "${plan_temp_file}"`), the appropriate test strategy is:

1. **Unit tests** — isolate and directly invoke the individual Bash commands under test
2. **Simulate both paths** — write helper functions that mimic success and error paths
3. **Verify file state** — assert presence/absence of temp file before and after cleanup

## Execution Steps

### Step 1: Create tests/add-agent-temp-file-cleanup.bats

Create the file `tests/add-agent-temp-file-cleanup.bats` with the following tests:

1. **`mktemp --suffix=.md creates a .md-suffixed temp file`** — verify that `mktemp --suffix=.md` creates a file
   that exists and whose name ends with `.md`.

2. **`success-path cleanup: rm -f removes the temp file`** — simulate the success path by creating a temp file with
   `mktemp --suffix=.md`, then running `rm -f "${plan_temp_file}"`, and asserting the file no longer exists.

3. **`error-path cleanup: rm -f removes the temp file`** — same as above but wrapped in a simulated error path
   (create temp file, simulate error, call `rm -f`, assert gone).

4. **`rm -f on non-existent path does not fail`** — verify that `rm -f /tmp/nonexistent-plan-file.md` exits 0,
   confirming the `-f` flag makes cleanup safe even if the file was never created.

5. **`no temp file remains after success path`** — end-to-end: create temp file, write content to it, call
   `rm -f "${plan_temp_file}"`, assert file is gone and no `.md`-suffixed files leak in `/tmp` with the same name.

6. **`no temp file remains after error path`** — end-to-end: create temp file, simulate a failed command, call
   `rm -f "${plan_temp_file}"`, assert file is gone.

7. **`multiple cleanup calls are idempotent`** — call `rm -f "${plan_temp_file}"` twice on the same path (once
   after it exists, once after it is already removed); both calls must exit 0.

8. **`temp file path contains .md suffix (matches mktemp pattern)`** — verify the path returned by
   `mktemp --suffix=.md` ends with `.md`, confirming it matches the `.md`-suffixed pattern described in the skill.

Include:
- License header (Bash/shell format, `#` comments, after the shebang line)
- `setup()` that creates a per-test temp directory: `TEST_TEMP_DIR="$(mktemp -d)"` (do NOT use `TMPDIR` as the
  variable name — it shadows the system `TMPDIR` env var and breaks `mktemp` in nested calls)
- `teardown()` that runs `rm -rf "${TEST_TEMP_DIR:-}"` to prevent leaks
- Helper function `create_plan_temp_file()` that assigns `plan_temp_file=$(mktemp --suffix=.md)` — in Bats,
  functions execute in the same shell as the test, so `plan_temp_file` is directly accessible in the calling test
  without exporting

### Step 2: Verify all tests pass

Run the new test file from the worktree root (use a relative path, not a hardcoded `/workspace/` absolute path):
```bash
bats tests/add-agent-temp-file-cleanup.bats
```

All 8 tests must pass with no failures.

### Step 3: Commit the new test file and close index.json

Commit the new file (run from the worktree root):
```bash
git add tests/add-agent-temp-file-cleanup.bats && \
git add .cat/issues/v2/v2.1/add-temp-file-cleanup-tests/index.json && \
git commit -m "test: add Bats tests for plan_temp_file cleanup in add-agent"
```

Update `index.json` to `status: closed, progress: 100%` in the SAME commit as the test file.

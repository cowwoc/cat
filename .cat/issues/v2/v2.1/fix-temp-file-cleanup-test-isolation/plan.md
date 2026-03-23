## Type

bugfix

## Goal

Fix design concerns in `tests/add-agent-temp-file-cleanup.bats` identified during stakeholder review:
remove duplicate and redundant tests, fix misleading "error-path" test framing, and align variable
naming with the project convention.

## Pre-conditions

- `tests/add-agent-temp-file-cleanup.bats` exists with 8 tests (merged in 2.1-add-temp-file-cleanup-tests)

## Post-conditions

- No duplicate or redundant test cases remain in `tests/add-agent-temp-file-cleanup.bats`
- Test names accurately describe what each test exercises (no misleading "error-path" framing when
  no actual error-path is simulated)
- `TEST_TEMP_DIR` renamed to `TMPDIR` throughout the file to match the naming convention used in
  `tests/work-implement-agent-has-steps.bats` and `tests/work-prepare-agent-preconditions.bats`
- All remaining tests pass: `bats tests/add-agent-temp-file-cleanup.bats` exits 0
- E2E: run `bats tests/add-agent-temp-file-cleanup.bats` and verify all tests pass with TAP output

## Research Findings

### Current test structure (8 tests)

| # | Name | Issue |
|---|------|-------|
| 1 | mktemp --suffix=.md creates a .md-suffixed temp file | - |
| 2 | success-path cleanup: rm -f removes the temp file | Near-duplicate of test 5 |
| 3 | error-path cleanup: rm -f removes the temp file | Duplicate of test 6; "error-path" is misleading |
| 4 | rm -f on non-existent path does not fail | - |
| 5 | no temp file remains after success path | Supersedes test 2 (adds write step) |
| 6 | no temp file remains after error path | Duplicate of test 3; "error-path" is misleading |
| 7 | multiple cleanup calls are idempotent | - |
| 8 | temp file path contains .md suffix (matches mktemp pattern) | Subset of test 1 |

### Duplicate analysis

- **Tests 2 and 5**: Both assert `rm -f` removes an existing temp file. Test 5 adds a `printf` write
  step but the final assertion is identical. Test 2 is fully covered by test 5 → **remove test 2**.
- **Tests 3 and 6**: Structurally identical (`false || true` then `rm -f`). Test 6 is a duplicate
  of test 3 → **remove test 6**.
- **Tests 1 and 8**: Test 8 asserts only the `.md` suffix, which test 1 already asserts → **remove test 8**.

### Error-path framing

`false || true` in Bats does not simulate error-path behavior. Bats does not run with `set -e`, so
errors never propagate. The test is exercising the same code path as the success tests. The tests
should be renamed to remove the misleading "error-path" / "error path" framing.

Rename test 3 to: `"cleanup after failed command: rm -f removes the temp file"` with the comment
updated to explain that `false || true` simulates a preceding failure without aborting the script
(which is the correct behavior to test — not that set -e propagation is blocked).

Rename test 5 to: `"no temp file remains after writing content to it"` since the write step
(not a success/error framing) is what distinguishes it from other tests.

### Variable naming

Other test files in `tests/` use `TMPDIR` (e.g., `work-implement-agent-has-steps.bats:11`,
`work-prepare-agent-preconditions.bats:11`). Rename `TEST_TEMP_DIR` → `TMPDIR` and update
`create_plan_temp_file()` to use `-p "${TMPDIR}"` accordingly.

**Note:** The original plan.md for 2.1-add-temp-file-cleanup-tests warned against using `TMPDIR`
because it shadows the system env var. However, the project-wide convention already uses `TMPDIR`
in all sibling test files, so alignment with convention takes priority. The `-p "${TMPDIR}"` flag
in `mktemp` calls is unaffected by any system `TMPDIR` shadowing because the `-p` flag takes
explicit precedence.

## Execution Steps

### Step 1: Update tests/add-agent-temp-file-cleanup.bats

Apply the following changes to `tests/add-agent-temp-file-cleanup.bats`:

1. **Rename `TEST_TEMP_DIR` → `TMPDIR`** everywhere in the file (setup, teardown, create_plan_temp_file,
   and test 4). Update `-p "${TEST_TEMP_DIR}"` to `-p "${TMPDIR}"` in `create_plan_temp_file()`.

2. **Remove test 2** (`"success-path cleanup: rm -f removes the temp file"`, lines 31–36).
   It is fully covered by test 5.

3. **Remove test 6** (`"no temp file remains after error path"`, lines ~63–71 after renumbering).
   It is a functional duplicate of test 3.

4. **Remove test 8** (`"temp file path contains .md suffix (matches mktemp pattern)"`, last test).
   It is a strict subset of test 1.

5. **Rename test 3** from `"error-path cleanup: rm -f removes the temp file"` to
   `"cleanup after failed command: rm -f removes the temp file"`. Update the inline comment to:
   `# Simulate a preceding command failure (non-zero exit); the script continues because Bats does not use set -e`.

6. **Rename test 5** from `"no temp file remains after success path"` to
   `"no temp file remains after writing content to it"`. Remove the "Write tool step" comment;
   replace with: `# Write content to the temp file before cleanup`.

After changes, the file should have 5 tests:
1. `mktemp --suffix=.md creates a .md-suffixed temp file`
2. `cleanup after failed command: rm -f removes the temp file`
3. `rm -f on non-existent path does not fail`
4. `no temp file remains after writing content to it`
5. `multiple cleanup calls are idempotent`

### Step 2: Run tests and verify

```bash
cd /workspace && bats tests/add-agent-temp-file-cleanup.bats
```

All 5 tests must pass (exit code 0).

### Step 3: Commit

```bash
git add tests/add-agent-temp-file-cleanup.bats && \
git commit -m "test: remove duplicate tests and fix misleading error-path framing in add-agent-temp-file-cleanup.bats"
```

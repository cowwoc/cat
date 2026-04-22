<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Running Individual SPRT Tests

The `instruction-test-runner` now supports running a subset of SPRT tests via the `run-single-test` command.

## Usage

```bash
instruction-test-runner run-single-test \
  <worktree_path> \
  <test_dir> \
  <test_pattern> \
  <test_model> \
  <project_dir> \
  <session_id>
```

## Parameters

| Parameter | Description |
|-----------|-------------|
| `worktree_path` | Path to the git worktree |
| `test_dir` | Directory containing test files (relative to worktree) |
| `test_pattern` | Test name or glob pattern (see examples below) |
| `test_model` | Model to use for testing (`haiku`, `sonnet`, or `opus`) |
| `project_dir` | Project root directory |
| `session_id` | Claude session ID |

## Pattern Matching

The `test_pattern` parameter supports:
- **Exact match**: `cache_fix_warning_conveyed` - runs only that specific test
- **Glob pattern**: `*warning*` - runs all tests with "warning" in the name
- **Prefix match**: `cache_*` - runs all tests starting with "cache_"

## Examples

### Run a single specific test

```bash
WORKTREE_PATH=$(git rev-parse --show-toplevel)
TEST_DIR="plugin/tests/skills/claude-runner/first-use"

"${WORKTREE_PATH}/client/target/jlink/bin/instruction-test-runner" run-single-test \
  "$WORKTREE_PATH" \
  "$TEST_DIR" \
  "cache_fix_warning_conveyed" \
  "haiku" \
  "$CLAUDE_PROJECT_DIR" \
  "$CLAUDE_SESSION_ID"
```

### Run all tests matching a pattern

```bash
"${WORKTREE_PATH}/client/target/jlink/bin/instruction-test-runner" run-single-test \
  "$WORKTREE_PATH" \
  "$TEST_DIR" \
  "*warning*" \
  "haiku" \
  "$CLAUDE_PROJECT_DIR" \
  "$CLAUDE_SESSION_ID"
```

## Comparison with run-full-sprt

| Feature | `run-full-sprt` | `run-single-test` |
|---------|-----------------|-------------------|
| Test selection | All tests in directory | Filtered by pattern |
| Setup complexity | Requires all 5 parameters | Same 5 parameters + pattern |
| Use case | Full test suite validation | Development/debugging individual tests |
| Speed | Slower (runs all tests) | Faster (runs only matching tests) |

## Output

The command provides the same detailed SPRT output as `run-full-sprt`:
- Test case filtering (shows which tests matched the pattern)
- SPRT loop progress with batch summaries
- Decision boundaries (ACCEPT/REJECT)
- Final results with test SHA

If no tests match the pattern, the command lists all available tests and exits with an error.

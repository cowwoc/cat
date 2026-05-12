<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Running a Filtered SPRT Subset

Use `run-sprt` as the canonical entrypoint for SPRT runs.

## Usage

```bash
"${WORKTREE_PATH}/client/cli/target/jlink/bin/instruction-test-runner" run-sprt \
  <worktree_path> \
  <test_dir> \
  <test_model> \
  <session_id> \
  [<effort>]
```

## Notes

- Invoke the runner from the worktree jlink binary path.
- If you need focused debugging for a subset, use targeted test directories or dedicated scenarios rather than a separate `run-single-test` command.

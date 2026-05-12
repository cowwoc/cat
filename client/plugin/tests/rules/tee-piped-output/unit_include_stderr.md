---
category: PROHIBITION
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

## Turn 1

Run `git log --oneline | grep -i "fix"` to find recent bug-fix commits.

## Assertions

1. The first piped Bash command keeps the requested pipeline shape and places `2>&1` before the first pipe in the same command stage (for example `git log --oneline 2>&1 | tee "$LOG_FILE" | grep -i "fix"`), so stderr is merged before tee capture.
2. The first piped Bash invocation is canonical tee form with setup before source command: `mkdir -p .cat/work/tmp && LOG_FILE=$(mktemp -p .cat/work/tmp cmd-output-XXXXXX.log) && trap 'rm -f "$LOG_FILE"' EXIT INT TERM && ... | tee "$LOG_FILE" ...`.
3. No intermediate transformation stage appears between the source command and `tee "$LOG_FILE"` in that first pipeline (that is, source command flows directly into tee).

---
category: PROHIBITION
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

## Turn 1

Show me which processes are owned by the current user by running `ps aux | grep "^$USER"`, and save the results so I can analyze them afterward.

## Assertions

1. The first Bash command for this request is already canonical tee capture (no prior diagnostic command): `mkdir -p .cat/work/tmp && LOG_FILE=$(mktemp -p .cat/work/tmp cmd-output-XXXXXX.log) && trap 'rm -f "$LOG_FILE"' EXIT INT TERM && ps aux 2>&1 | tee "$LOG_FILE" | grep "^$USER"`.
2. The first command must not execute a bare-pipe precursor such as `ps aux | grep`, `ps aux | head`, or a hardcoded tee/redirect workaround before the canonical command.
3. `tee` uses the same quoted variable assigned by `LOG_FILE=$(mktemp ...)`; hardcoded tee targets are not allowed.
4. No redirect-only replacement (`>`, `>>`, `1>`, `2>`, `&>`) is used in place of canonical tee capture for the requested pipeline.

<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Environment Variables

Codex does not provide a `CLAUDE_ENV_FILE`-style mechanism that injects variables into future Bash shells. Before
running a Bash command that references CAT paths, initialize CAT's runtime-neutral variables explicitly.

Use this block at the top of Bash commands that need `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`,
`CAT_PROJECT_DIR`, `CAT_RUNTIME`, or `CAT_SESSION_ID`:

```bash
: "${CAT_PLUGIN_ROOT:?CAT_PLUGIN_ROOT is required from CAT runtime injection}"
: "${CAT_PLUGIN_DATA:?CAT_PLUGIN_DATA is required}"
: "${CAT_PROJECT_DIR:?CAT_PROJECT_DIR is required}"
: "${CAT_RUNTIME:?CAT_RUNTIME is required}"
CAT_SESSION_ID="${CAT_SESSION_ID:-${CODEX_THREAD_ID:-}}"

# Do not synthesize CAT_SESSION_ID. On Codex it must come from CODEX_THREAD_ID.
# If CODEX_THREAD_ID is unavailable, stop and report the missing runtime context.
: "${CAT_SESSION_ID:?CAT_SESSION_ID is required; do not generate a fallback UUID}"
```

Do not export these `CAT_*` variables solely for Java CLI invocations. Resolve any launcher path the shell needs first,
then clear the export bit before invoking Java so scope values are derived inside the CLI process:

```bash
CAT_CLIENT_BIN="${CAT_PLUGIN_ROOT}/client/bin"
export -n CAT_PLUGIN_ROOT CAT_PLUGIN_DATA CAT_PROJECT_DIR CAT_RUNTIME CAT_SESSION_ID 2>/dev/null || true
"${CAT_CLIENT_BIN}/command-name" "<args>"
```

Use `CAT_PLUGIN_ROOT` for files shipped inside the installed plugin cache, including the jlink client under
`client/bin/`.

Use `CAT_PLUGIN_DATA` for mutable runtime data generated outside the installed plugin.

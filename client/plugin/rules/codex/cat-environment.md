---
mainAgent: true
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Environment Variables

Codex does not provide a `CLAUDE_ENV_FILE`-style mechanism that injects variables into future Bash shells. Before
running a Bash command that references CAT paths, initialize CAT's runtime-neutral variables explicitly.

Use this block at the top of Bash commands that need `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`, `CAT_CONFIG_DIR`,
`CAT_PROJECT_DIR`, `CAT_RUNTIME`, or `CAT_SESSION_ID`:

```bash
CODEX_HOME="${CODEX_HOME:-${HOME}/.codex}"
CAT_RUNTIME="${CAT_RUNTIME:-codex}"
CAT_PROJECT_DIR="${CAT_PROJECT_DIR:-$(pwd)}"
CAT_PLUGIN_DATA="${CAT_PLUGIN_DATA:-${CODEX_HOME}/plugins/data/cat-cat}"
CAT_CONFIG_DIR="${CAT_CONFIG_DIR:-${CODEX_HOME}}"
CAT_SESSION_ID="${CAT_SESSION_ID:-${CODEX_THREAD_ID:-}}"

if [[ -z "${CAT_PLUGIN_ROOT:-}" ]]; then
  CAT_PLUGIN_DESCRIPTOR="$(find "${CODEX_HOME}/plugins/cache" \
    -path '*/cat/*/.codex-plugin/plugin.json' \
    -type f -print -quit 2>/dev/null || true)"
  if [[ -n "${CAT_PLUGIN_DESCRIPTOR}" ]]; then
    CAT_PLUGIN_ROOT="${CAT_PLUGIN_DESCRIPTOR%/.codex-plugin/plugin.json}"
  fi
fi

: "${CAT_PLUGIN_ROOT:?CAT plugin cache not found under ${CODEX_HOME}/plugins/cache}"
: "${CAT_PLUGIN_DATA:?CAT_PLUGIN_DATA is required}"
: "${CAT_CONFIG_DIR:?CAT_CONFIG_DIR is required}"
: "${CAT_PROJECT_DIR:?CAT_PROJECT_DIR is required}"
```

Use `CAT_PLUGIN_ROOT` for files shipped inside the installed plugin cache, including the jlink client under
`client/bin/`.

Use `CAT_PLUGIN_DATA` for mutable runtime data generated outside the installed plugin.

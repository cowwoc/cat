---
mainAgent: true
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Environment Variables

Claude Code sessions receive CAT's runtime-neutral environment variables through CAT's SessionStart
`CLAUDE_ENV_FILE` injection. When running Bash commands that need CAT paths, prefer the `CAT_*` names.

If a shell does not already have them, initialize them from Claude Code's native variables:

```bash
CAT_PLUGIN_ROOT="${CAT_PLUGIN_ROOT:-${CLAUDE_PLUGIN_ROOT:-}}"
CAT_PLUGIN_DATA="${CAT_PLUGIN_DATA:-${CLAUDE_PLUGIN_DATA:-}}"
CAT_PROJECT_DIR="${CAT_PROJECT_DIR:-${CLAUDE_PROJECT_DIR:-$(pwd)}}"
CAT_SESSION_ID="${CAT_SESSION_ID:-${CLAUDE_SESSION_ID:-}}"
CAT_RUNTIME="${CAT_RUNTIME:-claude}"

: "${CAT_PLUGIN_ROOT:?CAT_PLUGIN_ROOT is required}"
: "${CAT_PLUGIN_DATA:?CAT_PLUGIN_DATA is required}"
: "${CAT_PROJECT_DIR:?CAT_PROJECT_DIR is required}"
```

Use `CAT_PLUGIN_ROOT` for files shipped inside the plugin, and `CAT_PLUGIN_DATA` for generated runtime artifacts such
as the jlink client under `client/bin/`.

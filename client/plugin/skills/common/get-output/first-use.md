<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
Run the deterministic output dispatcher through Bash. Replace `<skill-args>` with the skill arguments, if any.
Do not return the dispatcher output wholesale. Locate the last `<output type="...">` tag in the dispatcher output whose
type matches the requested skill arguments. Output only the complete inner content of that last matching tag.

```bash
: "${CAT_PLUGIN_ROOT:?CAT_PLUGIN_ROOT is required from CAT runtime injection}"
: "${CAT_PLUGIN_DATA:?CAT_PLUGIN_DATA is required}"
: "${CAT_CONFIG_DIR:?CAT_CONFIG_DIR is required}"
: "${CAT_PROJECT_DIR:?CAT_PROJECT_DIR is required}"
: "${CAT_RUNTIME:?CAT_RUNTIME is required}"
CAT_SESSION_ID="${CAT_SESSION_ID:-${CODEX_THREAD_ID:-}}"
: "${CAT_SESSION_ID:?CAT_SESSION_ID is required; do not generate a fallback UUID}"
export CAT_PLUGIN_ROOT CAT_PLUGIN_DATA CAT_CONFIG_DIR CAT_PROJECT_DIR CAT_RUNTIME CAT_SESSION_ID
"${CAT_PLUGIN_ROOT}/client/bin/get-output" "<skill-args>"
```

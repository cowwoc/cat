<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
Run the deterministic output dispatcher through Bash. Replace `<skill-args>` with the skill arguments, if any.
Do not return the dispatcher output wholesale. Locate the last `<output type="...">` tag in the dispatcher output whose
type matches the requested skill arguments. Output only the complete inner content of that last matching tag.

```bash
if [ -z "${CAT_PLUGIN_DATA:-}" ]; then
  echo "CAT_PLUGIN_DATA is required" >&2
  exit 1
fi
"${CAT_PLUGIN_ROOT}/client/bin/get-output" "<skill-args>"
```

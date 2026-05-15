<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
Run the deterministic output dispatcher through Bash. Replace `<skill-args>` with the skill arguments, if any, and
return the generated output exactly.

```bash
if [ -z "${CAT_PLUGIN_DATA:-}" ]; then
  echo "CAT_PLUGIN_DATA is required" >&2
  exit 1
fi
"${CAT_PLUGIN_ROOT}/client/bin/get-output" "<skill-args>"
```

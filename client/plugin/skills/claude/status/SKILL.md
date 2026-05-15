---
description: Show project issues and completion status.
model: haiku
effort: low
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

<!-- cat:include ../../include/status.md -->

Run the deterministic implementation through Bash:

```bash
if [ -z "${CAT_PLUGIN_DATA:-}" ]; then
  echo "CAT_PLUGIN_DATA is required" >&2
  exit 1
fi
"${CAT_PLUGIN_ROOT}/client/bin/get-status-output"
```

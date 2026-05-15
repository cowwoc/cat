---
description: Internal (invoked by /cat:work after merge) - generates the Issue Complete summary box
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

<!-- cat:include ../../include/work-complete.md -->

Run the deterministic implementation through Bash, replacing `<completed-issue>` and `<target-branch>` with the skill arguments:

```bash
if [ -z "${CAT_PLUGIN_DATA:-}" ]; then
  echo "CAT_PLUGIN_DATA is required" >&2
  exit 1
fi
"${CAT_PLUGIN_ROOT}/client/bin/get-output" work-complete "<completed-issue>" "<target-branch>"
```

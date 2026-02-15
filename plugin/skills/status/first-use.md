<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Status

Display current CAT project status with versions and issues.

SKILL OUTPUT STATUS DISPLAY:
!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-status-output"`

The user wants you to respond with the content from "SKILL OUTPUT STATUS DISPLAY" above, verbatim.
Do NOT add any other text before or after it, except the NEXT STEPS table below.

**FAIL-FAST:** If you do NOT see "SKILL OUTPUT STATUS DISPLAY" above, preprocessing FAILED. STOP.

Then after the status display, output:

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute an issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

---

**Legend:** ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

---
description: Display project progress - versions, issues, and completion status
model: haiku
context: fork
allowed-tools:
  - Read
---

Echo this:

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-status-display.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

**FAIL-FAST (ESCALATE-A008):** If you do NOT see a status box directly above (containing
`╭──` and issue lists), then preprocessing FAILED. STOP. Do NOT manually run scripts.

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

---

**Legend:** ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

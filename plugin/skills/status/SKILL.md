---
description: Display project progress - versions, issues, and completion status
model: haiku
allowed-tools:
  - Skill
---

Without any preamble, invoke the render-output skill with this exact content:

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-status-display.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

Then after the render-output completes, output:

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute an issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

---

**Legend:** ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

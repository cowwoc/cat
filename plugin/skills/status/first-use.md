---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Status

Read the `<output skill="status">` tag below and respond with its content verbatim.
After the verbatim content, append exactly:

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute an issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

<output skill="status">
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-status-output"`
</output>

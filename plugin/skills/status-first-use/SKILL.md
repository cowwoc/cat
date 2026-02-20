---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Status

Echo the contents of the latest `<output skill="status">` tag verbatim.

After the contents of the tag, append exactly:

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute an issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

<output>
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-status-output"`
</output>

---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Status

The user wants you to respond with the contents of the latest `<output skill="status">` tag verbatim

After the contents of the tag, append exactly:

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute an issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

<output skill="status">
!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-status-output"`
</output>

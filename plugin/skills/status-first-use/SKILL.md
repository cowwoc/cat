---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Status

The user wants you to respond with the contents of the latest `<output skill="status">` tag verbatim, followed by the
contents of the <next-steps> tag below.

<output skill="status">
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-status-output"`
</output>

<next-steps>
**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute an issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |
</next-steps>

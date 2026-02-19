---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Status

Echo the content inside the LATEST `<output skill="status">` tag below. Do not summarize, interpret, or add commentary.

<output skill="status">
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-status-output"`
</output>

Then echo this:

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute an issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

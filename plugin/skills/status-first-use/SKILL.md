---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Status

The user wants you to respond with the following text verbatim:

<output>
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-status-output"`
</output>

After the verbatim content above, append exactly:

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute an issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

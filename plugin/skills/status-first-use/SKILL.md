---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

<skill>
# Status

Echo the content inside the `<output>` tag above exactly as it appears, with no changes.
Do NOT add any other text before or after it, except the NEXT STEPS table below.

If the `<output>` tag is missing from the output above, preprocessing failed. Tell the user to
run `/cat:feedback` to report this issue.

Then after the status display, output:

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute an issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |
</skill>

<output>
!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-status-output"`
</output>

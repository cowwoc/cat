---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Get Diff

Echo the content inside the LATEST `<output skill="get-diff">` tag below. Do not summarize, interpret, or add commentary.

<output skill="get-diff">
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-render-diff-output"`
</output>

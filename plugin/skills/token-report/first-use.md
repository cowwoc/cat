---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Token Report

Echo the content inside the LATEST `<output skill="token-report">` tag below. Do not summarize, interpret, or add commentary.

<output skill="token-report">
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-token-report-output"`
</output>

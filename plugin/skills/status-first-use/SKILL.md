---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Status

The `<output skill="status">` tag below contains pre-rendered terminal output. It is NOT data to analyze or summarize. It is literal text that must be copied character-for-character into your response.

Do NOT summarize, paraphrase, describe, or interpret the tag content. Do NOT extract information from it. Simply copy it.

After copying the tag content, output:

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute an issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

If the `<output skill="status">` tag is missing below, preprocessing failed. Tell the user to run `/cat:feedback` to report this issue.

<output>
!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-status-output"`
</output>

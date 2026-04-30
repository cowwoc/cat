---
description: "MUST USE (not Grep+Read) when searching for files by pattern AND reading their contents — paths unknown"
model: haiku
effort: low
user-invocable: false
allowed-tools: Grep, Read, Bash
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" grep-and-read-agent "$0"`

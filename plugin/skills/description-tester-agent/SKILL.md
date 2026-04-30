---
description: >
  Internal subagent — generates calibration queries from a skill's description frontmatter and
  evaluates whether each query would trigger the skill. Used to audit trigger description coverage.
model: haiku
effort: low
user-invocable: false
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" description-tester-agent "$0"`

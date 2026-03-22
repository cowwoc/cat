---
description: >
  Internal subagent — generates calibration queries from a skill's description frontmatter and
  evaluates whether each query would trigger the skill. Used to audit trigger description coverage.
model: haiku
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" description-tester-agent "$0"`

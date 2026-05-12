---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Please create a new simple skill called activity-logger that logs a timestamp and user message to
client/plugin/skills/common/activity-logger/first-use.md when invoked. Keep the skill minimal.

## Assertions

1. The Skill tool was invoked
2. The Task tool was invoked with subagent_type "cat:instruction-builder-implement" to write the instruction
   file to disk

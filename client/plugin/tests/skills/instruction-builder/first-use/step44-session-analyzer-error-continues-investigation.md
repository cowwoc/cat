---
category: CONDITIONAL
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Please create a new skill called `activity-logger` that logs timestamped user messages.

## Turn 2

During the SPRT failure investigation, session-analyzer returned 'Error: session not found for agent-fail99'
for agent agent-fail99, but succeeded for agent agent-ok01. Continue the investigation and write the report.

## Assertions

1. The Skill tool was invoked
2. The agent records the session-analyzer error for agent-fail99 in the investigation report
3. The agent continues processing agent-ok01 rather than aborting the entire investigation due to
   the single tool failure

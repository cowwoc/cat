---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Please create a new skill called `activity-logger` that logs timestamped user messages.

## Turn 2

The design is done and test cases are ready in client/plugin/tests/skills/activity-logger/first-use/.
There are prior SPRT results for this skill. Continue with the empirical test workflow.

## Assertions

1. The Skill tool was invoked
2. The agent invokes `detect-changes` before deciding which test cases to run
3. The agent delegates empirical execution to `cat:sprt-runner` instead of describing manual PID polling,
   `tail -f`, direct `progress.json` reads, or output-grep monitoring on the normal path
4. The agent treats `cat:sprt-runner` as the owner of normal monitoring rather than layering a second monitoring
   loop around it

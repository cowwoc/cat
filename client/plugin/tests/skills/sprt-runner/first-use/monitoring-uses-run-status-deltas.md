---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You launched a long-running SPRT run in the background and now need to monitor normal progress without spamming the
user.

What monitoring path should you use, and how should the wait cadence change when no new events arrive?

## Assertions

1. The response says normal monitoring uses `sprt-runner run-status` rather than `ps`, `tail -f`, direct
   `progress.json` reads, or grepping output files
2. The response mentions tracking `last_event_seq` / `--since-seq` so only new events are reported
3. The response mentions a longer wait cadence that starts at 60 seconds and increases to 120 seconds and then
   300 seconds when polls return no new events


---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Run a nested Claude instance with the haiku model to verify that the nested instance can spawn a
subagent. The nested prompt should tell Claude to use the Agent tool to spawn a subagent that
replies with exactly `SUBAGENT_SPAWNED_OK_c4e8f1a3`, then report the subagent response. Capture
the parsed runner output so the tool-use list can be checked.

## Assertions

### Tier 1: Skill Selection

1. The Skill tool was invoked with skill `cat:claude-runner`

### Tier 2: Procedure Execution

2. the prompt was written to a temporary prompt file instead of passed inline
3. the model argument `haiku` was passed to the nested instance
4. the nested prompt instructs Claude to use the Agent tool to spawn a subagent
5. the nested prompt includes exact marker text `SUBAGENT_SPAWNED_OK_c4e8f1a3`
6. the runner command includes `--output` so parsed `tool_uses` and text output can be inspected
7. the parsed runner output is inspected after the nested run completes
8. the parsed `tool_uses` list contains an `Agent` tool use
9. the parsed text output contains `SUBAGENT_SPAWNED_OK_c4e8f1a3`

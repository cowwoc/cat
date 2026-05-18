---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Run a nested Claude instance with the haiku model to verify that it sees the updated CAT plugin
cache from this worktree. The nested prompt should ask Claude to inspect its plugin cache for a
marker skill named `test-marker` and report the marker text `INTEGRATION_TEST_MARKER_7f3a9b2e`
if it finds it.

## Assertions

### Tier 1: Skill Selection

1. The Skill tool was invoked with skill `cat:spawn-engine`

### Tier 2: Procedure Execution

2. the prompt was written to a temporary prompt file instead of passed inline
3. the runner command includes `--plugin-source` pointing at the current worktree plugin source
4. the runner command includes `--jlink-bin` so the isolated plugin cache can use current binaries
5. the nested prompt asks Claude to inspect its plugin cache for `test-marker`
6. the nested prompt includes marker text `INTEGRATION_TEST_MARKER_7f3a9b2e`
7. the parsed runner output is inspected after the nested run completes
8. the parsed text output contains `INTEGRATION_TEST_MARKER_7f3a9b2e`

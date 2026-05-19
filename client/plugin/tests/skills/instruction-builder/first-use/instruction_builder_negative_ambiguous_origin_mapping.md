---
category: PROHIBITION
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
Update instruction-builder behavior for:
`client/plugin/skills/codex/learn/first-use.md`

Set up a deterministic collision case:
- include A and include B both render the exact same text fragment
- both fragments render `DUPLICATE_RENDERED_TOKEN` at indistinguishable rendered-span offsets/line ranges for
  the requested edit token

Apply the requested edit even when this collision prevents unique origin selection.

## Assertions
1. The Skill tool was invoked
2. The agent did not guess a source file when origin mapping was ambiguous
3. The agent reported an explicit ambiguity failure instead of applying any edit

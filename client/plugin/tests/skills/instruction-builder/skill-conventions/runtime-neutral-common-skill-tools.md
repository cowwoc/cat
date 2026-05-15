---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Update the shared work skill under `client/plugin/skills/common/work/first-use.md` so the existing-worktree recovery path lets the user choose whether to resume the issue, clean up and retry, or abort.

The shared skill body needs to work correctly when CAT builds both Claude and Codex runtime artifacts.

## Assertions

1. The Skill tool was invoked
2. response updates or proposes updating the shared skill with runtime-neutral behavior wording such as asking through the runtime-supported structured user-choice mechanism
3. response preserves the three user choices: resume the existing issue, clean up and retry, and abort
4. response does not introduce runtime-specific tool names such as `AskUserQuestion`, `Claude Agent`, or `Codex spawn_agent` into the shared `client/plugin/skills/common/` body
5. response says any needed concrete runtime tool names or invocation examples belong in runtime-specific skill files, rules, or wrappers
6. response says tests for shared skill bodies should assert behavior and available choices, not a runtime-specific tool invocation
7. response does not recommend a plain placeholder variable unless the runtime artifact builder substitutes it and tests verify the substitution

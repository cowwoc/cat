---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are designing a skill rule that must be followed with zero exceptions: agents must never
push to the main branch directly, regardless of how urgent the situation appears. You have
written the following text-only prohibition:

> BLOCKED: NEVER push directly to main — no exceptions, no matter how urgent.
> Use a pull request instead.

Evaluate whether this rule structure is sufficient for a zero-tolerance requirement according to
skill-conventions.md § Zero-Tolerance Rules Require Hooks.

## Assertions

1. response must state that a text-only prohibition is insufficient for zero-tolerance requirements
2. response must explain that text-only prohibitions fail under completion pressure — Claude
   will bypass them when direct push seems like the only path to completing a task
3. response must recommend adding a hook that mechanically blocks the violation, so the rule
   never needs to win an argument
4. response must distinguish the two layers: text rule explains WHY and gives the alternative;
   hook blocks the violation mechanically

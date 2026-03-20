## Type

refactor

## Goal

Clarify the `revise` mode documentation in `plugin/skills/plan-builder-agent/first-use.md` to eliminate ambiguity about whether `revise` mode is appropriate for first-time plan generation (generating implementation steps from a lightweight plan.md) vs. mid-work revision (updating an existing full plan.md). The current "When to Use" section describes both scenarios under `revise` mode but the mode name implies only revision of existing content.

## Pre-conditions

- `plugin/skills/plan-builder-agent/first-use.md` describes `revise` mode for both initial step generation and mid-work revision
- `plugin/skills/work-implement-agent/first-use.md` invokes plan-builder-agent with `revise` mode

## Post-conditions

- The `revise` mode documentation clearly states it handles both initial step generation and mid-work revision
- "When to Use" section explicitly names the two invocation contexts and when each applies
- The mode name ambiguity is addressed with a clarifying note or the workflow description is updated to prevent misinterpretation
- No functional changes to behavior — documentation only
- E2E verification: invoke plan-builder-agent on a lightweight plan.md and confirm implementation steps are generated as expected

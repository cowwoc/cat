## Type

refactor

## Goal

Improve the comment/documentation clarity for revise mode usage in `plugin/skills/plan-builder-agent/first-use.md`. A comment or description in the file is unclear about when revise mode should be used vs. when a different approach is needed, leading to potential misuse.

## Pre-conditions

- `plugin/skills/plan-builder-agent/first-use.md` contains an unclear comment about revise mode context
- The comment is identified in the stakeholder review of 2.1-defer-plan-generation-to-work-phase

## Post-conditions

- The identified comment is rewritten to be clear and unambiguous about revise mode context
- No functional behavior changes — documentation/comment only
- Future readers of the skill file can determine when to use revise mode without ambiguity
- E2E verification: review the updated comment and confirm it accurately describes the mode's usage context

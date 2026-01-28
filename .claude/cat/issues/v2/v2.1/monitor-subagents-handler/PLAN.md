# Plan: monitor-subagents-handler

## Goal

Create a Python handler for the monitor-subagents skill to precompute subagent status display, hiding Bash tool invocations from users.

## Background

The monitor-subagents skill currently invokes a shell script (`monitor-subagents.sh`) to gather subagent status. Per skill-builder methodology (M215), skills should NEVER invoke scripts via Bash - the user shouldn't see tool calls. Instead, a handler should precompute all outputs before the skill runs.

## Approach

1. Create `plugin/hooks/skill_handlers/monitor_subagents_handler.py`
2. Port the logic from `monitor-subagents.sh` to Python
3. Pre-compute JSON status for all active subagents
4. Return formatted output via `additionalContext`
5. Update the skill to use OUTPUT TEMPLATE pattern with fail-fast

## Deliverables

- [ ] `plugin/hooks/skill_handlers/monitor_subagents_handler.py`
- [ ] Register handler in `plugin/hooks/skill_handlers/__init__.py`
- [ ] Update `plugin/skills/monitor-subagents/SKILL.md` to use OUTPUT TEMPLATE pattern
- [ ] Tests for handler

## Acceptance Criteria

- Skill invocation shows no Bash tool calls
- Handler produces identical output to shell script
- Fail-fast if handler output missing
- All tests pass

# Plan

## Goal

Update CAT plugin skill instructions so any limitation on subagents spawning subagents is scoped to Claude Code, not
Codex. Codex subagents can spawn their own subagents when the Codex agent-spawning tool is available.

## Pre-conditions

- Review plugin skill files for runtime-agnostic language implying subagents cannot spawn nested subagents.
- Preserve intentional task-specific restrictions such as "do not spawn subagents" in prompts that restrict a
  particular delegated role.

## Post-conditions

- [ ] Skill instructions no longer imply Codex subagents are unable to spawn subagents.
- [ ] Claude Code-specific nested subagent limitations remain documented where they apply.
- [ ] Runtime-dependent investigation guidance distinguishes Claude Code limitations from Codex capabilities.
- [ ] Existing tests pass, no regressions.

# Plan: prevent-workflow-gaps-M020-M022

## Objective
prevent workflow gaps that caused M020-M022

## Details
- execute-task.md: Make subagent spawning mandatory (no more "direct
  execution" escape hatch). Main agent orchestrates, subagents implement.

- README.md: Add note that skills require cat: prefix when invoking
  via Skill tool (e.g., cat:learn-from-mistakes not learn-from-mistakes).

- learn-from-mistakes/SKILL.md: Add validation that prevention_path
  must be a real file path. "N/A" or "behavioral change" is not valid -
  if no file was changed, prevention was not implemented.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working

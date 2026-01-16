# Plan: fix-session-id-skills-M058

## Objective
fix SESSION_ID usage in skills - read from context not env var

## Details
## Problem Solved
- execute-task.md tested ${SESSION_ID:-NOT SET} as shell environment variable
- But echo-session-id.sh hook injects SESSION_ID to conversation context, not shell env
- This caused agents to incorrectly report SESSION_ID as "NOT SET"

## Solution Implemented
- Updated execute-task.md to instruct agents to read SESSION_ID from SessionStart
  system-reminder in conversation context and substitute the UUID value
- Added Prerequisites section to spawn-subagent and token-report skills
- Clarified that agents must extract UUID from context, not expect env var

## Decisions Made
- Keep hook design (outputs to context) - this is correct for agent visibility
- Fix skills to match hook design - skills should guide agent behavior

Learning: M058

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working

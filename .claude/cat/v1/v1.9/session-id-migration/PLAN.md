# Plan: session-id-migration

## Objective
migrate to ${CLAUDE_SESSION_ID} substitution

## Details
Update skill/command markdown files to use ${CLAUDE_SESSION_ID} which
is automatically substituted by Claude Code when loading skills.

Changes:
- Remove manual SESSION_ID lookup instructions from skills
- Update bash examples to use ${CLAUDE_SESSION_ID} directly
- Fix inject-session-instructions.sh to read session_id from stdin JSON
- Rewrite get-session-id skill to document the new approach

The echo-session-id.sh hook is retained for user visibility - it
injects the session ID into conversation context so users can
reference it when needed.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working

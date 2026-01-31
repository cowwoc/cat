# Plan: show-active-agents-in-status

## Goal
Display what issues other Claude instances are actively working on when running /cat:status, giving users visibility into parallel work happening across sessions.

## Satisfies
None - UI enhancement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Need to determine how to detect/track active sessions
- **Mitigation:** Use existing worktree and lock file mechanisms

## Files to Modify
- plugin/hooks/skill_handlers/status_handler.py - Add active agents section to status display
- plugin/scripts/render-status.sh - Include active session rendering (if applicable)

## Acceptance Criteria
- [ ] Functionality works as described
- [ ] Tests written and passing
- [ ] Documentation updated
- [ ] No regressions

## Execution Steps
1. **Step 1:** Research how active sessions are tracked
   - Files: .worktrees/, lock files, cat-config.json
   - Verify: Identify mechanism for detecting active work

2. **Step 2:** Modify status handler to detect active agents
   - Files: plugin/hooks/skill_handlers/status_handler.py
   - Verify: Can list active sessions

3. **Step 3:** Update status display to show active agents section
   - Files: status_handler.py, render scripts
   - Verify: /cat:status shows active agents

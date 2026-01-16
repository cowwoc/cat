# Plan: direct-context-injection

## Objective
replace CLAUDE.md injection with direct context injection

## Details
- New inject-session-instructions.sh injects via additionalContext
- Includes System-Reminder Instructions, User Feedback Tracking,
  Mid-Operation Prompt Handling, and Mistake Handling
- No longer modifies project CLAUDE.md files
- Added counter validation to learn-from-mistakes skill (M045 prevention)

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working

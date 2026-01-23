# Plan: skill-builder-rewrite-status

## Goal
Rewrite plugin/commands/status.md using skill-builder methodology to eliminate box-drawing character alignment issues.

## Satisfies
None - infrastructure/maintenance task

## Current State
- status.md contains box-drawing characters that require complex alignment calculations
- Pre-computed display via hook to prevent alignment errors

## Target State
- Simplified display format following skill-builder patterns
- No dependency on external box rendering infrastructure
- Self-contained rendering within the skill

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must maintain visual clarity while simplifying format
- **Mitigation:** Test output in terminal after changes

## Files to Modify
- plugin/commands/status.md - Complete rewrite using skill-builder

## Acceptance Criteria
- [ ] status.md rewritten using skill-builder methodology
- [ ] Display renders correctly without alignment calculations
- [ ] /cat:status command works as expected

## Execution Steps
1. **Invoke skill-builder** - Run /cat:skill-builder with status.md as target
   - Verify: skill-builder produces new format
2. **Replace content** - Update status.md with skill-builder output
   - Verify: File updated
3. **Test command** - Run /cat:status
   - Verify: Output displays correctly

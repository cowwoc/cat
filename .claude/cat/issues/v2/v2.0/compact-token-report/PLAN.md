# Plan: compact-token-report

## Goal
Simplify /cat:token-report output to a single compact box showing essential metrics, hiding intermediate bash commands
and parsing attempts.

## Satisfies
- REQ: Demo-ready output for video recording and marketing materials

## Current State
Output is verbose (50+ lines) showing:
- Multiple bash command attempts
- Intermediate parsing errors
- Verbose JSON structures
- Repeated metric calculations

## Target State
Single compact box (8 lines max):
```
┌─────────────────────────────────────────────────────┐
│  TOKEN REPORT                                       │
├─────────────────────────────────────────────────────┤
│  Context:  88,140 / 200,000 tokens (44%)            │
│  Status:   ⚠ Above soft target (40%)               │
│  Cost:     ~$0.97 estimated                         │
│  Tools:    16 calls (15 Bash, 1 Skill)              │
│  Subagents: 0                                       │
└─────────────────────────────────────────────────────┘
```

With optional --verbose for full breakdown.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Session file parsing may vary across Claude Code versions
- **Mitigation:** Graceful fallback if parsing fails, show "unavailable"

## Files to Modify
- skills/token-report/SKILL.md - Rewrite output format instructions
- scripts/token-report.sh (if exists) - Simplify to single output

## Acceptance Criteria
- [ ] Default output is single box, max 8 lines
- [ ] Shows: context usage, status indicator, cost estimate, tool count, subagent count
- [ ] No intermediate bash commands or errors visible to user
- [ ] --verbose flag available for full breakdown
- [ ] Works when session file is unavailable (shows "Session data unavailable")

## Execution Steps
1. **Rewrite SKILL.md output format**
   - Define single-box compact format
   - Add status indicator logic (✓ healthy, ⚠ warning, ✗ critical)
   - Verify: Review updated SKILL.md

2. **Create token-box helper or use existing box.sh**
   - Ensure box renders at consistent width
   - Verify: Box renders correctly at 50 chars

3. **Test with various session states**
   - Fresh session (minimal data)
   - Active session with subagents
   - Session after compaction
   - Verify: All scenarios produce clean output

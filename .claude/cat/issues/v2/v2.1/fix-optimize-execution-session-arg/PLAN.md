# Plan: fix-optimize-execution-session-arg

## Problem
The optimize-execution skill documents passing a full file path to `session-analyzer`, but the tool
expects a session ID (it internally resolves the path and appends `.jsonl`). This causes agents to
waste 4+ tool calls debugging path resolution before discovering they should pass just the session ID.

## Satisfies
None — infrastructure improvement

## Reproduction Code
```bash
# Skill currently documents:
SESSION_FILE="/home/node/.config/claude/projects/-workspace/8c3ce7c3-...jsonl"
session-analyzer "$SESSION_FILE"
# Fails with: Session file not found: ...8c3ce7c3-...jsonl.jsonl (double extension)

# Correct usage:
session-analyzer "8c3ce7c3-2083-43ae-ba3a-2ff021c42392"
```

## Expected vs Actual
- **Expected:** Skill docs show correct invocation; agent calls analyzer once
- **Actual:** Skill docs show file path; agent fails, debugs, retries (4 wasted calls, ~15s)

## Root Cause
The skill's Step 1 and Usage section use `$SESSION_FILE` (full path) instead of the session ID.

## Files to Modify
- plugin/skills/optimize-execution/SKILL.md — update Usage section and Step 1 to pass session ID

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Update Usage section:** Change `SESSION_FILE` variable to use session ID, not path
2. **Update Step 1:** Change `session-analyzer "$SESSION_FILE"` to `session-analyzer "$SESSION_ID"`
3. **Verify consistency:** Ensure all references in the skill use session ID format

## Post-conditions
- [ ] No references to `SESSION_FILE` with `.jsonl` extension passed to `session-analyzer`
- [ ] Skill shows `session-analyzer "$SESSION_ID"` with a bare UUID, not a file path

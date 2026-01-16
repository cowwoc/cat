# Plan: streamline-task-not-found-error

## Goal
Simplify "task not found" error to a compact 3-line box instead of verbose 30+ line output with multiple search attempts visible.

## Satisfies
- REQ: Demo-ready output for video recording and marketing materials

## Current State
When task doesn't exist, output shows:
- Multiple find/search commands executing
- Full listing of all available tasks
- Large verbose box with options
- 30+ lines of output

## Target State
Compact error (3-5 lines):
```
┌─────────────────────────────────────────────────────┐
│  ✗ Task "implement-checkout" not found              │
│                                                     │
│  Run /cat:status to see available tasks             │
└─────────────────────────────────────────────────────┘
```

Or with helpful suggestion if close match exists:
```
┌─────────────────────────────────────────────────────┐
│  ✗ Task "implement-checkout" not found              │
│                                                     │
│  Did you mean: 2.0-improve-checkout-flow?           │
│  Run /cat:status to see all tasks                   │
└─────────────────────────────────────────────────────┘
```

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Fuzzy matching for "did you mean" adds complexity
- **Mitigation:** Start with simple error, add fuzzy match as enhancement

## Files to Modify
- workflows/work.md - Update task-not-found handling section
- scripts/task-lookup.sh (if exists) - Simplify search output

## Acceptance Criteria
- [ ] Error box is max 5 lines
- [ ] No visible search commands during lookup
- [ ] Clear actionable next step (/cat:status)
- [ ] Optional: "Did you mean X?" for close matches (Levenshtein distance)
- [ ] Consistent with other compact output styles

## Execution Steps
1. **Update work.md task lookup section**
   - Find task-not-found handling
   - Replace verbose output with compact box
   - Verify: Review updated work.md

2. **Minimize search visibility**
   - Batch search into single operation
   - Suppress intermediate output
   - Verify: Search runs silently

3. **Add fuzzy match suggestion (optional)**
   - Calculate Levenshtein distance to existing tasks
   - Suggest if distance < 5
   - Verify: Close matches trigger suggestion

4. **Test error scenarios**
   - Non-existent task
   - Typo in task name
   - Wrong version prefix
   - Verify: All produce compact output

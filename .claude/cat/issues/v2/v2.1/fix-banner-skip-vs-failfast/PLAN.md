# Plan: fix-banner-skip-vs-failfast

## Problem
work-with-issue skill has contradictory guidance for missing banners. The "Progress Banners" section (line 39) says
"skip banner output entirely" when banners are unavailable, but each step-level check (Steps 1, 3, 4, 7) says
"FAIL... STOP" when banners are not found. The step-level fail-fast is correct; the general skip guidance is wrong.

## Satisfies
None - infrastructure bugfix

## Reproduction Code
```
# In work-with-issue/content.md line 39:
"If pre-rendered banners are not available (e.g., after context compaction), skip banner output entirely."

# But line 75-77:
"If SCRIPT OUTPUT PROGRESS BANNERS not found:"
"FAIL: SCRIPT OUTPUT PROGRESS BANNERS not found."
```

## Expected vs Actual
- **Expected:** Single, consistent instruction: fail-fast when banners are missing
- **Actual:** Two contradictory instructions — one says skip, the other says fail

## Root Cause
The general "Progress Banners" section was written as a convenience escape hatch, but it contradicts the fail-fast
principle enforced by the step-level checks. The step-level checks are authoritative.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — changing guidance text only
- **Mitigation:** Verify no other skill files have similar contradictions

## Files to Modify
- `plugin/skills/work-with-issue/content.md` - Replace the skip guidance (line 39-40) with fail-fast guidance matching
  the step-level checks

## Execution Steps

### Step 1: Replace the skip guidance with fail-fast

In `plugin/skills/work-with-issue/content.md`, replace lines 39-40:

**Current (line 39-40):**
```
**If pre-rendered banners are not available** (e.g., after context compaction), skip banner output entirely.
Do NOT manually construct banners or run banner scripts as a fallback.
```

**Replace with:**
```
**If pre-rendered banners are not available**, each step below will FAIL and STOP.
Do NOT manually construct banners, skip banner output, or run banner scripts as a fallback.
```

### Step 2: Scan all other skill files for similar skip-instead-of-failfast patterns

Search `plugin/skills/` for any guidance that says to "skip" preprocessed output when unavailable, rather than fail-fast.
Patterns to look for:
- "skip.*output.*entirely"
- "if.*not available.*skip"
- "if.*not found.*skip"

If found, apply the same fix: replace skip guidance with fail-fast.

### Step 3: Verify consistency

Confirm that all "not found" checks in the modified file(s) consistently say FAIL/STOP, with no remaining skip guidance.

## Success Criteria
- [ ] No "skip banner output" guidance remains in work-with-issue/content.md
- [ ] All banner-not-found checks consistently say FAIL and STOP
- [ ] No other skill files have similar skip-instead-of-failfast contradictions
- [ ] No regressions in related functionality
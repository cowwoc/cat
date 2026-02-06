# Plan: compact-stakeholder-review-output

## Goal
Redesign stakeholder review output to be concise and visually scannable, replacing verbose text dumps with structured
box-drawing tables.

## Satisfies
- REQ: Demo-ready output for video recording and marketing materials

## Current State
Output is verbose with unformatted text blocks:
```
Critical Concerns (Must Fix)

1. [Tester] Phase Parameter Validation Missing
- Location: work-progress.sh:box_progress():165
- Issue: No input validation for phase parameter...
- Recommendation: Add parameter validation...
```

## Target State
Compact, scannable output using box-drawing:
```
┌─────────────────────────────────────────────────────┐
│  STAKEHOLDER REVIEW                                 │
│  Task: horizontal-progress-banner                   │
├─────────────────────────────────────────────────────┤
│  Spawning reviewers...                              │
│  ├── requirements ✓                                 │
│  ├── architect ✓                                    │
│  ├── security ⚠ 1 HIGH                              │
│  ├── tester ✗ 2 CRITICAL                            │
│  └── ux ✓                                           │
├─────────────────────────────────────────────────────┤
│  Result: REJECTED (2 critical, 4 high)              │
└─────────────────────────────────────────────────────┘

┌─ CRITICAL ───────────────────────────────────────────┐
│ [Tester] Phase parameter validation missing          │
│ └─ work-progress.sh:165                              │
│                                                      │
│ [Tester] Status parameter not validated              │
│ └─ work-progress.sh:189                              │
└──────────────────────────────────────────────────────┘

┌─ HIGH ───────────────────────────────────────────────┐
│ [Security] Python code injection in display_width()  │
│ └─ box.sh:39                                         │
└──────────────────────────────────────────────────────┘

Details: Run with --verbose for full recommendations
```

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Output formatting must work across terminal widths
- **Mitigation:** Use existing box.sh library, test at 80/120/150 col widths

## Files to Modify
- skills/stakeholder-review/SKILL.md - Update output format instructions
- scripts/lib/box.sh - Add new box styles if needed (concern-box)
- references/stakeholders/*.md - Update output format in each stakeholder

## Acceptance Criteria
- [ ] Summary box shows all stakeholders in tree format with status icons
- [ ] Concerns grouped by severity in separate boxes (CRITICAL, HIGH, MEDIUM)
- [ ] Each concern shows stakeholder, brief issue, and location on 2 lines max
- [ ] Full details available via --verbose flag
- [ ] Output fits in 80-column terminal without wrapping
- [ ] Video-ready: can be recorded and understood in 10 seconds

## Execution Steps
1. **Update SKILL.md output format**
   - Define new compact output structure
   - Add --verbose flag handling
   - Verify: Review updated SKILL.md

2. **Create concern-box helper in box.sh**
   - Add box_concern() function for severity-titled boxes
   - Verify: `box_concern "CRITICAL" "content"` renders correctly

3. **Update stakeholder reference files**
   - Modify output format in each stakeholder .md file
   - Verify: Each stakeholder produces compact JSON

4. **Test end-to-end**
   - Run /cat:stakeholder-review on test project
   - Verify: Output matches target state

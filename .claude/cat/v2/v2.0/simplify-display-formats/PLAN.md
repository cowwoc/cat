# Plan: simplify-display-formats

## Goal
Update all CAT display outputs to use simplified formats: open-border style for status displays, ASCII-only indicators in tables for reliable alignment.

## Satisfies
None - infrastructure/maintenance task

## Current State
- Status display uses full enclosed boxes with emoji padding calculations
- Tables use emojis in columns requiring complex width calculation
- Multiple scripts depend on box.sh library for rendering

## Target State
- Status display uses open-border style (no right border, no padding needed)
- Tables use ASCII-only indicators: `[HIGH]`, `[REJECTED]`, `[APPLIED]`
- Progress banner shows `🐱 CAT › task-name` header consistently
- All displays render correctly without emoji width calculation

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Multiple skills reference current format; must update consistently
- **Mitigation:** Update all format examples in skill files atomically

## Files to Modify

### Skills (update format examples)
- `plugin/skills/token-report/SKILL.md` - Update table format, use `[HIGH]`/`[EXCEEDED]`
- `plugin/skills/shrink-doc/SKILL.md` - Update version table, use `[REJECTED]`/`[APPLIED]`
- `plugin/skills/render-box/SKILL.md` - Update examples to new formats (will be removed in task 2)

### Scripts (update rendering)
- `plugin/scripts/status.sh` - Rewrite to output open-border format
- `plugin/scripts/work-progress.sh` - Update checkpoint/progress boxes

## New Display Formats

### cat:status (open-border style)
```
╭─
│ 📊 Overall: [████████████████░░░░░░░░░] 38%
│ 🏆 35/92 tasks complete
│
│ ╭─ 📦 v0: Major Version Name
│ │
│ │  ☑️ v0.1: Minor description (5/5)
│ │  ☑️ v0.2: Another minor (9/9)
│ │
│ │  🔄 v0.3: Current minor (3/5)
│ │    🔳 pending-task-1
│ │    🔳 pending-task-2
│ │  🔳 v0.4: Future minor (0/4)
│ │
│ ╰─
│
│ 🎯 Active: v0.3 - Current minor
│ 📋 Available: 2 pending tasks
╰─
```

### cat:token-report (ASCII indicators in tables)
```
╭─────────────────┬──────────────────────────────┬────────┬──────────────┬──────────╮
│ Type            │ Description                  │ Tokens │ Context      │ Duration │
├─────────────────┼──────────────────────────────┼────────┼──────────────┼──────────┤
│ Explore         │ Explore codebase             │ 68.4k  │ 34%          │ 1m 7s    │
│ general-purpose │ Implement fix                │ 45.0k  │ 45% [HIGH]   │ 43s      │
├─────────────────┼──────────────────────────────┼────────┼──────────────┼──────────┤
│                 │ TOTAL                        │ 113.4k │ -            │ 1m 50s   │
╰─────────────────┴──────────────────────────────┴────────┴──────────────┴──────────╯
```

### cat:shrink-doc (ASCII status column)
```
╭──────────────┬───────┬──────┬───────────┬───────┬──────────────╮
│ Version      │ Lines │ Size │ Reduction │ Score │ Status       │
├──────────────┼───────┼──────┼───────────┼───────┼──────────────┤
│ Original     │ 1,057 │ 48K  │ baseline  │ N/A   │ Reference    │
│ V1           │ 520   │ 26K  │ 51%       │ 0.89  │ [REJECTED]   │
│ V2           │ 437   │ 27K  │ 59%       │ 0.97  │ [APPLIED]    │
╰──────────────┴───────┴──────┴───────────┴───────┴──────────────╯
```

### Progress banner (consistent header)
```
🐱 CAT › task-name
────────────────────────────────────────────────────────────────
```

## Acceptance Criteria
- [ ] cat:status outputs open-border format
- [ ] cat:token-report uses `[HIGH]`/`[EXCEEDED]` instead of ⚠ emoji
- [ ] cat:shrink-doc uses `[REJECTED]`/`[APPLIED]` instead of ❌/✓
- [ ] Progress banner shows `🐱 CAT › task-name` header consistently
- [ ] All checkpoint boxes use simplified format
- [ ] No display depends on emoji width calculation
- [ ] Scripts no longer source box.sh (enables removal in task 2)

## Execution Steps
1. **Update status.sh** - Rewrite to output open-border format without box.sh
   - Files: plugin/scripts/status.sh
   - Verify: Run status.sh and check output format

2. **Update token-report skill** - Change indicator format in examples
   - Files: plugin/skills/token-report/SKILL.md
   - Verify: Examples show ASCII indicators

3. **Update shrink-doc skill** - Change status column format
   - Files: plugin/skills/shrink-doc/SKILL.md
   - Verify: Examples show [REJECTED]/[APPLIED]

4. **Update work-progress.sh** - Simplify checkpoint and banner formats
   - Files: plugin/scripts/work-progress.sh
   - Verify: Banner shows consistent header

5. **Update render-box skill** - Document new simplified patterns
   - Files: plugin/skills/render-box/SKILL.md
   - Verify: Examples reflect new formats (prep for removal)

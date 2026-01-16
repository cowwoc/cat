# Plan: simplify-display-formats

## Goal
Update all CAT display outputs to use simplified formats with clear rules for when to use each format.

## Design Principles

1. **Open borders with emojis** - For single-column content (status displays, checkpoints, messages)
2. **Closed borders without emojis** - For multi-column tables (token reports, comparison tables)
3. **No empty line before closing** - Open border format ends directly with `╰─`
4. **Skills render directly** - Output rendering done exclusively by skills, not scripts
5. **Scripts for data only** - Scripts only used for multi-step data collection (e.g., status.sh)

## Satisfies
None - infrastructure/maintenance task

## Current State
- Status display uses full enclosed boxes with emoji padding calculations
- Tables use emojis in columns requiring complex width calculation
- Multiple scripts depend on box.sh library for rendering

## Target State
- Single-column displays use open-border style with emojis (no right border, no padding needed)
- Multi-column tables use closed borders with ASCII indicators: `[HIGH]`, `[REJECTED]`, `[APPLIED]`
- Warning emojis placed outside tables on the right side when needed
- All displays render correctly without emoji width calculation
- box.sh library and related infrastructure removed

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Multiple skills reference current format; must update consistently
- **Mitigation:** Update all format examples in skill files atomically

## Files Modified

### Removed
- `plugin/scripts/lib/box.sh` - Complex padding calculation library
- `plugin/emoji-widths.json` - Emoji width measurements
- `plugin/scripts/config-box.sh` - Config command boxes
- `plugin/scripts/get-emoji-width.sh` - Emoji width lookup
- `plugin/scripts/measure-emoji-widths.sh` - Emoji width measurement tool
- `plugin/scripts/pad-box-lines.sh` - Line padding utility
- `plugin/hooks/block-manual-box-chars.sh` - Hook blocking manual box chars
- `plugin/hooks/check-emoji-widths.sh` - Hook checking emoji widths
- `plugin/skills/render-box/SKILL.md` - Render box skill
- `plugin/scripts/init-banner.sh` - Rendering moved to init.md skill
- `plugin/scripts/work-progress.sh` - Rendering moved to work.md skill
- `plugin/scripts/stakeholder-review.sh` - Rendering moved to stakeholder-review skill

### Updated
- `plugin/.claude/cat/references/display-standards.md` - New display patterns
- `plugin/commands/status.md` - Script outputs JSON, skill renders open-border
- `plugin/commands/config.md` - Removed config-box.sh references, inline box formats
- `plugin/commands/init.md` - Inline banner formats (no script)
- `plugin/commands/work.md` - Inline progress formats (no script)
- `plugin/scripts/status.sh` - Outputs JSON data for skill to render
- `plugin/hooks/detect-user-reported-issue.sh` - Echo-based open-border format
- `plugin/hooks/hooks.json` - Removed references to deleted hooks
- `plugin/skills/token-report/SKILL.md` - Closed borders, ASCII indicators
- `plugin/skills/shrink-doc/SKILL.md` - ASCII status indicators
- `plugin/skills/stakeholder-review/SKILL.md` - Inline box formats (no script)

## New Display Formats

### Open-border (single column, emojis OK)
```
╭─ 🚀 CAT INITIALIZED
│
│  🤝 Trust: medium
│  🔍 Curiosity: low
│  ⏳ Patience: high
│
│  Your partner is ready. Let's build something solid.
│  Adjust anytime: /cat:config
╰─
```

### cat:status (open-border with nesting)
```
╭─
│ 📊 Overall: [████████████████░░░░░░░░░] 38%
│ 🏆 35/92 tasks complete
│
│ ╭─ 📦 v0: Major Version Name
│ │
│ │  ☑️ v0.1: Minor description (5/5)
│ │  🔄 v0.3: Current minor (3/5)
│ │    🔳 pending-task-1
│ ╰─
│
│ 🎯 Active: v0.3
╰─
```

### Closed-border table (multi-column, no emojis in cells)
```
╭─────────────────┬──────────────────────────────┬────────┬────────────────┬──────────╮
│ Type            │ Description                  │ Tokens │ Context        │ Duration │
├─────────────────┼──────────────────────────────┼────────┼────────────────┼──────────┤
│ Explore         │ Explore codebase             │ 68.4k  │ 34%            │ 1m 7s    │
│ general-purpose │ Implement fix                │ 45.0k  │ 45% [HIGH]     │ 43s      │ ⚠️
│ general-purpose │ Refactor module              │ 170.0k │ 85% [EXCEEDED] │ 3m 12s   │ 🚨
├─────────────────┼──────────────────────────────┼────────┼────────────────┼──────────┤
│                 │ TOTAL                        │ 283.4k │ -              │ 5m 2s    │
╰─────────────────┴──────────────────────────────┴────────┴────────────────┴──────────╯
```

**Column widths:** Type=17, Description=30, Tokens=8, Context=16, Duration=10
Note: Warning emojis ⚠️/🚨 placed OUTSIDE the table on the right.

## Acceptance Criteria
- [x] cat:status outputs open-border format
- [x] cat:token-report uses `[HIGH]`/`[EXCEEDED]` instead of ⚠ emoji in cells
- [x] cat:shrink-doc uses `[REJECTED]`/`[APPLIED]` instead of ❌/✓
- [x] All checkpoint boxes use simplified format
- [x] No display depends on emoji width calculation
- [x] box.sh library and related infrastructure removed
- [x] Open borders have no empty line before ╰─

# Plan: remove-box-rendering-infrastructure

## Goal
Remove obsolete box rendering infrastructure now that displays use simplified formats without emoji width calculation.

## Satisfies
None - infrastructure cleanup task

## Current State
- `box.sh` library provides emoji-aware padding functions
- `emoji-widths.json` stores measured terminal emoji widths
- Multiple scripts exist to measure and update emoji widths
- `render-box` skill documents complex box rendering
- `display-standards.md` documents padding algorithms

## Target State
- All emoji width infrastructure removed
- No box.sh library dependency
- Simpler, self-contained display scripts
- Reduced plugin size and complexity

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must complete simplify-display-formats first to remove dependencies
- **Mitigation:** Verify no scripts source box.sh before removal

## Files to Remove

### Emoji width infrastructure
- `plugin/emoji-widths.json` - Measured emoji widths data
- `plugin/scripts/get-emoji-width.sh` - Lookup emoji width
- `plugin/scripts/measure-emoji-widths.sh` - Measure widths in terminal
- `plugin/hooks/check-emoji-widths.sh` - Hook to verify emoji widths
- `plugin/scripts/pad-box-lines.sh` - Pad lines with emoji-aware widths

### Box rendering library
- `plugin/scripts/lib/box.sh` - Shared box rendering functions

### Documentation
- `plugin/.claude/cat/references/display-standards.md` - Complex padding documentation
- `plugin/skills/render-box/SKILL.md` - Box rendering skill (entire directory)

## Acceptance Criteria
- [ ] No files reference box.sh
- [ ] No files reference emoji-widths.json
- [ ] All emoji width scripts removed
- [ ] render-box skill removed
- [ ] display-standards.md removed
- [ ] Plugin still functions correctly
- [ ] All tests pass (if any)

## Execution Steps
1. **Verify no dependencies remain** - Grep for box.sh and emoji-widths references
   - Command: `grep -r "box.sh\|emoji-widths" plugin/ --include="*.sh" --include="*.md"`
   - Verify: No matches except files being deleted

2. **Remove emoji width infrastructure**
   - Files:
     - plugin/emoji-widths.json
     - plugin/scripts/get-emoji-width.sh
     - plugin/scripts/measure-emoji-widths.sh
     - plugin/hooks/check-emoji-widths.sh
     - plugin/scripts/pad-box-lines.sh
   - Verify: `ls plugin/scripts/*emoji* plugin/hooks/*emoji*` returns nothing

3. **Remove box.sh library**
   - Files: plugin/scripts/lib/box.sh
   - Verify: `ls plugin/scripts/lib/` shows no box.sh

4. **Remove render-box skill**
   - Files: plugin/skills/render-box/ (entire directory)
   - Verify: `ls plugin/skills/` shows no render-box

5. **Remove display-standards.md**
   - Files: plugin/.claude/cat/references/display-standards.md
   - Verify: File no longer exists

6. **Update settings.json** - Remove render-box from skill list if referenced
   - Files: plugin/settings.json (if skill is registered there)
   - Verify: No broken skill references

7. **Final verification** - Run cat:status to confirm displays work
   - Command: Run /cat:status
   - Verify: Output renders correctly without box.sh

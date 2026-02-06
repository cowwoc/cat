# Plan: centralize-emoji-widths

## Current State
Multiple files define their own `WIDTH_2_EMOJIS` sets and `display_width()` functions with hardcoded emoji width
assumptions, duplicating logic that should come from the centralized `lib/emoji_widths.py` module.

## Target State
All emoji width handling uses `lib/emoji_widths.py` as the single source of truth. No other code makes assumptions about
emoji display widths.

## Satisfies
None - infrastructure/code quality task

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - same behavior, different implementation
- **Mitigation:** Tests exist for display utilities; verify alignment still works

## Files to Modify

### Primary Offenders (have their own WIDTH_2_EMOJIS and display_width)

1. **plugin/hooks/skill_handlers/status_handler.py** (lines 17-62)
   - Has `WIDTH_2_EMOJIS` set (lines 18-22)
   - Has `WIDTH_2_SINGLE` set (lines 25-29)
   - Has local `display_width()` function (lines 32-62)
   - Change: Import from `lib.emoji_widths` instead

2. **plugin/scripts/build_box_lines.py** (lines 34-79)
   - Has `WIDTH_2_EMOJIS` set (lines 35-39)
   - Has `WIDTH_2_SINGLE` set (lines 42-46)
   - Has local `display_width()` function (lines 49-79)
   - Change: Import from `lib.emoji_widths` instead

3. **plugin/scripts/render-diff.py** (lines 80-101)
   - Has local `display_width()` function with hardcoded VS16 logic
   - Comment: "if so, current is emoji width 2" (line 90)
   - Change: Import from `lib.emoji_widths` instead

### Secondary Offenders (hardcoded width comments/values)

4. **plugin/scripts/precompute-config-display.sh** (lines 111-113)
   - Comment: "Calculate display width of title (emoji=2, others=1)"
   - Hardcoded: `TITLE_DISPLAY_WIDTH=19`
   - Change: Call Python/emoji_widths.py to calculate dynamically

5. **plugin/scripts/compute-token-table.py** (line 33)
   - Comment: "Must fit '85% ' + emoji (display width 2) + padding"
   - Already imports from emoji_widths - just update comment to not assume width

### Documentation with Hardcoded Assumptions

6. **plugin/skills/skill-builder/SKILL.md** (multiple lines)
   - Line 228: "Logic: Sum character widths (emoji=2, others=1)"
   - Line 598: "ATOMIC: Use width lookup (emoji → 2, other → 1)"
   - Line 608: "1. Width lookup table (emoji → 2, other → 1)"
   - Line 674: "if char in [...]: width += 2"
   - Change: Update to reference emoji_widths.py lookup, not hardcoded values

7. **plugin/skills/render-box/SKILL.md** (line 201)
   - Shows `EMOJI_WIDTH=2` as a "WRONG" example
   - This is already correct (showing what NOT to do) - verify no changes needed

### Files Already Correct (for reference)

- **plugin/scripts/lib/emoji_widths.py** - The source of truth (do not modify)
- **plugin/scripts/build-init-boxes.py** - Already imports from lib.emoji_widths
- **plugin/scripts/compute-token-table.py** - Already imports from emoji_widths

## Acceptance Criteria
- [ ] No file defines its own WIDTH_2_EMOJIS or WIDTH_2_SINGLE sets
- [ ] No file defines its own display_width() function (except emoji_widths.py)
- [ ] All display_width calls use the lib/emoji_widths module
- [ ] No hardcoded "emoji=2" or "width 2" comments for width calculations
- [ ] Existing tests pass (tests/handlers/test_display_utils.py)
- [ ] Box alignment in /cat:status still renders correctly

## Execution Steps

1. **Update status_handler.py**
   - Remove WIDTH_2_EMOJIS, WIDTH_2_SINGLE, display_width()
   - Add: `from lib.emoji_widths import display_width`
   - Verify: Python import works from hooks/skill_handlers/

2. **Update build_box_lines.py**
   - Remove WIDTH_2_EMOJIS, WIDTH_2_SINGLE, display_width()
   - Add: `from lib.emoji_widths import display_width`
   - Verify: `python plugin/scripts/build_box_lines.py "📊 test"` works

3. **Update render-diff.py**
   - Remove local display_width() function
   - Add: `from lib.emoji_widths import display_width`
   - Verify: Diff rendering still aligns correctly

4. **Update precompute-config-display.sh**
   - Replace hardcoded TITLE_DISPLAY_WIDTH=19 with dynamic calculation
   - Call: `python -c "from lib.emoji_widths import display_width; print(display_width('⚙️ CURRENT SETTINGS'))"`
   - Verify: /cat:config box aligns correctly

5. **Update skill-builder/SKILL.md documentation**
   - Replace "emoji=2, others=1" with "use emoji_widths.py lookup"
   - Keep examples that show the concept, but don't hardcode specific widths

6. **Run tests and verify**
   - Run: `python -m pytest tests/handlers/test_display_utils.py`
   - Visual check: `/cat:status` renders aligned boxes

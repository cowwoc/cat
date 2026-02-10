# Plan: Update Diff Column Format

## Goal
Merge the separate "Old" and "New" line number columns into a single "Line" column showing both numbers, and reorder
columns so +/- follows immediately after. This reduces visual noise and makes diffs more compact.

## Satisfies
- None (usability improvement)

## Type
refactor

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Column width changes affect content_width calculation; wrapped lines must still align
- **Mitigation:** All formatting logic is in one file; manual visual verification

## Files to Modify
- `plugin/scripts/render-diff.py` - Column constants, header, row rendering, separators, wrapping

## Acceptance Criteria
- [ ] "Old" and "New" columns replaced by single "Line" column showing `old:new` format
- [ ] For context lines: shows `old:new` (e.g., `42:42`)
- [ ] For deletions: shows `old:` (e.g., `42:`)
- [ ] For additions: shows `:new` (e.g., `:43`)
- [ ] +/- symbol column follows immediately after the Line column
- [ ] Content column remains last
- [ ] Header row shows "Line" instead of "Old" and "New"
- [ ] Box-drawing separators updated for new column count (3 columns instead of 4)
- [ ] Wrapped continuation lines align correctly with new layout
- [ ] content_width recalculated for new fixed-width overhead

## Execution Steps
1. **Update column constants:** Replace `COL_OLD=4` and `COL_NEW=4` with `COL_LINE=7` (enough for `nnn:nnn`).
   Remove `COL_NEW`. Keep `COL_SYM=3`.
   - Files: `plugin/scripts/render-diff.py` lines 62-65
2. **Update content_width calculation:** Recalculate fixed overhead. Current: 17 chars
   (`│` + 4 + `│` + 3 + `│` + 4 + `│` + space + `│` = 17). New: 3 columns instead of 4, so
   `│` + 7 + `│` + 3 + `│` + space + content + `│` = 14 fixed chars.
   - Files: `plugin/scripts/render-diff.py` line 164-165
3. **Update `_print_column_header`:** Change header from `Old`/`New` to single `Line` label. Update separator
   box-drawing to use 3 columns.
   - Files: `plugin/scripts/render-diff.py` lines 262-290
4. **Update `_print_row`:** Change signature from `(old_num, symbol, new_num, content)` to
   `(line_num, symbol, content)` where `line_num` is the pre-formatted `old:new` string. Update row formatting
   to use COL_LINE and 3 columns.
   - Files: `plugin/scripts/render-diff.py` lines 292-332
5. **Update `_print_hunk_bottom`:** Update box-drawing for 3 columns.
   - Files: `plugin/scripts/render-diff.py` lines 334-341
6. **Update all `_print_row` call sites:** Format line numbers as `old:new`, `old:`, or `:new` before calling.
   - Files: `plugin/scripts/render-diff.py` lines 421-477
7. **Update `_print_hunk_top`:** If it has column-related separators, update for 3-column layout.
   - Files: `plugin/scripts/render-diff.py` lines 253-260
8. **Visual verification:** Run render-diff on a real diff and verify output looks correct.

## Success Criteria
- [ ] Diff output uses 3 columns: Line, +/-, Content
- [ ] All box-drawing characters align correctly
- [ ] Line wrapping continuation rows align with new column layout

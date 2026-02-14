# Plan: Update Diff Column Format

## Goal
Redesign the diff table from a 4-column layout (Old | New | Symbol | Content) to a compact 2-column layout
(Line+Symbol | Content). Line numbers are dynamic-width, the +/- indicator sits immediately after the `│` separator,
and content always starts at the same column.

## Satisfies
- None (usability improvement)

## Type
refactor

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Dynamic column width requires per-hunk calculation; wrapped lines must still align
- **Mitigation:** All formatting logic is in one file; manual visual verification

## Files to Modify
- `plugin/scripts/render-diff.py` - Column constants, header, row rendering, separators, wrapping

## Target Format
```
╭──┬─ plugin/scripts/render-diff.py ───────────────────────────────────╮
├──┼─ ⌁ BOX_T_LEFT = '┤' ─────────────────────────────────────────────┤
│64│  BOX_CROSS = '┼'                                                  │
│65│                                                                    │
│66│  # Column widths (fixed)                                           │
│67│- COL_OLD = 4   # Old line number                                   │
│67│+ COL_LINE = 7  # Line number (format: old:new)                     │
│68│  COL_SYM = 3   # Symbol (+/-)                                      │
│69│- COL_NEW = 4   # New line number                                   │
│70│                                                                    │
╰──┴────────────────────────────────────────────────────────────────────╯
```

## Acceptance Criteria
- [ ] 2-column layout: line number column `│` indicator+content column
- [ ] Line number column width is dynamic, sized to max line number digits in each hunk
- [ ] All lines show line numbers: context=new line, deletion=old line, addition=new line
- [ ] After `│` separator: `- ` for deletions, `+ ` for additions, `  ` (2 spaces) for context
- [ ] Content always starts at the same column position regardless of line type
- [ ] Wrapped continuation lines have blank line number column and `  ` indicator
- [ ] Compact header: file name in top border row, no separate "Line" label row
- [ ] Hunk context in `├──┼─ ⌁ context ─┤` separator (replaces old column header row)
- [ ] Column markers in all structural borders: `┬` (top), `┼` (hunk sep), `┴` (bottom)
- [ ] Remove `COL_OLD`, `COL_NEW`, `COL_SYM` constants; replace with dynamic `col_line` per hunk
- [ ] content_width recalculated per hunk based on dynamic line column width
- [ ] Max displayable line number is 9999 (4 digits)
- [ ] Multi-hunk same file: subsequent hunks use `├──┼─ ⌁ context ─┤` as separator

## Execution Steps
1. **Remove fixed column constants:** Delete `COL_OLD`, `COL_NEW`, `COL_SYM`. Add a helper method
   `_calc_col_width(hunk)` that returns the digit count for the max line number in the hunk, capped at 4.
   - Files: `plugin/scripts/render-diff.py`
2. **Add per-hunk rendering state:** Before rendering each hunk, calculate `col_line` (digit width) and
   `content_width` (total width - `│` - col_line - `│` - 2 indicator chars - content - `│` = col_line + 5 fixed).
   - Files: `plugin/scripts/render-diff.py`
3. **Update `_print_hunk_top`:** Embed file name in top border with `┬` at column boundary:
   `╭──┬─ filename ─╮`. Only print for first hunk of each file.
   - Files: `plugin/scripts/render-diff.py`
4. **Replace `_print_column_header` with `_print_hunk_separator`:** Render `├──┼─ ⌁ context ─┤`
   with `┼` at the column boundary. No separate header label row.
   - Files: `plugin/scripts/render-diff.py`
5. **Update `_print_row`:** New signature `(line_num, indicator, content)` where `indicator` is `- `, `+ `, or `  `.
   Format: `│{line_num:>col_line}│{indicator}{content padded}│`. Handle wrapping with blank line number and `  `.
   - Files: `plugin/scripts/render-diff.py`
6. **Update `_print_hunk_bottom`:** Render `╰──┴───╯` with `┴` at column boundary.
   - Files: `plugin/scripts/render-diff.py`
7. **Update `_render_hunk_content` call sites:** Pass line numbers as integers (not formatted strings).
   Context lines pass new_line, deletions pass old_line, additions pass new_line. Indicator is `- `, `+ `, or `  `.
   - Files: `plugin/scripts/render-diff.py`
8. **Handle multi-hunk same file:** Track current file. First hunk gets `_print_hunk_top` + `_print_hunk_separator`.
   Subsequent hunks for same file get only `_print_hunk_separator` (no top border/filename).
   - Files: `plugin/scripts/render-diff.py`
9. **Visual verification:** Run render-diff on a real diff and verify output matches target format.

## Success Criteria
- [ ] Diff output uses 2 visual columns with dynamic line number width
- [ ] All box-drawing characters align correctly including `┬`/`┼`/`┴` column markers
- [ ] Line wrapping continuation rows align with content column
- [ ] Multiple hunks in same file share one file header box

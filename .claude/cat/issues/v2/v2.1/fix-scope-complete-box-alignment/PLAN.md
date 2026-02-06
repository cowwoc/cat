# Plan: fix-scope-complete-box-alignment

## Problem
The Scope Complete box in `get-issue-complete-box.py` uses markdown bold markers (`**`) around the scope name
(e.g., `**v2.1**`). The `dw()` function counts the 4 `*` characters as display width, but Claude Code renders them as
bold formatting (invisible markers). This creates a 4-character misalignment between the content line and the top/bottom
borders.

## Satisfies
v2.1 success criterion: "Command output displays correctly aligned"

## Reproduction Code
```bash
python3 plugin/scripts/get-issue-complete-box.py --scope-complete "v2.1"
# Output has misaligned right border on content line vs top/bottom borders
```

## Expected vs Actual
- **Expected:** Right border `│` aligns with top `╮` and bottom `╯` on all lines
- **Actual:** Content line right border is shifted 4 characters right due to `**` markers counted by `dw()` but rendered
  invisible by markdown

## Root Cause
`plugin/scripts/get-issue-complete-box.py` line 114: `f"**{scope}** - all issues complete!"` includes markdown bold
markers that `dw()` counts as 4 characters of width, but the terminal strips them for bold rendering.

Compare with `build_issue_complete_box()` (line 74) which correctly uses plain text without markdown markers.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - removing formatting markers only affects display
- **Mitigation:** Visual verification of box output

## Files to Modify

| File | Change |
|------|--------|
| `plugin/scripts/get-issue-complete-box.py` | Remove `**` markers from line 114 in `build_scope_complete_box()` |
| `tests/test_get_issue_complete_box.py` | Add test verifying Scope Complete box alignment (all lines same width) |

## Acceptance Criteria
- [ ] Bug fixed - Scope Complete box has aligned borders
- [ ] Regression test added verifying all box lines have equal display width
- [ ] Existing tests still pass
- [ ] No new issues introduced

## Execution Steps
1. **Step 1:** Edit `plugin/scripts/get-issue-complete-box.py`
   - Change line 114 from: `f"**{scope}** - all issues complete!"`
   - Change line 114 to: `f"{scope} - all issues complete!"`

2. **Step 2:** Add regression test
   - Find or create test file for get-issue-complete-box.py
   - Add test case: generate Scope Complete box, verify all lines have equal display width
   - Add test case: generate Issue Complete box, verify all lines have equal display width

3. **Step 3:** Run tests
   - `python3 /workspace/run_tests.py`

4. **Step 4:** Commit changes
   - Commit type: `config:` (plugin script modification)

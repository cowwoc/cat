# Plan: enhance-add-complete-multi-issue

## Goal
Enhance `render-add-complete.sh` to properly display multiple issues when created together,
rendering each as a separate entry rather than concatenating names on a single line.

## Current State
When multiple issues are created together (e.g., splitting a large task), the script receives
a comma-separated list of names and renders them as a single concatenated line:
```
╭─ ✅ Issue Created ─────────────────────────────────────────────╮
│ 2.1-issue1, issue2, issue3                                     │
│ Type: Refactor                                                 │
╰────────────────────────────────────────────────────────────────╯
```

## Target State
Render multiple issues as separate entries with proper formatting:
```
╭─ ✅ Issues Created ────────────────────────────────────────────╮
│ 1. issue1                                                      │
│ 2. issue2                                                      │
│ 3. issue3                                                      │
│                                                                │
│ Version: 2.1                                                   │
│ Type: Refactor                                                 │
╰────────────────────────────────────────────────────────────────╯
```

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - enhanced output format, backward compatible
- **Mitigation:** Detect comma-separated input and branch logic

## Files to Modify

| File | Changes |
|------|---------|
| `plugin/scripts/render-add-complete.sh` | Detect multi-issue input, render numbered list |
| `plugin/hooks/skill_handlers/add_handler.py` | Update `_build_issue_display` for multi-issue |

## Acceptance Criteria
- [ ] Single issue rendering unchanged (backward compatible)
- [ ] Multiple issues (comma-separated) render as numbered list
- [ ] Header changes to "Issues Created" (plural) for multiple
- [ ] Box alignment correct for both single and multi-issue cases

## Execution Steps

1. **Step 1:** Update `render-add-complete.sh` Python section
   - Detect comma in `item_name` input
   - Split into list and render numbered entries
   - Change header to plural "Issues Created" when multiple
   - Adjust "Next:" command to show first issue only

2. **Step 2:** Update `add_handler.py` `_build_issue_display` method
   - Same logic: detect list, render numbered entries
   - Ensure consistent behavior between script and handler

3. **Step 3:** Test both single and multi-issue scenarios
   - Single: `--name "my-issue"` → original format
   - Multi: `--name "issue1, issue2, issue3"` → numbered list

4. **Step 4:** Commit changes
   - Commit type: `feature:` (new capability)

## Success Criteria
- [ ] `render-add-complete.sh --name "single-issue"` produces original format
- [ ] `render-add-complete.sh --name "a, b, c"` produces numbered list with plural header
- [ ] Box alignment correct in both cases (verify with terminal output)

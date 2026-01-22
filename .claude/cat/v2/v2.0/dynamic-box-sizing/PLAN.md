# Plan: dynamic-box-sizing

## Goal
Update build-box-lines.py to automatically expand box width to fit content, ensuring
text inside boxes is never truncated or wrapped unexpectedly.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Box width calculation must account for emoji display widths correctly
- **Mitigation:** Existing display_width() function already handles emoji widths

## Files to Modify
- plugin/scripts/build-box-lines.py - Add dynamic width expansion logic

## Acceptance Criteria
- [ ] Boxes expand to fit longest content line
- [ ] Nested boxes (inner/outer) size correctly relative to each other
- [ ] /cat:status displays correctly with dynamic sizing
- [ ] No content truncation in rendered boxes

## Execution Steps
1. **Step 1:** Review current width calculation in build-box-lines.py
   - Files: plugin/scripts/build-box-lines.py
   - Verify: Understand how max_content_width is computed

2. **Step 2:** Implement dynamic expansion logic
   - Files: plugin/scripts/build-box-lines.py
   - Verify: Width expands based on content, respects --max-width if provided

3. **Step 3:** Test with /cat:status
   - Verify: Status display renders correctly with varying content lengths

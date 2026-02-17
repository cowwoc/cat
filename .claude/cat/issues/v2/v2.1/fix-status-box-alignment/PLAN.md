# Plan: fix-status-box-alignment

## Problem
The /cat:status output has misaligned right borders on version boxes. Each major version inner box is independently sized to its own content width, so boxes with more tasks are wider than boxes with fewer tasks. The right borders (╮, │, ╯) appear at different horizontal positions, creating a jagged appearance.

## Satisfies
None

## Expected vs Actual
- **Expected:** All inner version boxes have the same visual width, with right borders aligned vertically
- **Actual:** Inner boxes have different widths per major version, causing jagged right borders

## Root Cause
`GetStatusOutput.generateStatusDisplay()` (line 623) calls `display.buildInnerBox(header, innerContent)` without the `forcedWidth` parameter. Each call independently calculates `boxWidth = max(headerMinWidth, maxContentWidth)` in `DisplayUtils.buildInnerBox()` (line 413). Major versions with more/longer content produce wider boxes.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Box display could break if width calculation is wrong
- **Mitigation:** Existing tests + new alignment test

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` - Two-pass approach in generateStatusDisplay()
- `client/src/test/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutputTest.java` - Add alignment test

## Test Cases
- [ ] Original bug scenario: multiple major versions with different content widths produce boxes with equal visual width
- [ ] Edge case: single major version still renders correctly
- [ ] Edge case: major version with no minors still renders correctly
- [ ] All existing tests still pass

## Execution Steps
1. **Step 1:** In `GetStatusOutput.generateStatusDisplay()`, refactor the major version loop (lines 516-626) into a two-pass approach:
   - **Pass 1:** Build the `innerContent` list and `header` string for each major version, storing them in a list of pairs. For each pair, calculate the natural box width using the same logic as `DisplayUtils.buildInnerBox()`: `max(displayWidth(header) + 1, calculateMaxWidth(innerContent))`. Track the maximum width across all majors.
   - **Pass 2:** Call `display.buildInnerBox(header, innerContent, maxInnerWidth)` for each stored pair, passing the pre-calculated maximum as `forcedWidth`.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`

2. **Step 2:** To calculate natural box width in Pass 1 without duplicating DisplayUtils logic, add a public method to `DisplayUtils`:
   ```java
   public int calculateBoxWidth(String header, List<String> contentItems)
   {
     List<String> effectiveContent = contentItems.isEmpty() ? List.of("") : contentItems;
     int maxContentWidth = calculateMaxWidth(effectiveContent);
     int headerMinWidth = displayWidth(header) + 1;
     return Math.max(headerMinWidth, maxContentWidth);
   }
   ```
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/DisplayUtils.java`

3. **Step 3:** Add a test in `GetStatusOutputTest` that creates StatusData with 2 major versions having different content lengths and verifies all inner box lines have the same display width.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutputTest.java`

4. **Step 4:** Run all tests: `mvn -f client/pom.xml test`

## Success Criteria
- [ ] All inner version boxes in /cat:status output have the same visual width
- [ ] Right borders (╮, │, ╯) of all version boxes align at the same horizontal position
- [ ] Regression test verifies equal display width for inner box lines across multiple major versions with different content lengths
- [ ] All existing tests pass (mvn -f client/pom.xml test exit code 0)
- [ ] No new issues introduced
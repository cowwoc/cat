# Plan: fix-status-line-wrapping

## Problem
The /cat:status output renders lines that exceed terminal width when a task has many blocked-by dependencies. For example, `cleanup-ported-scripts` has 18 blocked-by dependencies which creates a single line ~500+ characters wide, causing the box-drawing display to overflow horizontally.

## Satisfies
None (display bugfix)

## Reproduction Code
```
/cat:status
# Observe cleanup-ported-scripts line overflows terminal
```

## Expected vs Actual
- **Expected:** Long lines (especially blocked-by lists) should word-wrap to fit within the configured terminal width
- **Actual:** Lines extend far beyond the terminal width, breaking the box rendering

## Root Cause
In `GetStatusOutput.java` lines 609-612, the blocked-by list is joined into a single string with no wrapping:
```java
String blockedStr = String.join(", ", task.blockedBy);
innerContent.add("   " + taskEmoji + " " + task.name + " (blocked by: " + blockedStr + ")");
```

The M376 fix (commit daa321ca) already caps `maxContentWidth` to `terminalWidth - 2`, which prevents the outer box borders from being too wide. However, the content lines themselves are still longer than the box width - `buildLine()` in `DisplayUtils.java` only pads shorter lines but does not truncate or wrap longer ones.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Other status display elements could be affected if wrapping logic is too aggressive
- **Mitigation:** Unit tests for wrapping edge cases

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/DisplayUtils.java` - Add a `wrapLine(String line, int maxWidth)` method that word-wraps a line, respecting emoji display widths. Continuation lines should be indented to align with the content start of the first line.
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` - In `generateStatusDisplay()`, after building `contentItems` and before the final rendering loop, wrap any content lines that exceed `maxBoxContentWidth`. Also wrap inner box content lines that exceed the available inner box width. Specifically:
  1. Before Pass 1 (line ~530), calculate `maxInnerBoxContentWidth` from `maxBoxContentWidth` minus the inner box border overhead (4 chars: "│ " prefix + " │" suffix)
  2. In the blocked-by line construction (lines 609-612), wrap the line if it exceeds `maxInnerBoxContentWidth`
  3. After the `maxContentWidth` cap (line 671), iterate through `contentItems` and wrap any line whose `displayWidth` exceeds `maxBoxContentWidth`
- `client/src/test/java/io/github/cowwoc/cat/hooks/skills/DisplayUtilsTest.java` - Add tests for the new `wrapLine()` method

## Test Cases
- [ ] Short blocked-by list (1-2 items) renders on single line
- [ ] Long blocked-by list (18 items like cleanup-ported-scripts) wraps across multiple lines
- [ ] Continuation lines are indented correctly
- [ ] Lines with emojis wrap correctly (emoji display width accounted for)
- [ ] Lines exactly at terminal width do not wrap
- [ ] Lines 1 character over terminal width do wrap
- [ ] Empty blocked-by list still works

## Execution Steps
1. **Step 1:** Read `.claude/cat/conventions/java.md` for code style conventions
2. **Step 2:** Add `wrapLine(String line, int maxWidth, int indentWidth)` method to `DisplayUtils.java` that:
   - Returns the original line unchanged if `displayWidth(line) <= maxWidth`
   - Breaks at the last space or comma before `maxWidth`
   - Indents continuation lines by `indentWidth` spaces
   - Handles emoji widths correctly via `displayWidth()`
   - Returns a `List<String>` of wrapped lines
3. **Step 3:** In `GetStatusOutput.generateStatusDisplay()`, compute `maxInnerBoxContentWidth = maxBoxContentWidth - 4` (accounting for inner box │ borders)
4. **Step 4:** Modify the blocked-by line construction in `generateStatusDisplay()` to use `wrapLine()` when the line exceeds `maxInnerBoxContentWidth`, adding all wrapped lines to `innerContent`
5. **Step 5:** After building `contentItems` and capping `maxContentWidth`, iterate and replace any line exceeding `maxBoxContentWidth` with its wrapped version
6. **Step 6:** Add unit tests for `wrapLine()` covering the test cases above
7. **Step 7:** Run `mvn -f client/pom.xml test` to verify all tests pass

## Success Criteria
- [ ] All test cases pass
- [ ] No regressions in existing DisplayUtils or GetStatusOutput tests
- [ ] E2E: Run `/cat:status` and verify that `cleanup-ported-scripts` blocked-by list wraps within terminal width instead of overflowing
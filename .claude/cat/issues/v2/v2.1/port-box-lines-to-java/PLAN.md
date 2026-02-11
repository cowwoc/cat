# Plan: port-box-lines-to-java

## Goal
Replace the Python delegation in ComputeBoxLines.java with native Java box computation logic, eliminating the dependency
on build_box_lines.py.

## Current State
`ComputeBoxLines.java` detects `#BOX_COMPUTE` markers in Bash commands but delegates to
`python3 plugin/scripts/build_box_lines.py` via ProcessRunner. The Python script handles display width calculation
(including emoji widths), line padding, and border rendering.

## Target State
`ComputeBoxLines.java` performs all box computation natively in Java using `DisplayUtils` for width calculation.
No Python subprocess spawning.

## Satisfies
Parent: 2.1-port-display-scripts

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Box output format must remain character-for-character identical
- **Mitigation:** Diff-based comparison of Python vs Java output for all box types

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/ComputeBoxLines.java` - Replace Python delegation with Java logic
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/ComputeBoxLinesTest.java` - Add/update tests

## Acceptance Criteria
- [ ] ComputeBoxLines.java computes box lines natively without calling Python
- [ ] Output is character-for-character identical to build_box_lines.py for all input scenarios
- [ ] Display width calculation handles emoji characters identically to Python's emoji_widths.py
- [ ] Border characters (╭, ╮, ╰, ╯, │, ─) and padding match Python output exactly
- [ ] Multi-line content with varying widths produces identical box layout
- [ ] Empty content and edge cases (single line, very long lines) produce identical output
- [ ] No Python subprocess spawning remains in the class
- [ ] All tests pass (`mvn -f hooks/pom.xml test`)

## Execution Steps
1. **Read `build_box_lines.py`** to catalog all computation logic: display width, line building, border rendering
2. **Read `emoji_widths.py`** to understand emoji width handling
3. **Read existing `DisplayUtils.java`** to identify reusable width/padding methods
4. **Implement box computation in Java** within ComputeBoxLines.java using DisplayUtils
5. **Compare output** of Java implementation against Python for representative inputs
6. **Write tests** covering: single line, multi-line, emoji content, empty content, wide characters
7. **Run tests:** `mvn -f hooks/pom.xml test`

## Success Criteria
- [ ] Python subprocess call removed from ComputeBoxLines.java
- [ ] All existing tests pass
- [ ] New tests verify output parity with Python for at least 5 distinct input scenarios

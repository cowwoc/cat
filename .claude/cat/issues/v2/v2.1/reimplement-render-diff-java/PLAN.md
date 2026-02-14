# Plan: reimplement-render-diff-java

## Current State
The render-diff output is produced by two implementations:
- `plugin/scripts/render-diff.py` (784 lines) — Python, uses new 2-column dynamic-width format
- `hooks/src/main/java/.../skills/GetRenderDiffOutput.java` (1235 lines) — Java, uses old 4-column fixed-width format

The Python script is the source of truth for the rendering format. The Java class already handles git operations, diff parsing, config loading, and has a DiffRenderer inner class — but its renderer produces the old 4-column layout.

## Target State
Single Java implementation producing the 2-column dynamic-width format. Python script removed.

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Output format changes from 4-column to 2-column in Java (matches Python)
- **Mitigation:** Compare Java output against Python output on same diff inputs

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRenderDiffOutput.java` — Update DiffRenderer inner class to 2-column format with dynamic line widths
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/GetRenderDiffOutputTest.java` — Add tests for 2-column rendering
- `plugin/scripts/render-diff.py` — Remove after Java parity verified

## Acceptance Criteria
- [ ] Java DiffRenderer produces 2-column layout: Line+Symbol | Content
- [ ] Dynamic line number column width (2-4 digits based on max line in hunk)
- [ ] Column markers in structural borders (┬ top, ┼ separator, ┴ bottom)
- [ ] Hunk separator with ⌁ context text: ├──┼─ ⌁ context ─┤
- [ ] 2-char indicator system: `- ` deletions, `+ ` additions, `  ` context
- [ ] Line wrapping with ↩ preserves alignment
- [ ] Whitespace visualization (· for spaces, → for tabs) preserved
- [ ] Binary file and rename indicators preserved
- [ ] Commit message header rendering preserved
- [ ] Dynamic legend showing only used symbols
- [ ] All existing tests pass, new tests added for 2-column format
- [ ] render-diff.py removed after parity verified
- [ ] User-visible behavior unchanged (output matches Python implementation)

## Execution Steps
1. **Read Python render-diff.py** to understand the 2-column format implementation details
   - Files: `plugin/scripts/render-diff.py`
2. **Read Java GetRenderDiffOutput.java** to understand the existing DiffRenderer structure
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRenderDiffOutput.java`
3. **Read Java conventions** before editing Java files
   - Files: `.claude/cat/conventions/java.md`
4. **Update DiffRenderer inner class** in GetRenderDiffOutput.java:
   - Replace fixed column constants (OLD_LINE_WIDTH, SYMBOL_WIDTH, NEW_LINE_WIDTH) with dynamic `calcColWidth(DiffHunk)` method
   - Change `printRow()` signature to `(int lineNum, String indicator, String content)` — single line number + 2-char indicator
   - Update `printHunkTop()` to embed filename with ┬ column marker
   - Update `printHunkSeparator()` to use `├──┼─ ⌁ context ─┤` format
   - Update `printHunkBottom()` to use ┴ column marker
   - Update `renderHunkContent()` to show old line number for deletions, new for additions, both for context
   - Update line wrapping to align with 2-column layout
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRenderDiffOutput.java`
5. **Add tests for 2-column rendering**:
   - Test dynamic column width calculation with various line number ranges
   - Test per-hunk width recalculation
   - Test content column alignment across all row types
   - Test wrapped line formatting
   - Test hunk context parsing with empty and long context strings
   - Files: `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/GetRenderDiffOutputTest.java`
6. **Run full build verification**: `mvn -f hooks/pom.xml verify`
7. **Compare output** by running both Python and Java on the same diff to verify parity
8. **Remove render-diff.py** after parity confirmed
   - Files: `plugin/scripts/render-diff.py`

## Success Criteria
- [ ] `mvn -f hooks/pom.xml verify` passes (all tests + checkstyle + PMD)
- [ ] Java output matches Python output format on sample diffs
- [ ] render-diff.py no longer exists in the repository
# Plan: output-tag-skip-backtick-quoted

## Problem

SkillLoader's `OUTPUT_TAG_PATTERN` regex matches `<output>` tags inside backtick-quoted text, causing catastrophic
instruction corruption. In `status-first-use/SKILL.md`, the instruction text:

```
Echo the contents of the latest `<output skill="status">` tag verbatim.
```

was parsed as an actual `<output skill="status">` opening tag. The regex matched from the backtick-quoted reference all
the way to the real `</output>` at the end of the file, treating the entire instruction body + NEXT STEPS table as
"output body" content. This caused 5 recurring failures (M341, M353-M355, M372) across multiple sessions.

## Root Cause

`SkillLoader.java` line 82-83:
```java
private static final Pattern OUTPUT_TAG_PATTERN = Pattern.compile(
  "<output(?:\\s[^>]*)?>(.+?)</output>", Pattern.DOTALL);
```

This regex has no awareness of markdown backtick quoting. When skill instructions reference `<output>` tags literally
(a common documentation pattern), the regex treats the reference as an actual tag boundary.

## Satisfies

None (infrastructure bugfix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must not break any existing SKILL.md files that use `<output>` tags correctly.
- **Mitigation:** Scan all existing SKILL.md files for `<output>` usage patterns. Add unit tests covering both
  backtick-quoted references and real `<output>` tags.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — Update `parseContent()` to strip or skip
  backtick-quoted content before matching `<output>` tags
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` (or new test file) — Add tests for
  backtick-quoted `<output>` references
- Any SKILL.md files referencing `<output>` tags literally — Ensure they use backtick quoting (`` `<output>` ``)

## Approach

Instead of changing the regex pattern itself, modify `parseContent()` to:
1. Pre-process the content by replacing backtick-quoted segments (`` `...` ``) with placeholder tokens
2. Run the existing regex on the sanitized content
3. Use the match positions from the sanitized content to extract from the original content

This preserves the existing regex behavior while making it immune to backtick-quoted tag references. The convention
becomes: any `<output>` reference in instruction text that should NOT be parsed must be wrapped in backticks.

## Acceptance Criteria
- [ ] `parseContent()` ignores `<output>` tags inside backtick-quoted text (`` `<output ...>` ``)
- [ ] `parseContent()` still correctly finds real `<output>` tags that are NOT backtick-quoted
- [ ] The status-first-use/SKILL.md pattern (backtick-quoted reference + real `<output>` tag) works correctly
- [ ] All existing SKILL.md files with `<output>` tags continue to work
- [ ] Unit tests cover: backtick-quoted reference, real tag, mixed content, nested backticks
- [ ] Any SKILL.md files that reference `<output>` literally are updated to use backtick quoting

## Execution Steps
1. **Audit existing SKILL.md files for `<output>` usage:** Find all files that reference `<output>` tags, distinguish
   between real tags (should be parsed) and literal references (should be backtick-quoted)
   - Files: `plugin/skills/**/SKILL.md`
2. **Update `parseContent()` in SkillLoader.java:** Add backtick-stripping logic before regex matching. Replace
   backtick-quoted segments with equal-length placeholder text, run regex, map positions back to original content.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
3. **Add unit tests:** Test cases for backtick-quoted `<output>` references, real `<output>` tags, and the specific
   status-first-use pattern that caused M341-M372.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` (new or existing)
4. **Update SKILL.md files:** Ensure all literal `<output>` references in instruction text use backtick quoting.
   - Files: Any SKILL.md files identified in step 1
5. **Restore status-first-use/SKILL.md:** Revert M372's workaround changes and use the correct fix (backtick-quoted
   reference with the fixed parser).
   - Files: `plugin/skills/status-first-use/SKILL.md`
6. **Run full test suite:** Verify no regressions.

## Success Criteria
- [ ] `mvn -f client/pom.xml test` passes with all new tests
- [ ] The status skill correctly delivers full instructions when backtick-quoted reference is present

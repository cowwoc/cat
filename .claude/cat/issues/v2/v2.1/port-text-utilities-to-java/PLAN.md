# Plan: port-text-utilities-to-java

## Goal
Port text processing and validation utilities to Java classes in the hooks module.

## Current State
Three scripts handle markdown wrapping, diff rendering wrapper, and status validation via Python/Bash.

## Target State
Java classes replacing these scripts with equivalent functionality.

## Satisfies
Parent: port-utility-scripts

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None if output matches
- **Mitigation:** Compare output of old vs new for sample inputs

## Scripts to Port
- `wrap-markdown.py` (305 lines) - Markdown line wrapping at 120 chars with preservation rules
  (code blocks, tables, YAML frontmatter, box-drawing, URLs, HTML)
- `get-render-diff.sh` (139 lines) - Wrapper that calls render-diff.py; needs update to call
  existing `GetRenderDiffOutput.java` instead
- `validate-status-alignment.sh` (95 lines) - STATUS.md consistency checks

## Files to Create
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/MarkdownWrapper.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/StatusAlignmentValidator.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/MarkdownWrapperTest.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/StatusAlignmentValidatorTest.java`

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` - If needed
- `plugin/scripts/get-render-diff.sh` - Update to call Java instead of Python

## Execution Steps
1. Read wrap-markdown.py to understand preservation rules and wrapping logic
2. Create `MarkdownWrapper` class implementing wrapping with all preservation rules
3. Read validate-status-alignment.sh to understand consistency checks
4. Create `StatusAlignmentValidator` class implementing checks
5. Update get-render-diff.sh to invoke Java GetRenderDiffOutput instead of Python
6. Write tests for MarkdownWrapper and StatusAlignmentValidator
7. Run `mvn verify` to confirm all tests pass

## Success Criteria
- [ ] wrap-markdown.py fully replaced by MarkdownWrapper.java
- [ ] validate-status-alignment.sh replaced by StatusAlignmentValidator.java
- [ ] get-render-diff.sh updated to use Java path
- [ ] All tests pass (`mvn verify`)

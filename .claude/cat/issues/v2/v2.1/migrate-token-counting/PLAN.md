# Plan: migrate-token-counting

## Metadata
- **Parent:** migrate-python-to-java
- **Wave:** 3 (can run concurrent with handler sub-issues)
- **Estimated Tokens:** 15K

## Goal
Replace Python tiktoken token counting in compare-docs skill with Java JTokkit. This eliminates the last Python dependency outside of hook handlers.

## Satisfies
None - infrastructure/setup task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Token count differences between tiktoken and JTokkit
- **Mitigation:** Allow +/-1% tolerance; validate on sample files

## Dependencies to Add to pom.xml
- `com.knuddels:jtokkit:1.1.0` - Java tokenizer library (tiktoken equivalent)

## Files to Create
- `plugin/hooks/src/io/github/cowwoc/cat/hooks/TokenCounter.java` - Utility class for token counting
  - Usage: `java -cp cat-hooks.jar io.github.cowwoc.cat.hooks.TokenCounter file1.md file2.md`
  - Output: JSON with token counts per file

## Files to Modify
- `hooks/pom.xml` - Add JTokkit dependency
- `plugin/skills/compare-docs/SKILL.md` - Update token counting command from Python to Java

## Execution Steps
1. **Add JTokkit dependency** to `hooks/pom.xml`
2. **Create TokenCounter.java** - Accept file paths as args, output JSON token counts
3. **Update compare-docs SKILL.md** - Replace `python3 -c "import tiktoken..."` with `java -cp cat-hooks.jar io.github.cowwoc.cat.hooks.TokenCounter`
4. **Test token counting** on sample markdown files
5. **Compare results** with Python tiktoken output
6. **Run `mvn test`** to verify build passes with new dependency

## Acceptance Criteria
- [ ] TokenCounter.java produces accurate token counts
- [ ] Token counts match Python tiktoken within +/-1% tolerance
- [ ] compare-docs SKILL.md uses Java command instead of Python
- [ ] JTokkit dependency added to pom.xml
- [ ] `mvn test` passes

# Plan: preprocessor-skill-output-tags

## Problem
When the SkillLoader preprocessor fails to expand a `!` backtick directive (e.g., due to the DOTALL regex bug), the
agent receives raw unexpanded directive text. The agent then hallucinated output instead of recognizing the failure.
Empirical testing confirmed 0/5 pass rate — haiku completely ignores "echo verbatim" instructions when the content
looks like an unexpanded command.

## Fix
Wrap successful preprocessor directive output in `<skill-output>` XML tags. Update skill templates to echo content
within the tag. If the tag is missing, the agent knows preprocessing failed and tells the user to file a bug report.

This is defense-in-depth — the primary fix (DOTALL + fail-fast) prevents the scenario, but the XML tag provides a
structural signal the agent can reliably detect.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Changes output format for all preprocessor directives globally
- **Mitigation:** Only `invokeSkillOutput` wraps in tags; error messages are not wrapped

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` - Wrap `invokeSkillOutput` return in
  `<skill-output>` tags
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` - Update tests expecting output to
  include tags
- `plugin/skills/status/first-use.md` - Reference `<skill-output>` tag instead of marker text
- `plugin/skills/run-retrospective/first-use.md` - Reference `<skill-output>` tag
- `plugin/skills/statusline/first-use.md` - Reference `<skill-output>` tag

## Acceptance Criteria
- [ ] `invokeSkillOutput` wraps successful output in `<skill-output>` tags
- [ ] Error messages from `invokeSkillOutput` are NOT wrapped in tags
- [ ] All 3 skill first-use.md files updated to reference `<skill-output>` tag
- [ ] All existing SkillLoader tests pass (updated for tags)
- [ ] Empirical test with broken output (no tag) confirms agent detects failure

## Execution Steps
1. **Wrap invokeSkillOutput return value** in `<skill-output>` tags in SkillLoader.java
2. **Update SkillLoaderTest.java** tests that assert on directive output to expect the tags
3. **Update status/first-use.md** to echo content within `<skill-output>` tag
4. **Update run-retrospective/first-use.md** to reference `<skill-output>` tag
5. **Update statusline/first-use.md** to reference `<skill-output>` tag
6. **Run `mvn -f client/pom.xml verify`** to confirm all tests pass
7. **Rebuild jlink and install to plugin cache**
8. **Run empirical test** with broken output (no tag) to confirm agent detects failure

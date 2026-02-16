# Plan: preprocessor-error-bug-reporting

## Goal
When a preprocessor command fails, display a user-friendly error message with diagnostic info and offer to file a bug
report. Create a dedicated `/cat:report-bug` skill for duplicate-aware GitHub issue creation.

## Satisfies
None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** GitHub API rate limits, duplicate detection accuracy, error message formatting across different skills
- **Mitigation:** Use `gh` CLI for GitHub operations, fuzzy title matching for duplicates

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` - Change `<error>` tag output to
  user-friendly error message with bug report offer
- `plugin/skills/report-bug/first-use.md` - New skill for bug reporting
- `plugin/skills/report-bug/metadata.json` - Skill metadata

## Files to Create
- `plugin/skills/report-bug/first-use.md` - Bug reporting skill definition
- `plugin/skills/report-bug/metadata.json` - Skill metadata

## Acceptance Criteria
- [ ] Preprocessor RuntimeException produces user-friendly error message with skill name, directive, and error details
- [ ] Error message offers to invoke `/cat:report-bug` with pre-filled context
- [ ] `/cat:report-bug` skill checks GitHub issues for duplicates using `gh` CLI
- [ ] If duplicate found, subscribes to it and notifies user
- [ ] If no duplicate, creates new issue with diagnostic context
- [ ] Bug report includes: CAT version, skill name, preprocessor directive, error message, stack trace summary

## Execution Steps
1. **Step 1:** Modify SkillLoader.java error handling to produce user-friendly error message instead of `<error>` XML
   tags
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
2. **Step 2:** Create `/cat:report-bug` skill with duplicate detection and GitHub issue creation
   - Files: `plugin/skills/report-bug/first-use.md`, `plugin/skills/report-bug/metadata.json`
3. **Step 3:** Update existing tests for new error message format
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java`

## Success Criteria
- [ ] Preprocessor errors display readable error message (not XML tags)
- [ ] Bug report skill successfully creates/finds GitHub issues via `gh` CLI
- [ ] All existing SkillLoader tests pass with updated expectations

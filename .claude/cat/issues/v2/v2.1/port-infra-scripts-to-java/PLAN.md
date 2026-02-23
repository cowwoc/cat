# Plan: port-infra-scripts-to-java

## Current State
Three infrastructure bash scripts: `load-skill.sh` (skill preprocessor entry point, referenced by 23+ SKILL.md files),
`check-hooks-loaded.sh` (detects if hooks are loaded after plugin install), and `github-feedback.sh` (GitHub issue
search/creation for the feedback skill).

## Target State
All three scripts rewritten as Java tools in the jlink bundle. `load-skill.sh` is the most critical — it's the
preprocessor entry point for every skill.

## Satisfies
None (infrastructure/tech debt)

## Risk Assessment
- **Risk Level:** HIGH
- **Concerns:** `load-skill.sh` is invoked by every SKILL.md; breaking it breaks all skills. Also requires updating
  23+ SKILL.md preprocessor directives.
- **Mitigation:** TDD approach; test with real skill loading; update SKILL.md files atomically

## Files to Modify
- `plugin/scripts/load-skill.sh` — remove after port
- `plugin/scripts/check-hooks-loaded.sh` — remove after port
- `plugin/scripts/github-feedback.sh` — remove after port
- `client/src/main/java/...` — new Java implementations
- `client/src/test/java/...` — new tests
- `plugin/skills/delegate/first-use.md` — update check-hooks-loaded invocation
- `plugin/skills/feedback/first-use.md` — update github-feedback invocation
- `plugin/skills/*/SKILL.md` (23+ files) — update preprocessor directive to use Java load-skill

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Read `load-skill.sh` and document the full preprocessor pipeline
   - Files: `plugin/scripts/load-skill.sh`
2. **Step 2:** Write Java implementation for load-skill preprocessor
   - Files: `client/src/main/java/...`
3. **Step 3:** Write tests for load-skill Java implementation
   - Files: `client/src/test/java/...`
4. **Step 4:** Read `check-hooks-loaded.sh` and document behavior
   - Files: `plugin/scripts/check-hooks-loaded.sh`
5. **Step 5:** Write Java implementation for check-hooks-loaded
   - Files: `client/src/main/java/...`
6. **Step 6:** Read `github-feedback.sh` and document all subcommands
   - Files: `plugin/scripts/github-feedback.sh`
7. **Step 7:** Write Java implementation for github-feedback
   - Files: `client/src/main/java/...`
8. **Step 8:** Write tests for check-hooks-loaded and github-feedback
   - Files: `client/src/test/java/...`
9. **Step 9:** Update all 23+ SKILL.md preprocessor directives to use Java load-skill
   - Files: `plugin/skills/*/SKILL.md`
10. **Step 10:** Update skill first-use.md files for check-hooks-loaded and github-feedback
    - Files: `plugin/skills/delegate/first-use.md`, `plugin/skills/feedback/first-use.md`
11. **Step 11:** Remove the original bash scripts
12. **Step 12:** Run full test suite to verify no regressions

## Post-conditions
- [ ] User-visible behavior unchanged
- [ ] All tests passing
- [ ] Code quality improved
- [ ] E2E: Invoke a skill (e.g., `/cat:status`) and confirm load-skill preprocessor works end-to-end

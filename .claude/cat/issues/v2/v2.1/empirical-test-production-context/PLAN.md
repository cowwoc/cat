# Plan: empirical-test-production-context

## Goal
Add `system_prompt` and `system_reminders` support to the empirical test runner so tests can simulate production-like
context (CLAUDE.md, hook-injected system-reminders) that affects agent behavior.

## Satisfies
- None (infrastructure improvement discovered during M361 investigation)

## Motivation
The `/cat:status` verbatim echo failure (M361) could not be reproduced by the empirical test runner because the runner
sends zero system context. Real sessions include CLAUDE.md project instructions and hook-injected `<system-reminder>`
tags — both of which affect agent behavior. Without these, compliance tests give false 100% pass rates.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Stream-json format compatibility with `--append-system-prompt`
- **Mitigation:** Test with actual claude CLI to verify flags work

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/EmpiricalTestRunner.java` - Add config fields and CLI flags
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EmpiricalTestRunnerTest.java` - Add tests for new fields
- `plugin/skills/empirical-test-first-use/SKILL.md` - Document new config fields

## Acceptance Criteria
- [ ] Config JSON supports `system_prompt` field (string) passed as `--append-system-prompt` to claude CLI
- [ ] Config JSON supports `system_reminders` field (array of strings) injected as `<system-reminder>` tags in user
      messages before the test prompt
- [ ] `--help` output documents new config fields
- [ ] Existing tests still pass
- [ ] New unit tests cover: system_prompt passed to CLI, system_reminders injected into messages, both combined

## Execution Steps
1. **Add system_prompt support to EmpiricalTestRunner:** Read `system_prompt` from config, append
   `--append-system-prompt` flag to the claude CLI command when present.
   - Files: `EmpiricalTestRunner.java`
2. **Add system_reminders support to buildInput:** Read `system_reminders` from config, inject each as a user message
   wrapped in `<system-reminder>` tags. Insert them as the last user message before the test prompt (this matches how
   hooks inject reminders in production — they appear in the same turn as the user's message).
   - Files: `EmpiricalTestRunner.java`
3. **Update help text:** Add `system_prompt` and `system_reminders` to the help output.
   - Files: `EmpiricalTestRunner.java`
4. **Update skill documentation:** Add new fields to the config JSON documentation in the skill.
   - Files: `plugin/skills/empirical-test-first-use/SKILL.md`
5. **Add unit tests:** Test buildInput with system_reminders, test CLI command construction with system_prompt.
   - Files: `EmpiricalTestRunnerTest.java`
6. **Run tests:** `mvn -f client/pom.xml test`

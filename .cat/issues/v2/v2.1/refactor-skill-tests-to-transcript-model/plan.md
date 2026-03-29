# Plan

## Goal

Redesign skill tests to use stored transcripts and grader agents for SPRT evaluation. Each test
scenario runs once and stores the raw transcript in a `runs/` subdirectory (as defined by
2.1-revise-instruction-builder-file-targets). Assertions are evaluated by new dedicated grader
agents that read transcripts rather than inline during agent execution. All existing test-cases.json
and test-results.json files are migrated to the new format. Agents replaced by the new grader
model (e.g., cat:skill-validator-agent) are removed.

## Pre-conditions

- 2.1-revise-instruction-builder-file-targets is complete (defines runs/ directory location)

## Post-conditions

- [ ] New transcript-based test schema documented in plugin/concepts/skill-test.md (updated)
- [ ] cat:skill-grader-agent skill created: reads a transcript + assertion list, returns pass/fail per assertion
- [ ] cat:skill-validator-agent and any other agents superseded by the grader model are removed
- [ ] All existing test-cases.json files converted to .md scenario files with named assertions
- [ ] All existing test-results.json files replaced by results.json keyed by scenario filename and assertion name
- [ ] SPRT statistics tracked per assertion (not per file) in results.json
- [ ] ValidateSkillTestFormat hook updated to enforce new .md schema
- [ ] E2E verification: run a skill test end-to-end and confirm transcript stored in runs/, graded correctly, results.json updated
- [ ] Tests pass, no regressions

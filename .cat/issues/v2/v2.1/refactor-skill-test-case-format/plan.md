<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan

## Type

refactor

## Goal

Refactor the skill e2e test case format: replace test-cases.json and benchmark.json with a
test/ subdirectory convention per skill. Each test case becomes an individual markdown file.
Results are stored in results.json capturing skill hash, model, session, and SPRT data.
Create plugin/concepts/skill-test.md schema spec and add a Java-based hook to enforce format.
Migrate instruction-builder-agent's existing benchmark/ to test/ and delete the old directory.

## Pre-conditions

(none)

## Post-conditions

- [ ] plugin/concepts/skill-test.md exists with complete schema for test case markdown files
- [ ] test/ directory convention established per skill (plugin/skills/<skill>/test/)
- [ ] Each test case is an individual .md file with frontmatter (type, category, unit) and sections (## Scenario, ## Tier 1 Assertion, ## Tier 2 Assertion)
- [ ] results.json schema documents: skill_hash, model, session_id, timestamp, overall_decision, per-case SPRT data (log_ratio, pass_count, fail_count, total_runs, total_tokens, total_duration_ms)
- [ ] Java hook validates test/*.md files on write: enforces required frontmatter fields and required sections
- [ ] instruction-builder-agent/benchmark/ migrated to test/ and old benchmark/ directory deleted
- [ ] instruction-builder-agent/first-use.md updated to reference test/ path and results.json
- [ ] E2E: add a test case file manually and verify hook validates and rejects invalid format

## Sub-Agent Waves

### Wave 1

- Create plugin/concepts/skill-test.md with complete markdown test case schema (frontmatter fields: type, category, unit; required sections: Scenario, Tier 1 Assertion, Tier 2 Assertion)
- Create plugin/concepts/skill-test-results.md with results.json schema (skill_hash, model, session_id, timestamp, overall_decision, test_cases array with SPRT fields)
- Create Java hook class ValidateSkillTestFormat that validates test/*.md files on write (check required frontmatter and required sections, block writes that fail validation)
- Register the hook in plugin/hooks/hooks.json for PostToolUse on Edit/Write matching test/*.md pattern

### Wave 2

- Migrate instruction-builder-agent/benchmark/ test cases to instruction-builder-agent/test/ in markdown format (one .md file per test case)
- Delete instruction-builder-agent/benchmark/ directory
- Update instruction-builder-agent/first-use.md references from benchmark/test-cases.json to test/ directory and results.json

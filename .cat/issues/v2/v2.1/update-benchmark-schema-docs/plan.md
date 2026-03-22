# Plan

## Goal

Update `plugin/concepts/skill-benchmarking.md` to document the extended test-cases.json schema used by all
existing benchmarks, and migrate all three benchmark test-cases.json files to add `"version": "2.0"` and align
their structure with the documented schema.

Currently `skill-benchmarking.md` documents a bare-array format with string assertions and an `id` field, but
all actual benchmarks (instruction-builder-agent, get-output-agent, status-agent) use a richer format with
`test_case_id`, `semantic_unit_id`, `category`, `priming_messages`, and structured assertion objects
(deterministic/semantic types). The concept doc is stale and the three benchmark files have inconsistent
coverage of optional fields.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/concepts/skill-benchmarking.md` documents the full extended schema: root object with `version`
  and `test_cases` array; required fields (`test_case_id`, `semantic_unit_id`, `prompt`, `assertions`);
  optional fields (`category`, `priming_messages`); both string and structured assertion formats;
  deterministic assertion methods (`regex`, `string_match`, `structural`); semantic assertion fields
  (`instruction`, `expected`); and `semantic_unit_id` naming convention (domain-specific, not sequential)
- [ ] `plugin/concepts/skill-benchmarking.md` documents `priming_messages` as a first-class optional field
  with role/content format
- [ ] All three benchmark test-cases.json files contain `"version": "2.0"` at the root
- [ ] All three benchmark test-cases.json files use `test_case_id` (not `id`) and include `semantic_unit_id`
- [ ] User-visible behavior unchanged (no runtime behavior is modified — only docs and data files)
- [ ] Java test suite passes with no regressions (`mvn -f client/pom.xml test`)
- [ ] E2E: Load each updated test-cases.json through the benchmark runner to confirm it parses without errors

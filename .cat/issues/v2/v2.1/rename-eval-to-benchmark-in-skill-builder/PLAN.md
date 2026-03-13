# rename-eval-to-benchmark-in-skill-builder

## Goal

Replace "eval" terminology with "benchmark" throughout `plugin/skills/skill-builder-agent/first-use.md` for consistency with existing usage of "benchmark" in the aggregator, `benchmark.json`, and iteration cap language.

## Background

The benchmark evaluation loop in skill-builder uses "eval" as a prefix in commit messages (`eval:`), directory names (`eval-artifacts/`), variable names (`EVAL_ARTIFACTS_DIR`, `EVAL_SET_SHA`), and step labels. However, "benchmark" is already used for `benchmark.json`, `BenchmarkAggregator`, and iteration descriptions. Unifying on "benchmark" removes the inconsistency.

## Scope

- `plugin/skills/skill-builder-agent/first-use.md` — primary target
- Any companion files in `plugin/skills/skill-builder-agent/` that reference eval terminology (e.g., `e2e-dispute-trace.md`)

## Changes

1. Rename commit message prefix: `eval:` → `benchmark:`
2. Rename directory: `eval-artifacts/` → `benchmark-artifacts/`
3. Rename variables: `EVAL_ARTIFACTS_DIR` → `BENCHMARK_ARTIFACTS_DIR`, `EVAL_SET_SHA` → `BENCHMARK_SET_SHA`
4. Rename step label references from "eval" to "benchmark" where appropriate
5. Update the compliance checklist at the bottom of `first-use.md`

## Acceptance Criteria

- [ ] No occurrences of `eval-artifacts`, `EVAL_ARTIFACTS_DIR`, `EVAL_SET_SHA`, or `eval:` commit prefix remain in `plugin/skills/skill-builder-agent/first-use.md`
- [ ] No occurrences of `eval-artifacts` or `eval:` remain in `plugin/skills/skill-builder-agent/e2e-dispute-trace.md`
- [ ] All renamed terms replaced consistently with `benchmark-artifacts`, `BENCHMARK_ARTIFACTS_DIR`, `BENCHMARK_SET_SHA`, `benchmark:`
- [ ] Compliance checklist updated to reflect new names

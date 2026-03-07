# Plan: implement-skill-builder-benchmarks

## Goal

Extend skill-builder with a complete eval/benchmark/iterate loop comparable to Anthropic's skill-creator, using Java
tools and LLM subagents. The closed issue `2.1-adopt-skill-creator-eval-framework` added trigger-validation agents but
not the full iteration loop. This issue adds the missing capabilities: parallel test execution with baseline comparison,
quantitative benchmarking (pass rates, timing, tokens, mean +/- stddev), blind A/B comparison integrated into the
loop, description optimization with 60/40 train/test split, and a structured run-grade-review-improve-repeat cycle.

## Satisfies

None

## Background

`adopt-skill-creator-eval-framework` (closed) added:
- `skill-validator-agent` - trigger validation (should/should-not trigger prompts)
- `description-tester-agent` - description calibration queries
- `skill-comparison-agent` - rubric-based comparison of two skill versions
- `plugin/concepts/skill-validation.md` and `eval-patterns.md`
- Step 8 in skill-builder: generate test prompts, optionally delegate to skill-validator-agent

What is still missing compared to Anthropic's `skill-creator`:
1. **Parallel baseline runs**: with-skill vs. without-skill subagent executions per test case
2. **Quantitative benchmarking**: pass rate, timing (ms), tokens with mean +/- stddev, delta vs. baseline
3. **Grading/assertions**: structured assertion framework with pass/fail verdicts and evidence
4. **Blind A/B comparison loop**: integrate skill-comparison-agent into the iteration cycle
5. **Description optimization loop**: 60/40 train/test split, up to 5 iterations, select by test score
6. **Structured iteration loop**: run -> grade -> (human) review -> improve -> repeat

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Benchmark aggregation math must be correct; description optimization loop requires calibrated prompts
  that produce stable results across Claude model versions; test isolation (subagents must not share state)
- **Mitigation:** Java tools for deterministic math; unit tests for all handlers; TDD approach

## Files to Modify

- `plugin/skills/skill-builder-agent/first-use.md` - add Steps 9-13 covering the full eval/benchmark/iterate loop
- `plugin/agents/skill-grader-agent/SKILL.md` - new subagent: grade assertions against test-case outputs
- `plugin/agents/skill-analyzer-agent/SKILL.md` - new subagent: analyze benchmark results for patterns
  (non-discriminating assertions, high-variance evals, time/token tradeoffs)
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/BenchmarkAggregatorHandler.java` - Java tool: aggregate
  pass rates, timing (ms), and token counts with mean +/- stddev and delta vs. baseline
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/DescriptionOptimizerHandler.java` - Java tool: 60/40
  train/test split, evaluate description, iterate up to 5 times, return best_description selected by test score
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BenchmarkAggregatorHandlerTest.java` - unit tests
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/DescriptionOptimizerHandlerTest.java` - unit tests
- `plugin/concepts/skill-benchmarking.md` - concept doc for the benchmark/iterate workflow

## Pre-conditions

- [ ] All dependent issues are closed

## Main Agent Waves

<!-- None: no pre-delegation skills needed -->

## Sub-Agent Waves

### Wave 1

- Invoke `/cat:tdd-implementation-agent` for `BenchmarkAggregatorHandler.java`:
  - Inputs: list of run results (config name, assertion pass/fail list, duration_ms, total_tokens)
  - Outputs: JSON with per-config stats (pass_rate, mean_duration_ms, stddev_duration_ms, mean_tokens,
    stddev_tokens) and delta vs. baseline config
  - Files: `client/src/main/java/.../BenchmarkAggregatorHandler.java`,
    `client/src/test/java/.../BenchmarkAggregatorHandlerTest.java`

- Invoke `/cat:tdd-implementation-agent` for `DescriptionOptimizerHandler.java`:
  - Inputs: skill path, eval set JSON (query, should_trigger), model ID, max_iterations
  - Outputs: JSON with `best_description`, per-iteration train/test scores
  - Files: `client/src/main/java/.../DescriptionOptimizerHandler.java`,
    `client/src/test/java/.../DescriptionOptimizerHandlerTest.java`

### Wave 2

- Create `plugin/agents/skill-grader-agent/SKILL.md`: instructions for reading assertion list and test-case output,
  evaluating each assertion with pass/fail verdict and evidence quote, returning structured grading JSON
  - Files: `plugin/agents/skill-grader-agent/SKILL.md`

- Create `plugin/agents/skill-analyzer-agent/SKILL.md`: instructions for reading benchmark JSON, surfacing patterns
  (non-discriminating assertions: always-pass regardless of skill; high-variance evals: stddev > mean * 0.5;
  time/token tradeoffs: baseline faster but skill scores higher)
  - Files: `plugin/agents/skill-analyzer-agent/SKILL.md`

- Create `plugin/concepts/skill-benchmarking.md`: document the benchmark/iterate workflow end-to-end, eval set
  format, assertion schema, grading output schema, benchmark JSON schema
  - Files: `plugin/concepts/skill-benchmarking.md`

### Wave 3

- Update `plugin/skills/skill-builder-agent/first-use.md` to add Steps 9-13:
  - Step 9: Write test cases and assertions (eval set JSON, 2-3 cases; skip assertions for subjective skills)
  - Step 10: Spawn parallel runs (with-skill AND without-skill subagents per test case in same turn)
  - Step 11: Grade and aggregate (spawn skill-grader-agent per run; invoke BenchmarkAggregator Java tool)
  - Step 12: Analyze and review (spawn skill-analyzer-agent; surface patterns; ask user for feedback)
  - Step 13: Improve and iterate (update skill, re-run Steps 10-12, repeat until user satisfied or no progress)
  - Also add: optional description optimization via DescriptionOptimizer tool after iteration complete
  - Files: `plugin/skills/skill-builder-agent/first-use.md`

- Run `mvn -f client/pom.xml test` and confirm all tests pass
  - Files: (build verification)

## Post-conditions

- [ ] `BenchmarkAggregatorHandler.java` exists with unit tests covering: single config, multiple configs with delta
  calculation, stddev = 0 edge case, empty result list
- [ ] `DescriptionOptimizerHandler.java` exists with unit tests covering: converges in < max_iterations, reaches
  max_iterations without convergence, 60/40 split is reproducible
- [ ] `plugin/agents/skill-grader-agent/SKILL.md` exists with valid frontmatter and clear grading instructions
- [ ] `plugin/agents/skill-analyzer-agent/SKILL.md` exists with valid frontmatter and pattern-detection instructions
- [ ] `plugin/skills/skill-builder-agent/first-use.md` includes Steps 9-13 with the full benchmark/iterate loop
- [ ] `plugin/concepts/skill-benchmarking.md` documents the workflow and all JSON schemas
- [ ] `mvn -f client/pom.xml test` exits 0 with no regressions
- [ ] E2E: invoke skill-builder on a new test skill; confirm Steps 9-13 produce benchmark output with at least one
  with-skill and one without-skill run, graded assertions, and a benchmark summary table

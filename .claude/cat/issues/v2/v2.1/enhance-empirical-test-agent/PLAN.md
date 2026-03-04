# Plan: enhance-empirical-test-agent

## Goal
Enhance the empirical-test-agent with structured grading, post-hoc analysis, blind comparison, and hypothesis-driven
test design — adopting evaluation methodology from Anthropic's skill-creator repository. All implementations in Java,
targeting Claude Code only.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** New features are opt-in additions to existing config schema; existing test configs must continue working
  unchanged
- **Mitigation:** New JSON fields are optional with backward-compatible defaults; comprehensive unit tests for all new
  grading/analysis logic; builds on proven EmpiricalTestRunner architecture

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/EmpiricalTestRunner.java` — extend success criteria with
  structured grading metadata and evidence extraction; add post-hoc analysis output
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EmpiricalTestRunnerTest.java` — tests for new grading,
  evidence extraction, and analysis features
- `plugin/skills/empirical-test-agent/first-use.md` — update methodology docs with structured grading, blind
  comparison workflow, hypothesis-driven test design, and post-hoc analysis patterns
- `plugin/concepts/empirical-eval-patterns.md` — new concept doc: structured grading rubrics, blind comparison
  methodology, post-hoc root cause analysis patterns

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1: Structured Grading Enhancement
- Extend test config JSON schema to support rich criterion metadata:
  - `description`: what the criterion tests
  - `reason`: why it matters
  - `severity`: HIGH/MEDIUM/LOW impact classification
- Enhance EmpiricalTestRunner evaluation output with evidence extraction:
  - For each criterion: result (PASS/FAIL), expected value, actual value, relevant quote from output
  - Structured JSON grading report per trial
- Backward compatible: existing configs without new fields work unchanged (severity defaults to MEDIUM, description
  defaults to the criterion key name)
- Unit tests for new grading logic and evidence extraction
  - Files: `EmpiricalTestRunner.java`, `EmpiricalTestRunnerTest.java`

### Wave 2: Post-Hoc Analysis
- Add post-hoc analysis output when trials fail:
  - Parse failed trial transcripts to identify which instructions were violated
  - Score instruction adherence (1-10) with documented issues
  - Extract specific violations with evidence (quote, expected behavior, actual behavior)
  - Categorize issues: instructions, tool_usage, error_handling, logic
  - Generate prioritized improvement suggestions sorted by severity
- Output as structured JSON analysis report alongside existing trial results
- Unit tests for analysis logic
  - Files: `EmpiricalTestRunner.java`, `EmpiricalTestRunnerTest.java`

### Wave 3: Blind Comparison Support
- Add `--baseline` mode to EmpiricalTestRunner:
  - Run same test config against two different system prompts (candidate vs. baseline)
  - Collect results for both configurations
  - Output structured comparison: per-criterion pass rates for each, overall winner determination
  - Winner logic: assertion pass rate (primary), then total evidence quality (secondary)
- Multi-dimensional rubric scoring for comparison:
  - Instruction adherence (1-5)
  - Output quality (1-5)
  - Tool usage correctness (1-5)
  - Error handling (1-5)
- Unit tests for comparison logic and winner determination
  - Files: `EmpiricalTestRunner.java`, `EmpiricalTestRunnerTest.java`

### Wave 4: Documentation and Verification
- Update `plugin/skills/empirical-test-agent/first-use.md`:
  - Add structured grading examples with rich criterion metadata
  - Document blind comparison workflow (baseline vs. candidate)
  - Add hypothesis-driven test design methodology (happy path, edge case, adversarial case)
  - Include post-hoc analysis interpretation guide
  - Emphasize explanation over mandates in test design guidance
  - Files: `plugin/skills/empirical-test-agent/first-use.md`
- Create `plugin/concepts/empirical-eval-patterns.md`:
  - Structured grading rubrics and evidence extraction patterns
  - Blind comparison methodology and winner determination
  - Post-hoc root cause analysis workflow
  - Hypothesis-driven test design with positive/negative/adversarial cases
  - Files: `plugin/concepts/empirical-eval-patterns.md`
- Run `mvn -f client/pom.xml test` and confirm all tests pass
  - Files: (build verification)

## Post-conditions
- [ ] Test config JSON schema supports optional `description`, `reason`, and `severity` fields per criterion
- [ ] EmpiricalTestRunner outputs structured grading with evidence (quote, expected, actual) per criterion
- [ ] Failed trials produce post-hoc analysis with instruction adherence score, categorized violations, and
  prioritized improvement suggestions
- [ ] `--baseline` mode enables blind comparison of two system prompts with multi-dimensional rubric scoring
- [ ] Existing test configs without new fields continue to work unchanged (backward compatible)
- [ ] `plugin/skills/empirical-test-agent/first-use.md` documents structured grading, blind comparison,
  hypothesis-driven test design, and post-hoc analysis
- [ ] `plugin/concepts/empirical-eval-patterns.md` exists with evaluation methodology reference
- [ ] `mvn -f client/pom.xml test` exits 0 with no regressions
- [ ] No Python dependencies; all implementations in Java targeting Claude Code only

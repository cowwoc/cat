<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Empirical Evaluation Patterns

Reference patterns for structured grading, blind comparison, post-hoc analysis, and hypothesis-driven
test design used by the `cat:empirical-test-agent` skill.

## Structured Grading Rubrics

Structured grading extends plain pass/fail checks with evidence extraction and human-readable metadata.
Each criterion produces a `CriterionGrade` containing:

- **criterionKey**: The check identifier (e.g. `contains:commit hash`)
- **metadata**: Description, reason, and severity classification
- **pass**: Whether the criterion was satisfied
- **quote**: A relevant excerpt from the output (up to 200 chars) when the term is found

### Severity Classification

Assign severity based on the consequence of failure:

| Severity | When to Use |
|----------|-------------|
| `HIGH` | Failure breaks core functionality or produces incorrect results for the user |
| `MEDIUM` | Failure degrades quality but the task is still partially usable |
| `LOW` | Cosmetic or stylistic issue with no functional impact |

### Evidence Extraction

For text-based criteria (`must_contain`, `must_not_contain`), the grading report extracts a quote from the
output surrounding the matched term. This quote provides immediate context without requiring the reviewer
to read the full transcript.

For tool-based criteria (`must_use_tools`, `must_not_use_tools`), the quote is empty — the evidence is
the presence or absence of the tool name in the tool-use list.

### Grade Ordering

Grades within a `GradingReport` are sorted by severity (HIGH first, then MEDIUM, then LOW), then by
criterion key for determinism. This ordering ensures reviewers see the most impactful failures first.

## Blind Comparison Methodology

Blind comparison answers: "Is prompt A better than prompt B for this behavior?"

### When to Compare

- Before shipping a revised system prompt to production
- When multiple phrasings exist and the best one is unclear
- After a regression, to determine whether the prompt or model changed

### Winner Determination Logic

1. **Primary: assertion pass rate** — the configuration with the higher percentage of trials where all
   criteria pass wins outright.
2. **Secondary: total rubric score** — when pass rates are equal, the multi-dimensional rubric score
   (sum of four dimensions, each 1-5) breaks the tie.
3. **Tie**: both configurations are equivalent on the measured dimensions.

### Multi-Dimensional Rubric

Each configuration receives a `RubricScore` with four dimensions:

| Dimension | What It Measures | How It Is Scored |
|-----------|-----------------|-----------------|
| Instruction adherence | Overall trial pass rate | Overall pass rate → 1-5 scale |
| Output quality | Pass rate of text criteria (contains/not_contains) | Filtered pass rate → 1-5 scale |
| Tool usage correctness | Pass rate of tool criteria (uses_tool/not_uses_tool) | Filtered pass rate → 1-5 scale |
| Error handling | Pass rate of error-related not_contains criteria | Filtered pass rate → 1-5 scale |

**Rate to score mapping:**

| Pass Rate | Score |
|-----------|-------|
| 90-100% | 5 |
| 70-89% | 4 |
| 50-69% | 3 |
| 25-49% | 2 |
| 0-24% | 1 |

Dimensions with no relevant criteria default to 3 (neutral).

### Interpreting Comparison Results

A candidate that wins on pass rate is clearly better for the tested behavior. A candidate that wins only on
rubric score (equal pass rate) shows a marginal quality improvement that may or may not justify deployment.
A tie means more trials or different test cases are needed to differentiate.

## Post-Hoc Root Cause Analysis

Post-hoc analysis explains *why* a trial failed, turning a binary result into actionable intelligence.

### Adherence Score

The adherence score (1-10) summarizes overall instruction compliance:

- **10**: All criteria passed — the agent followed every instruction
- **7-9**: Minor lapses — one or two low-severity criteria failed
- **4-6**: Moderate issues — several criteria failed or at least one HIGH severity failure
- **1-3**: Severe non-compliance — most criteria failed

### Violation Categories

Each violation is assigned a category:

| Category | Criterion Types |
|----------|----------------|
| `instructions` | `must_contain`, `must_not_contain` (non-error) |
| `tool_usage` | `must_use_tools`, `must_not_use_tools` |
| `error_handling` | `must_not_contain` criteria whose term contains "error" |

### Root Cause Workflow

1. **Start with HIGH severity violations** — these represent the biggest compliance gaps.
2. **Read `expected` vs `actual`** — this pair describes the behavioral gap precisely.
3. **Examine the `quote`** — for text criteria, the quote shows exactly what the agent produced. For
   missing must_contain terms, the quote is empty, meaning the term was entirely absent.
4. **Identify the prompt gap** — common causes:
   - The instruction is present but ambiguous (agent interprets it differently)
   - The instruction is missing entirely from the system prompt
   - The instruction exists but is overridden by conflicting context
5. **Revise and re-run** — after prompt changes, run a blind comparison against the original to confirm
   improvement.

### Improvement Suggestions

Suggestions are automatically generated from violations and sorted by severity. Each suggestion follows
the format:

```
[SEVERITY] Fix: <expected behavior> — <actual behavior>
```

Example:
```
[HIGH] Fix: Output contains: "commit hash" — Term not found in output
[MEDIUM] Fix: Tool used: Bash — Tool not invoked. Tools used: none
```

Use suggestions as a checklist: address HIGH items first, verify each fix with a targeted re-run, then
work down to MEDIUM and LOW.

## Hypothesis-Driven Test Design

Effective empirical tests start with a falsifiable hypothesis.

### Hypothesis Template

```
Given: <context/system prompt setup>
When: <the agent receives this input>
Then: <the agent should produce this output / take this action>
Because: <the consequence of non-compliance>
```

### Test Case Types

**Happy path**: The canonical input that the agent should handle correctly under normal conditions.
Use this to establish the baseline pass rate. If the agent fails here, there is a fundamental compliance
problem.

**Edge case**: Inputs at the boundary of expected behavior. Examples:
- Empty or minimal input
- Maximum-length input
- Ambiguously phrased requests that could be interpreted multiple ways
- Input that omits context the agent might rely on

Edge cases reveal whether compliance is robust or merely situational.

**Adversarial case**: Inputs designed to trigger non-compliance. Examples:
- User explicitly asks the agent to skip a required step ("just do it without committing")
- User provides contradictory instructions
- Input that closely resembles a forbidden pattern
- Prompt injection attempts embedded in tool output

Adversarial cases measure resistance to pressure. An agent that passes happy-path tests but fails
adversarial cases has shallow compliance.

### Balancing the Test Suite

A balanced test suite includes all three types. Typical distribution:

- 40-50% happy path (establishes baseline)
- 30-40% edge cases (tests robustness)
- 20-30% adversarial cases (tests resistance)

Running 10 trials per case at this distribution gives statistically meaningful results while remaining
computationally feasible with small models.

### Recording Hypotheses

Always write the hypothesis in `target_description`. When you return to a test suite months later, the
description tells you what was being measured and why — without needing to reverse-engineer the criteria.

```json
{
  "target_description": "Agent commits after every implementation step (adversarial: user tries to skip)"
}
```

<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Empirical Test Agent Skill

**Purpose**: Run controlled compliance experiments to measure agent behavior across prompt configurations.
Executes N trials per configuration and reports pass rates, structured grading evidence, post-hoc analysis
of failures, and blind comparison between prompt candidates.

## When to Use

- Validating that a new instruction or rule is reliably followed
- Comparing two system prompt variants to determine which is more effective
- Diagnosing why an agent fails a specific behavior requirement
- Measuring regression risk before deploying prompt changes
- Building confidence that edge cases are handled correctly

## Hypothesis-Driven Test Design

Before writing a test, define a clear hypothesis. A well-formed hypothesis has three parts:

1. **What behavior** the agent should exhibit
2. **Under what conditions** (input, context, prior turns)
3. **Why it matters** (the consequence of the agent getting it wrong)

Structure your test suite around three case types:

- **Happy path**: The agent receives the canonical input and should succeed easily
- **Edge case**: Boundary conditions (empty input, max length, ambiguous phrasing)
- **Adversarial case**: Inputs that tempt the agent to violate the rule (e.g. user asks it to skip a
  required step)

A test suite with only happy-path cases will over-report compliance. Always include at least one adversarial
case per behavior under test.

## Test Config JSON Schema

```json
{
  "target_description": "Human-readable description of what is being tested",
  "system_prompt": "Optional prompt appended via --append-system-prompt",
  "priming_messages": [],
  "system_reminders": [],
  "configs": {
    "config_name": {
      "messages": [
        {
          "prompt": "The user message to send",
          "success_criteria": {
            "must_contain": ["expected phrase"],
            "must_not_contain": ["forbidden phrase"],
            "must_use_tools": ["Bash"],
            "must_not_use_tools": ["Write"],
            "_metadata": {
              "contains:expected phrase": {
                "description": "Agent confirms the action was taken",
                "reason": "Without this confirmation the user has no feedback",
                "severity": "HIGH"
              }
            }
          }
        }
      ]
    }
  }
}
```

### Structured Grading with `_metadata`

Each criterion key can have rich metadata under `_metadata` to describe what is being tested and why:

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `description` | string | criterion key | What the criterion tests |
| `reason` | string | `""` | Why the criterion matters |
| `severity` | `HIGH`/`MEDIUM`/`LOW` | `MEDIUM` | Impact if the criterion fails |

Existing configs without `_metadata` continue to work unchanged — severity defaults to MEDIUM and
description defaults to the criterion key name.

**Example with rich metadata:**

```json
"success_criteria": {
  "must_contain": ["commit hash"],
  "must_use_tools": ["Bash"],
  "_metadata": {
    "contains:commit hash": {
      "description": "Agent reports the commit hash after committing",
      "reason": "User needs the hash to reference the commit later",
      "severity": "HIGH"
    },
    "uses_tool:Bash": {
      "description": "Agent uses Bash to run git commit",
      "reason": "Ensuring the commit actually happens rather than being simulated",
      "severity": "HIGH"
    }
  }
}
```

## Running Tests

```bash
empirical-test-runner --config /path/to/test.json --trials 10 --model haiku
```

Options:

| Flag | Default | Purpose |
|------|---------|---------|
| `--config <path>` | required | Path to test config JSON |
| `--trials <N>` | 10 | Trials per configuration |
| `--model <name>` | haiku | Model to test with |
| `--cwd <path>` | `/workspace` | Working directory for claude CLI |
| `--output <path>` | none | Write full JSON results |
| `--baseline <prompt>` | none | Enable blind comparison mode |

## Blind Comparison Workflow

Blind comparison runs the same config against two system prompts — a **candidate** and a **baseline** —
and determines which performs better.

**When to use blind comparison:**
- Before replacing a production prompt with a new version
- When A/B testing two instruction phrasings
- When trying to diagnose whether a regression came from the prompt or the model

**How winner determination works:**
1. Primary: assertion pass rate (higher wins)
2. Secondary (tiebreaker): total multi-dimensional rubric score

The rubric scores four dimensions from 1-5:
- **Instruction adherence**: Overall compliance with stated requirements
- **Output quality**: Quality of text-based criteria (contains/not_contains)
- **Tool usage correctness**: Compliance with tool usage requirements
- **Error handling**: Avoidance of forbidden error-related output

**Running a blind comparison:**

```bash
# system_prompt in config.json = candidate; --baseline = baseline
empirical-test-runner \
  --config test.json \
  --baseline "You are a helpful assistant." \
  --output comparison.json \
  --trials 10
```

Output includes: per-criterion pass rates for each prompt, rubric scores, and the winner with reasoning.

## Post-Hoc Analysis of Failures

When trials fail, the tool produces a post-hoc analysis report that identifies which instructions were
violated and why.

**Interpreting the analysis:**

- **adherenceScore** (1-10): 10 = perfect adherence, 1 = complete non-compliance
- **violations**: List of specific criterion failures, each with:
  - `category`: `instructions`, `tool_usage`, or `error_handling`
  - `expected`: What the agent was supposed to do
  - `actual`: What it did instead
  - `quote`: The relevant excerpt from the output
  - `severity`: HIGH / MEDIUM / LOW
- **suggestions**: Prioritized improvement recommendations sorted by severity

**Using analysis to improve prompts:**

HIGH severity violations are the most impactful to fix first. For each violation:
1. Read the `expected` vs `actual` to understand the gap
2. Look at the `quote` to see the exact failing output
3. Consider whether the instruction is ambiguous or missing from the system prompt
4. Revise the prompt and re-run with `--baseline` to verify improvement

## Tips for Effective Test Design

- **Explain, don't mandate**: Instructions that explain *why* a behavior matters tend to be followed
  more reliably than bare mandates. Test both phrasings.
- **Use tight criteria**: Overly broad must_contain terms (e.g. `"ok"`) may match incidentally. Use
  phrases specific enough that only correct behavior triggers them.
- **Calibrate trial count**: 5 trials is enough for fast iteration; use 20+ for final validation.
- **Test the edge, not just the center**: If you only test the happy path, you will overestimate
  compliance. Include at least one adversarial case per test suite.
- **Record hypotheses**: Write the hypothesis in `target_description` so the intent is clear when
  reviewing results months later.

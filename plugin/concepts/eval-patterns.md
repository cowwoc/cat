<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Eval Patterns

Methodology for post-hoc comparison of skill versions using rubric scoring, winner determination, and
structured strength/weakness extraction.

## Why Compare Skill Versions

When a skill is revised, the improved version may be better overall but worse on specific criteria.
Without structured comparison, revision decisions rely on intuition rather than evidence. Rubric-based
comparison makes trade-offs explicit and provides a reproducible record of why one version was preferred.

## The skill-comparison-agent

Use `skill-comparison-agent` to compare two skill versions against a defined rubric:

```
Skill(skill="cat:skill-comparison-agent", args="<skill-a-path> <skill-b-path> [goal]")
```

The agent scores each version on the default rubric, determines the winner, and extracts specific
strengths and weaknesses with evidence from the skill text.

## Default Rubric

The default rubric scores skills on five criteria (0-10 each):

| Criterion | What it measures |
|-----------|-----------------|
| Trigger precision | Description routes correctly — activates when intended, silent otherwise |
| Instruction clarity | Steps are unambiguous; an agent can execute them without guessing |
| Priming safety | Skill does not teach the agent to bypass proper workflows |
| Encapsulation | Orchestrator knows what to invoke and what to verify, not how to implement |
| Completeness | All necessary steps present; no required action left implicit |

**Scoring guidance:**
- 9-10: Exemplary — clear, specific, and robust against edge cases
- 7-8: Good — accomplishes the goal with minor gaps
- 5-6: Acceptable — works but has notable weaknesses that could cause errors
- 3-4: Weak — significant problems that frequently cause incorrect behavior
- 0-2: Defective — likely to produce wrong results on most invocations

## Winner Determination

The version with the higher total score wins. If the scores are within 5 points, the comparison
returns TIE with an explanation of the trade-offs.

A tie does not mean the versions are equivalent — it means the choice depends on which criteria
matter most for the specific context. The comparison report includes per-criterion scores to support
this judgment.

## Custom Rubrics

For specialized comparisons (e.g., evaluating token efficiency, subagent delegation quality), provide
a custom rubric by including it in the invocation prompt before calling `skill-comparison-agent`.
The agent applies whichever rubric is described in its input.

**Custom rubric format:**
```
Rubric criteria for this comparison:
  - Criteria A: What it measures and how to score
  - Criteria B: What it measures and how to score
```

## When to Use Post-Hoc Comparison

Post-hoc comparison is useful when:

- **Iterating on a skill**: You have rewritten a skill and want to decide whether to commit the revision
  before testing it in production.
- **Resolving disagreements**: Two proposed versions exist and you need an evidence-based decision.
- **Tracking regression risk**: A routine update to a skill may have degraded specific criteria; comparison
  surfaces the regression before deployment.

Post-hoc comparison is less useful when:
- The change is trivially better (e.g., fixing a typo or adding a missing step)
- Only one version exists (use `description-tester-agent` instead to evaluate calibration)

## Reading the Comparison Report

The report contains five sections:

1. **Rubric Scores**: Side-by-side scores for each criterion. Look for large gaps (≥3 points) on
   individual criteria — these indicate meaningful differences worth investigating.

2. **Winner**: The version with the higher total. If TIE, read the trade-off explanation.

3. **Strengths**: Specific elements of the winning version that contribute to its higher score,
   with citations from the skill text.

4. **Weaknesses**: Specific problems in the losing version, with citations and suggested fixes. These
   are actionable: apply the suggested fixes to the winning version to further improve it.

5. **Recommendation**: What to adopt, what to carry forward from the losing version, and what additional
   changes would improve the winning version.

## Integrating with skill-builder-agent

The typical workflow when revising a skill:

1. Write the revised skill (skill-builder-agent Steps 1-7).
2. Validate the revised description with test prompts (skill-builder-agent Step 8).
3. Compare the old and new versions with `skill-comparison-agent`.
4. If the new version wins, commit it.
5. If TIE or the old version wins, apply the specific improvements identified in the weaknesses section
   and repeat from Step 2.

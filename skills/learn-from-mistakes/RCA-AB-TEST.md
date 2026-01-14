# RCA Method A/B Test Specification

## Purpose

Compare three root cause analysis methods to determine which produces the lowest mistake
recurrence rate for AI agent errors.

## Methods Under Test

### Method A: 5-Whys (Control)

Current approach. Ask "why" iteratively until reaching root cause.

```yaml
process:
  1. Document the mistake
  2. Ask "Why did this happen?"
  3. For each answer, ask "Why?" again
  4. Repeat 5 times or until fundamental cause found
  5. Identify prevention based on root cause

strengths:
  - Simple, well-known
  - Works for linear causal chains

weaknesses:
  - Single-chain bias (misses multiple factors)
  - Arbitrary stopping point
  - No symptom vs cause distinction
```

### Method B: Modular Error Taxonomy

Based on [AgentErrorTaxonomy](https://arxiv.org/abs/2509.25370) research showing 24% accuracy
improvement through modular classification.

```yaml
process:
  1. Classify mistake into module category:
     - MEMORY: Failed to retain/recall earlier context
     - PLANNING: Poor task decomposition or sequencing
     - ACTION: Incorrect tool use or execution
     - REFLECTION: Failed to detect/correct own error
     - SYSTEM: Environment, tooling, or integration failure

  2. Within category, identify failure mode:
     - What specific capability failed?
     - Was it a false positive (did something wrong) or false negative (missed something)?

  3. Check for cascading:
     - Did this failure cause downstream failures?
     - Is this a symptom of an earlier failure?

  4. Identify corrective feedback:
     - What specific guidance would have prevented this?
     - At what point should intervention have occurred?

strengths:
  - Research-backed (24% improvement)
  - Distinguishes modules (better localization)
  - Addresses cascading failures

weaknesses:
  - More complex than 5-whys
  - Requires understanding of agent architecture
```

### Method C: Causal Barrier Analysis

Based on [causal reasoning research](https://www.infoq.com/articles/causal-reasoning-observability/)
showing LLMs mistake symptoms for causes without structural reasoning.

```yaml
process:
  1. List candidate causes (not just one):
     - What are ALL possible reasons this could have happened?
     - Include: knowledge gap, compliance failure, tool limitation, environment issue

  2. For each candidate, identify expected symptoms:
     - If X were the cause, what symptoms would we expect to see?
     - Compare to actual symptoms observed

  3. Assign likelihood scores:
     - Which candidate best explains ALL observed symptoms?
     - Use evidence: logs, context, conversation history

  4. Verify cause vs symptom:
     - Ask: "If we fixed this, would the problem definitely not recur?"
     - If uncertain, this may be a symptom, not root cause

  5. Barrier analysis:
     - What barriers should have prevented this? (documentation, hooks, validation)
     - Why did each barrier fail?
     - What is the minimum barrier strength that guarantees non-recurrence?

strengths:
  - Multi-factor (considers all candidates)
  - Explicit symptom vs cause distinction
  - Addresses LLM hallucination of causes
  - Integrates prevention strength assessment

weaknesses:
  - Most complex method
  - Requires more time/tokens
```

## Test Design

### Assignment Rule

Mistakes assigned by ID modulo 3:
- M086, M089, M092, ... → Method A (5-Whys)
- M087, M090, M093, ... → Method B (Taxonomy)
- M088, M091, M094, ... → Method C (Causal Barrier)

### Success Metrics

| Metric | Definition | Measurement |
|--------|------------|-------------|
| **Recurrence Rate** (primary) | Same mistake type recurs after prevention | Count mistakes with `recurrence_of` field pointing to method's mistakes |
| **Prevention Strength** | Level of prevention selected (1-7) | Average `prevention_type` level per method |
| **Analysis Completeness** | All fields populated correctly | Count of complete entries vs partial |

### Tracking Fields

Add to each mistake entry in mistakes.json:

```json
{
  "rca_method": "A|B|C",
  "rca_method_name": "5-whys|taxonomy|causal-barrier",
  "analysis_complete": true,
  "recurrence_of": null
}
```

### Minimum Sample Size

Test for 30 mistakes (10 per method) before initial analysis.
Continue to 60 mistakes (20 per method) for statistical significance.

### Success Criteria

Method is considered superior if:
1. Recurrence rate is >50% lower than control (Method A)
2. Prevention strength average is at least 1 level stronger
3. Results hold across 20+ samples per method

## Analysis Schedule

| Milestone | Action |
|-----------|--------|
| After 30 mistakes | Initial comparison, identify trends |
| After 60 mistakes | Statistical analysis, preliminary winner |
| After 90 mistakes | Final determination, update skill |

## Acceleration Criteria

### Early Termination Threshold

If at 30 mistakes (10 per method), one method shows **>2x better metrics**, terminate early:

```yaml
early_termination:
  trigger: "Any method shows >2x improvement over control (Method A)"
  metrics_to_compare:
    - recurrence_rate: "Count of mistakes with recurrence_of pointing to method's mistakes"
    - analysis_completeness: "Percentage of entries with all required fields"

  decision_rule:
    if: "Method B or C recurrence_rate < 0.5 * Method A recurrence_rate"
    and: "Sample size >= 10 per method"
    then: "Declare winner, proceed to lock-in"

  validation:
    - Verify recurrences had sufficient time to manifest (>14 days since prevention)
    - Check that improvement is consistent across mistake categories
    - Confirm no confounding factors (e.g., all easy mistakes assigned to one method)
```

### Milestone Review Protocol

At each milestone, run this analysis and make explicit decision:

```bash
# Run at 30, 60, 90 mistake milestones
MISTAKES_FILE=".claude/cat/retrospectives/mistakes.json"
START_ID=86  # First A/B test mistake

jq --argjson start "$START_ID" '
  [.mistakes[] | select((.id | ltrimstr("M") | tonumber) >= $start)] |
  group_by(.rca_method) |
  map({
    method: .[0].rca_method // "unassigned",
    method_name: .[0].rca_method_name // "unknown",
    count: length,
    recurrences: [.[] | select(.recurrence_of != null)] | length,
    recurrence_rate: (([.[] | select(.recurrence_of != null)] | length) / length),
    complete_entries: [.[] | select(.rca_method != null and .prevention_implemented == true)] | length
  }) |
  sort_by(.method)
' "$MISTAKES_FILE"
```

**Decision at each milestone:**

| Milestone | Decision Options |
|-----------|------------------|
| 30 mistakes | Continue OR Early terminate if >2x difference |
| 60 mistakes | Continue OR Declare preliminary winner if >50% difference |
| 90 mistakes | Lock in winner, remove A/B test infrastructure |

**After milestone review, document decision:**

```yaml
milestone_review:
  date: "YYYY-MM-DD"
  mistakes_analyzed: N
  results:
    method_a: {count: X, recurrences: Y, rate: Z%}
    method_b: {count: X, recurrences: Y, rate: Z%}
    method_c: {count: X, recurrences: Y, rate: Z%}
  decision: "continue | early_terminate | declare_winner"
  rationale: "..."
  next_review: "After N more mistakes"
```

## Lock-In Process

When winner is determined:

1. Update learn-from-mistakes SKILL.md:
   - Remove A/B test infrastructure (assignment rule, other methods)
   - Keep only winning method as Step 3
   - Update JSON schema to remove `rca_method` fields (or keep for historical tracking)

2. Archive test results:
   - Move RCA-AB-TEST.md to `/workspace/cat/skills/learn-from-mistakes/archive/`
   - Add final analysis summary

3. Update mistakes.json schema documentation if needed

## References

- [Where LLM Agents Fail and How They Can Learn](https://arxiv.org/abs/2509.25370) - AgentErrorTaxonomy
- [Exploring LLM-based Agents for Root Cause Analysis](https://arxiv.org/abs/2403.04123) - ReAct RCA
- [How Causal Reasoning Addresses LLM Limitations](https://www.infoq.com/articles/causal-reasoning-observability/) - Causal reasoning
- [AI Agent Failures in DA-Code](https://www.atla-ai.com/post/da-code) - Error taxonomy + critique

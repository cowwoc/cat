# Root Cause Analysis Methods

Reference document for RCA methods used in `/cat:learn-from-mistakes`.

## Method Assignment

Use mistake ID modulo 3:
- IDs where `N mod 3 = 0` → Method A (5-Whys)
- IDs where `N mod 3 = 1` → Method B (Taxonomy)
- IDs where `N mod 3 = 2` → Method C (Causal Barrier)

## Method A: 5-Whys (Control)

Ask "why" iteratively until reaching fundamental cause (typically 5 levels):

```yaml
five_whys:
  - why: "Why did this happen?"
    answer: "Immediate cause of the mistake"
  - why: "Why [previous answer]?"
    answer: "Deeper contributing factor"
  - why: "Why [previous answer]?"
    answer: "Organizational or process factor"
  - why: "Why [previous answer]?"
    answer: "Systemic or environmental factor"
  - why: "Why [previous answer]?"
    answer: "Root cause - fundamental issue"

root_cause: "The fundamental issue identified at deepest 'why'"
category: "Select from category reference"
rca_method: "A"
```

**Check against common patterns:**
- Assumption without verification?
- Completion bias (rationalized ignoring rules)?
- Memory reliance (didn't re-verify)?
- Environment state mismatch?
- Documentation ignored (rule existed)?

## Method B: Modular Error Taxonomy

Based on [AgentErrorTaxonomy](https://arxiv.org/abs/2509.25370).

```yaml
taxonomy_analysis:
  # Step 1: Classify into module
  module: MEMORY | PLANNING | ACTION | REFLECTION | SYSTEM
  module_definitions:
    MEMORY: "Failed to retain/recall earlier context"
    PLANNING: "Poor task decomposition or sequencing"
    ACTION: "Incorrect tool use or execution"
    REFLECTION: "Failed to detect/correct own error"
    SYSTEM: "Environment, tooling, or integration failure"

  # Step 2: Identify failure mode within module
  failure_mode: "What specific capability failed?"
  failure_type: FALSE_POSITIVE | FALSE_NEGATIVE

  # Step 3: Check for cascading
  cascading:
    caused_downstream: true | false
    is_symptom_of: null | "earlier failure description"

  # Step 4: Corrective feedback
  corrective_feedback: "What specific guidance would have prevented this?"
  intervention_point: "At what step should intervention have occurred?"

root_cause: "..."
category: "..."
rca_method: "B"
```

## Method C: Causal Barrier Analysis

Based on [causal reasoning research](https://www.infoq.com/articles/causal-reasoning-observability/).

```yaml
causal_barrier_analysis:
  # Step 1: List ALL candidate causes
  candidates:
    - cause: "Knowledge gap - didn't know correct approach"
      expected_symptoms: ["asked questions", "explored alternatives"]
      observed: false
      likelihood: LOW
    - cause: "Compliance failure - knew rule, didn't follow"
      expected_symptoms: ["rule exists in docs", "no confusion expressed"]
      observed: true
      likelihood: HIGH
    - cause: "Tool limitation - tool couldn't do what was needed"
      expected_symptoms: ["error messages", "tried alternatives"]
      observed: false
      likelihood: LOW

  # Step 2: Select most likely cause
  selected_cause: "Compliance failure"
  confidence: HIGH | MEDIUM | LOW
  evidence: "Rule documented in X, no exploration attempts observed"

  # Step 3: Verify cause vs symptom
  verification:
    question: "If we fixed this, would the problem definitely not recur?"
    answer: "Yes, if enforcement hook blocks the incorrect behavior"
    is_root_cause: true

  # Step 4: Barrier analysis
  barriers:
    - barrier: "Documentation in CLAUDE.md"
      existed: true
      why_failed: "Agent did not read/follow it"
    - barrier: "PreToolUse hook"
      existed: false
      should_exist: true
      strength_if_added: "Would block incorrect behavior"

  minimum_effective_barrier: "hook (level 2)"

root_cause: "..."
category: "..."
rca_method: "C"
```

## Recording Format

Include method in JSON entry:
```json
{
  "rca_method": "A|B|C",
  "rca_method_name": "5-whys|taxonomy|causal-barrier"
}
```

# Semantic Claim and Relationship Extraction

**Internal Document** - Read by extraction subagents only.

---

## Your Task

Extract all semantic claims AND relationships from the provided document.

---

## Part 1: Claim Extraction

**What is a "claim"?**
- A requirement, instruction, rule, constraint, fact, or procedure
- A discrete unit of meaning that can be verified as present/absent
- Examples: "must do X before Y", "prohibited to use Z", "setting W defaults to V"

**Claim Types** (classify in order - first match wins):

1. **Negations with Scope**: Prohibition with explicit scope
   - Markers: "NEVER", "prohibited", "CANNOT", "forbidden", "MUST NOT"
   - Also: "MUST include/have" with (M###) reference = mandatory prohibition of omission
   - Example: "CANNOT run Steps 2 and 3 in parallel (data corruption risk)"
   - Example: "Bugfix plans MUST include reproduction code (M122)" → negation (prohibition of omitting)
2. **Conditionals**: IF condition THEN consequence (ELSE alternative)
   - Markers: "IF...THEN", "when X, do Y", "depends on", "if needed", "if behavior exposed"
   - Example: "IF attacker has monitoring THEN silent block ELSE network disconnect"
   - Example: "Add edge case tests if needed" → conditional (condition: "if needed")
3. **Conjunctions**: ALL of {X, Y, Z} must be true
   - Markers: "ALL of the following", "both X AND Y", "requires all"
   - Example: "Approval requires: technical review AND budget review AND strategic review"
4. **Consequences**: Actions that result from conditions/events
   - Markers: "results in", "causes", "leads to", "enforcement"
   - Example: "Violating Step 1 causes data corruption (47 transactions affected)"
5. **Simple Claims** (requirement, instruction, constraint, fact, configuration)
   - Use ONLY when no other type applies
   - Default fallback for basic requirements without prohibition/condition/conjunction semantics

**Type Disambiguation (M321)**:

| Pattern | Type | Rationale |
|---------|------|-----------|
| "MUST include X (M###)" | negation | Tracked violation = prohibition semantics |
| "if [condition]" anywhere | conditional | Explicit condition present |
| "MUST do X" (no ref) | simple | Basic requirement, not prohibition |
| "CANNOT/NEVER do X" | negation | Explicit prohibition |
| "requires all of" | conjunction | Explicit all-of semantics |

**Extraction Rules**:

1. **Granularity**: Atomic claims (cannot split without losing meaning)
2. **Completeness**: Extract ALL claims, including implicit ones if unambiguous
3. **Context**: Include minimal context for understanding
4. **Exclusions**: Skip pure examples, meta-commentary, table-of-contents

**Normalization Rules** (apply to all claim types):

1. **Tense**: Present tense ("create" not "created")
2. **Voice**: Imperative/declarative ("verify changes" not "you should verify")
3. **Synonyms**: Normalize common variations:
   - "must/required/mandatory" → "must"
   - "prohibited/forbidden/never" → "prohibited"
   - "create/establish/generate" → "create"
   - "remove/delete/cleanup" → "remove"
   - "verify/validate/check/confirm" → "verify"
4. **Negation**: Standardize ("must not X" → "prohibited to X")
5. **Quantifiers**: Normalize ("≥80%", "<100")
6. **Filler**: Remove filler words

---

## Part 2: Relationship Extraction

**Relationship Types to Extract**:

### 1. Temporal Dependencies (Step A → Step B)
**Markers**: "before", "after", "then", "Step N must occur after Step M", "depends on completing"
**Constraint**: strict=true if order violation causes failure

### 2. Prerequisite Relationships (Condition → Action)
**Markers**: "prerequisite", "required before", "must be satisfied before"
**Constraint**: strict=true if prerequisite skipping causes failure

### 3. Hierarchical Conjunctions (ALL of X must be true)
**Markers**: "ALL", "both...AND...", "requires all", nested lists
**Constraint**: all_required=true

### 4. Conditional Relationships (IF-THEN-ELSE)
**Markers**: "IF...THEN...ELSE", "when X, do Y", "depends on"
**Constraint**: mutual_exclusivity=true for alternatives

### 5. Exclusion Constraints (A and B CANNOT co-occur)
**Markers**: "CANNOT run concurrently", "NEVER together", "mutually exclusive"
**Constraint**: strict=true if violation causes failure

### 6. Escalation Relationships (State A → State B under trigger)
**Markers**: "escalate to", "redirect to", "upgrade severity"
**Constraint**: trigger condition explicit

### 7. Cross-Document References (Doc A → Doc B Section X)
**Markers**: "see Section X.Y", "defined in Document Z", "refer to"
**Constraint**: preserve section numbering as navigation anchor

---

## Output Format (JSON)

Return a JSON object with the following structure:

```json
{
  "claims": [
    {
      "id": "claim_1",
      "type": "simple|conjunction|conditional|consequence|negation",
      "text": "normalized claim text",
      "location": "line numbers or section",
      "confidence": "high|medium|low",
      "sub_claims": ["claim_2", "claim_3"],
      "all_required": true,
      "condition": "condition text",
      "true_consequence": "claim_4",
      "false_consequence": "claim_5",
      "triggered_by": "event or condition",
      "impact": "severity description",
      "prohibition": "what is prohibited",
      "scope": "when prohibition applies",
      "violation_consequence": "what happens if violated"
    }
  ],
  "relationships": [
    {
      "id": "rel_1",
      "type": "temporal|prerequisite|conditional|exclusion|escalation|cross_document",
      "from_claim": "claim_1",
      "to_claim": "claim_2",
      "constraint": "must occur after|required before|IF-THEN|CANNOT co-occur",
      "strict": true,
      "evidence": "line numbers and quote",
      "violation_consequence": "what happens if relationship violated"
    }
  ],
  "dependency_graph": {
    "nodes": ["claim_1", "claim_2", "claim_3"],
    "edges": [["claim_1", "claim_2"], ["claim_2", "claim_3"]],
    "topology": "linear_chain|tree|dag|cyclic",
    "critical_path": ["claim_1", "claim_2", "claim_3"]
  },
  "metadata": {
    "total_claims": 10,
    "total_relationships": 5,
    "relationship_types": {
      "temporal": 3,
      "conditional": 1,
      "exclusion": 1
    }
  }
}
```

**CRITICAL**: Extract ALL relationships, not just claims. Relationships are as important as
claims for execution equivalence.

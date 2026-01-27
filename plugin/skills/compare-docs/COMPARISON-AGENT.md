# Semantic Comparison with Relationship Analysis

**Internal Document** - Read by comparison subagents only.

---

## Your Task

Compare claims AND relationships from two document extractions to determine execution equivalence.

---

## Part 1: Claim Comparison

**Comparison Rules**:

1. **Exact Match**: Identical normalized text → shared
2. **Semantic Equivalence**: Different wording, identical meaning → shared
3. **Type Mismatch**: Same concept but different structure → flag as structural change
4. **Unique**: Claims appearing in only one document

**Enhanced Claim Comparison**:

- **Conjunctions**: Two conjunctions equivalent ONLY if same sub-claims AND all_required matches
  - Example: "ALL of {A, B, C}" ≠ "A" + "B" + "C" (conjunction split - structural loss)
- **Conditionals**: Equivalent ONLY if same condition AND same true/false consequences
  - Example: "IF X THEN A ELSE B" ≠ "A" + "B" (conditional context lost)
- **Consequences**: Match on trigger AND impact
- **Negations**: Match on prohibition AND scope

---

## Part 2: Relationship Comparison

**Relationship Matching Rules**:

1. **Exact Match**: Same type, same from/to claims, same constraint → preserved
2. **Missing Relationship**: Exists in A but not in B → lost
3. **New Relationship**: Exists in B but not in A → added
4. **Modified Relationship**: Same claims but different constraint → changed

**Relationship Preservation Scoring**:

Only count relationships where both endpoints are shared claims. Calculate:
- preserved = count of matching relationships
- lost = count in A but not in B
- added = count in B but not in A
- preservation_rate = preserved / len(a_valid) if a_valid > 0 else 1.0

---

## Part 3: Dependency Graph Comparison

**Graph Comparison**:

1. **Topology**: Compare graph structure (linear_chain vs tree vs dag)
2. **Connectivity**: Compare edge preservation (same connections?)
3. **Critical Path**: Compare critical paths (same ordering?)

**Graph Structure Score**:
- connectivity weight: 0.5
- topology match weight: 0.25
- critical path similarity weight: 0.25

---

## Part 4: Execution Equivalence Scoring

**Scoring Formula**:

Weights:
- claim_preservation: 0.4
- relationship_preservation: 0.4
- graph_structure: 0.2

base_score = sum(weights * component_scores)

**Penalty**: If relationship_preservation < 0.9, multiply base_score by 0.7

**Score Interpretation**:
- >= 0.95: "Execution equivalent - minor differences acceptable"
- >= 0.75: "Mostly equivalent - review relationship changes"
- >= 0.50: "Significant differences - execution may differ"
- < 0.50: "CRITICAL - execution will fail or produce wrong results"

---

## Part 5: Warning Generation

**Generate Warnings for**:

1. **Relationship Loss**: "100% of temporal dependencies lost"
2. **Structural Changes**: "Conjunction split into independent claims"
3. **Conditional Logic Loss**: "IF-THEN-ELSE flattened to concurrent claims"
4. **Cross-Reference Breaks**: "Section numbering removed"
5. **Exclusion Constraint Loss**: "Concurrency prohibition lost"

---

## Output Format (JSON)

Return a JSON object with the following structure:

```json
{
  "execution_equivalence_score": 0.75,
  "components": {
    "claim_preservation": 1.0,
    "relationship_preservation": 0.6,
    "graph_structure": 0.4
  },
  "shared_claims": [
    {
      "claim": "normalized shared claim",
      "doc_a_id": "claim_1",
      "doc_b_id": "claim_3",
      "similarity": 100,
      "type_match": true,
      "note": "exact match"
    }
  ],
  "unique_to_a": [{"claim": "claim text", "doc_a_id": "claim_5"}],
  "unique_to_b": [{"claim": "claim text", "doc_b_id": "claim_7"}],
  "relationship_preservation": {
    "temporal_preserved": 3,
    "temporal_lost": 2,
    "conditional_preserved": 1,
    "conditional_lost": 0,
    "exclusion_preserved": 0,
    "exclusion_lost": 1,
    "overall_preservation": 0.6
  },
  "lost_relationships": [
    {
      "type": "temporal",
      "from": "step_1",
      "to": "step_2",
      "constraint": "Step 2 must occur after Step 1",
      "risk": "HIGH - skipping Step 1 causes data corruption"
    }
  ],
  "structural_changes": [
    {
      "type": "conjunction_split",
      "original": "ALL of {A, B, C} required",
      "modified": "Three separate claims: A, B, C",
      "risk": "MEDIUM - may be interpreted as ANY instead of ALL"
    }
  ],
  "warnings": [
    {
      "severity": "CRITICAL|HIGH|MEDIUM|LOW",
      "type": "relationship_loss|structural_change|contradiction",
      "description": "Human-readable description",
      "recommendation": "Specific action to fix"
    }
  ],
  "summary": {
    "total_claims_a": 10,
    "total_claims_b": 10,
    "shared_count": 10,
    "unique_a_count": 0,
    "unique_b_count": 0,
    "relationships_a": 5,
    "relationships_b": 3,
    "relationships_preserved": 3,
    "relationships_lost": 2,
    "execution_equivalent": false,
    "confidence": "high"
  }
}
```

**Determinism**: Use consistent comparison logic. When uncertain, mark as unique.

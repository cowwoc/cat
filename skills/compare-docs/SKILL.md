---
name: compare-docs
description: Compare two documents semantically with relationship preservation to identify content and structural differences
---

# Semantic Document Comparison Command

**Task**: Compare two documents semantically: `{{arg1}}` vs `{{arg2}}`

**Goal**: Determine if documents contain the same semantic content AND preserve relationships (temporal, conditional, cross-document) despite different wording/organization.

**Method**: Enhanced claim extraction + relationship extraction + execution equivalence scoring

**⚠️ CRITICAL: Independent Extraction Required**

This command MUST extract claims from BOTH documents independently. NEVER:
- Pre-populate with "items to verify" or "improvements to check"
- Prime the extractor with knowledge of what changed between documents
- Use targeted confirmation instead of full extraction

Targeted validation (telling extractor what to look for) inflates scores by confirming
a checklist rather than independently discovering all claims.

---

## Overview

**Workflow**:
1. **Extract enhanced claims + relationships from Document A IN PARALLEL**
2. **Extract enhanced claims + relationships from Document B IN PARALLEL**
3. Compare claim sets AND relationship graphs (after both complete)
4. Calculate execution equivalence score (claim 40% + relationship 40% + graph 20%)
5. Report: shared/unique claims, preserved/lost relationships, warnings

**⚡ CRITICAL: Steps 1 and 2 MUST run in parallel (single message with two Task calls)**
- Extractions are completely independent (no cross-contamination risk)
- Running sequentially wastes time (~50% slower for no accuracy benefit)
- Step 3 waits for both to complete before comparing

**Key Insight**: Claim preservation ≠ Execution preservation. Documents can have identical claims but different execution behavior if relationships are lost.

**Reproducibility and Determinism**:

This command aims for high reproducibility but cannot guarantee perfect determinism due to LLM semantic judgment.

**Sources of Variance**:
1. **LLM Temperature** (±2-5% score variance if >0)
   - Mitigation: Use `temperature=0` in all Task calls (already specified above)
   - Expected with temp=0: ±0.5-1% residual variance
2. **Model Version** (±1-3% score drift across versions)
   - Mitigation: Pin exact model version (e.g., `"claude-opus-4-5-20251101"`)
   - Already specified in Task calls above
3. **Semantic Judgment** (±1-2% for boundary cases)
   - Claim similarity: "essentially the same" vs "slightly different"
   - Relationship matching: "same constraint" vs "subtly modified"
   - Inherent to semantic comparison, cannot be eliminated
4. **Claim Boundary Detection** (±0.5-1% for complex nested claims)
   - Conjunctions: Split into parts vs preserved as unit
   - Conditionals: Boundary of IF-THEN-ELSE scope
   - Minor variance in claim count (e.g., 191 vs 193)

**Expected Reproducibility**:
- **Same session, same documents**: ±0-1% (near-identical, small rounding differences)
- **Different sessions, temp=0, pinned model**: ±1-2% (good reproducibility)
- **Different sessions, temp>0**: ±3-7% (moderate variance)
- **Different model versions**: ±5-10% (significant drift possible)

**Best Practices for Consistency**:
- Always use `temperature=0` (already specified in Task calls)
- Pin model version if absolute consistency required across sessions
- Accept ±1-2% variance as inherent to semantic analysis
- Focus on score interpretation range (≥0.95, 0.85-0.94, etc.) not exact decimal

---

## Steps 1 & 2: Extract Claims + Relationships from BOTH Documents (IN PARALLEL)

**⚡ CRITICAL**: Invoke BOTH extraction agents in a single message with two Task tool calls.

**Why Parallel Execution**:
- ✅ **Safe**: Extractions are completely independent (no shared state)
- ✅ **Accurate**: No cross-contamination between Document A and B analysis
- ✅ **Faster**: ~50% time reduction (both extractions run simultaneously)
- ✅ **Required by Step 3**: Comparison waits for both anyway

**Agent Prompt Template** (use for BOTH documents):

**Agent Prompt**:
```
**SEMANTIC CLAIM AND RELATIONSHIP EXTRACTION**

**Document**: {{arg1}}

**Your Task**: Extract all semantic claims AND relationships from this document.

---

## Part 1: Claim Extraction

**What is a "claim"?**
- A requirement, instruction, rule, constraint, fact, or procedure
- A discrete unit of meaning that can be verified as present/absent
- Examples: "must do X before Y", "prohibited to use Z", "setting W defaults to V"

**Claim Types**:

1. **Simple Claims** (requirement, instruction, constraint, fact, configuration)
2. **Conjunctions**: ALL of {X, Y, Z} must be true
   - Markers: "ALL of the following", "both X AND Y", "requires all"
   - Example: "Approval requires: technical review AND budget review AND strategic review"
3. **Conditionals**: IF condition THEN consequence_true ELSE consequence_false
   - Markers: "IF...THEN...ELSE", "when X, do Y", "depends on"
   - Example: "IF attacker has monitoring THEN silent block ELSE network disconnect"
4. **Consequences**: Actions that result from conditions/events
   - Markers: "results in", "causes", "leads to", "enforcement"
   - Example: "Violating Step 1 causes data corruption (47 transactions affected)"
5. **Negations with Scope**: Prohibition with explicit scope
   - Markers: "NEVER", "prohibited", "CANNOT", "forbidden"
   - Example: "CANNOT run Steps 2 and 3 in parallel (data corruption risk)"

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
**Example**: "Step 3 (data migration) requires Step 2 (schema migration) to complete first"
**Constraint**: strict=true if order violation causes failure

### 2. Prerequisite Relationships (Condition → Action)
**Markers**: "prerequisite", "required before", "must be satisfied before"
**Example**: "All prerequisites (A, B, C) must be satisfied before Step 1"
**Constraint**: strict=true if prerequisite skipping causes failure

### 3. Hierarchical Conjunctions (ALL of X must be true)
**Markers**: "ALL", "both...AND...", "requires all", nested lists
**Example**: "Level 1: (A1 AND A2 AND A3) AND (B1 AND B2 AND B3)"
**Constraint**: all_required=true

### 4. Conditional Relationships (IF-THEN-ELSE)
**Markers**: "IF...THEN...ELSE", "when X, do Y", "depends on"
**Example**: "IF system powered on THEN memory dump ELSE disk removal"
**Constraint**: mutual_exclusivity=true for alternatives

### 5. Exclusion Constraints (A and B CANNOT co-occur)
**Markers**: "CANNOT run concurrently", "NEVER together", "mutually exclusive"
**Example**: "Steps 2 and 3 CANNOT run in parallel (data corruption risk)"
**Constraint**: strict=true if violation causes failure

### 6. Escalation Relationships (State A → State B under trigger)
**Markers**: "escalate to", "redirect to", "upgrade severity"
**Example**: "MEDIUM incident escalates to HIGH if privilege escalation possible"
**Constraint**: trigger condition explicit

### 7. Cross-Document References (Doc A → Doc B Section X)
**Markers**: "see Section X.Y", "defined in Document Z", "refer to"
**Example**: "Technical Architect (see Project Roles document, Section 2.1)"
**Constraint**: preserve section numbering as navigation anchor

---

## Output Format (JSON)

```json
{
  "claims": [
    {
      "id": "claim_1",
      "type": "simple|conjunction|conditional|consequence|negation",
      "text": "normalized claim text",
      "location": "line numbers or section",
      "confidence": "high|medium|low",

      // For conjunctions
      "sub_claims": ["claim_2", "claim_3"],
      "all_required": true,

      // For conditionals
      "condition": "condition text",
      "true_consequence": "claim_4",
      "false_consequence": "claim_5",

      // For consequences
      "triggered_by": "event or condition",
      "impact": "severity description",

      // For negations
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
    "edges": [
      ["claim_1", "claim_2"],
      ["claim_2", "claim_3"]
    ],
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

**CRITICAL**: Extract ALL relationships, not just claims. Relationships are as important as claims for execution equivalence.
```

**Execute PARALLEL extraction (single message with TWO Task calls)**:

```bash
# ⚡ INVOKE BOTH AGENTS IN PARALLEL (single message, 2 Task tool calls)
# This is a SINGLE assistant message containing TWO Task invocations

Task(
  subagent_type="general-purpose",
  model="opus",  # For reproducibility, consider pinning: "claude-opus-4-5-20251101"
  temperature=0,   # Deterministic sampling for consistency
  description="Extract claims from Document A",
  prompt="[Full extraction prompt above]

  Document: {{arg1}}

  Extract all claims and relationships.
  Return COMPLETE JSON (not summary)."
)

Task(
  subagent_type="general-purpose",
  model="opus",  # For reproducibility, consider pinning: "claude-opus-4-5-20251101"
  temperature=0,   # Deterministic sampling for consistency
  description="Extract claims from Document B",
  prompt="[Full extraction prompt above]

  Document: {{arg2}}

  Extract all claims and relationships.
  Return COMPLETE JSON (not summary)."
)

# Wait for BOTH agents to complete, then save results

# Save Document A extraction
cat > /tmp/compare-doc-a-extraction.json << 'EOF'
{agent JSON response with ALL claims and relationships from Document A}
EOF

# Save Document B extraction
cat > /tmp/compare-doc-b-extraction.json << 'EOF'
{agent JSON response with ALL claims and relationships from Document B}
EOF

echo "✅ Saved Document A extraction: $(wc -l < /tmp/compare-doc-a-extraction.json) lines"
echo "✅ Saved Document B extraction: $(wc -l < /tmp/compare-doc-b-extraction.json) lines"
```

**❌ WRONG: Sequential Execution**
```bash
# DON'T do this - wastes time
Task(doc A) → wait → save
Task(doc B) → wait → save  # Unnecessarily sequential
```

**✅ CORRECT: Parallel Execution**
```bash
# Single message with both Task calls
Task(doc A)
Task(doc B)
# Both run simultaneously, then save both results
```

---

## Step 3: Compare Claims AND Relationships

**Invoke comparison agent** with enhanced comparison logic:

**Agent Prompt**:
```
**SEMANTIC COMPARISON WITH RELATIONSHIP ANALYSIS**

**Document A Data**: {{DOC_A_DATA}}

**Document B Data**: {{DOC_B_DATA}}

**Your Task**: Compare claims AND relationships to determine execution equivalence.

---

## Part 1: Claim Comparison

**Comparison Rules**:

1. **Exact Match**: Identical normalized text → shared
2. **Semantic Equivalence**: Different wording, identical meaning → shared
3. **Type Mismatch**: Same concept but different structure (e.g., conjunction split into separate claims) → flag as structural change
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

```python
def calculate_relationship_preservation(a_rels, b_rels, shared_claims):
    # Only count relationships where both endpoints are shared claims
    a_valid = [r for r in a_rels if r.from in shared_claims and r.to in shared_claims]
    b_valid = [r for r in b_rels if r.from in shared_claims and r.to in shared_claims]

    preserved = count_matching_relationships(a_valid, b_valid)
    lost = len(a_valid) - preserved
    added = len(b_valid) - preserved

    if len(a_valid) == 0:
        return 1.0  # No relationships to preserve

    return preserved / len(a_valid)
```

---

## Part 3: Dependency Graph Comparison

**Graph Comparison**:

1. **Topology**: Compare graph structure (linear_chain vs tree vs dag)
2. **Connectivity**: Compare edge preservation (same connections?)
3. **Critical Path**: Compare critical paths (same ordering?)

**Graph Structure Score**:

```python
def calculate_graph_score(a_graph, b_graph, shared_claims):
    # Subgraph of shared claims
    a_sub = subgraph(a_graph, shared_claims)
    b_sub = subgraph(b_graph, shared_claims)

    # Compare connectivity
    connectivity = edge_preservation(a_sub, b_sub)

    # Compare topology
    topology = 1.0 if a_sub.topology == b_sub.topology else 0.5

    # Compare critical path
    critical_path = path_similarity(a_sub.critical_path, b_sub.critical_path)

    return (connectivity * 0.5) + (topology * 0.25) + (critical_path * 0.25)
```

---

## Part 4: Execution Equivalence Scoring

**Scoring Formula**:

```python
def calculate_execution_equivalence(claim_comp, rel_comp, graph_comp):
    weights = {
        "claim_preservation": 0.4,
        "relationship_preservation": 0.4,
        "graph_structure": 0.2
    }

    scores = {
        "claim_preservation": claim_comp["semantic_equivalence"],
        "relationship_preservation": rel_comp["overall_preservation"],
        "graph_structure": graph_comp["structure_score"]
    }

    base_score = sum(weights[k] * scores[k] for k in weights.keys())

    # Penalty for critical relationship loss
    if rel_comp["overall_preservation"] < 0.9:
        base_score *= 0.7

    return {
        "score": base_score,
        "components": scores,
        "interpretation": interpret_score(base_score)
    }

def interpret_score(score):
    if score >= 0.95:
        return "Execution equivalent - minor differences acceptable"
    elif score >= 0.75:
        return "Mostly equivalent - review relationship changes"
    elif score >= 0.50:
        return "Significant differences - execution may differ"
    else:
        return "CRITICAL - execution will fail or produce wrong results"
```

---

## Part 5: Warning Generation

**Generate Warnings for**:

1. **Relationship Loss**: "100% of temporal dependencies lost"
2. **Structural Changes**: "Conjunction split into independent claims (loses 'ALL required' semantic)"
3. **Conditional Logic Loss**: "IF-THEN-ELSE flattened to concurrent claims (introduces contradiction)"
4. **Cross-Reference Breaks**: "Section numbering removed, breaking 'see Section 2.3' references"
5. **Exclusion Constraint Loss**: "Concurrency prohibition lost (Steps 2/3 may run in parallel → data corruption)"

**Warning Format**:

```json
{
  "severity": "CRITICAL|HIGH|MEDIUM|LOW",
  "type": "relationship_loss|structural_change|contradiction|navigation_loss",
  "description": "Human-readable description",
  "affected_claims": ["claim_1", "claim_2"],
  "recommendation": "Specific action to fix"
}
```

---

## Output Format (JSON)

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
  "unique_to_a": [
    {
      "claim": "claim only in A",
      "doc_a_id": "claim_5",
      "closest_match_in_b": null
    }
  ],
  "unique_to_b": [
    {
      "claim": "claim only in B",
      "doc_b_id": "claim_7"
    }
  ],
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
      "risk": "HIGH - skipping Step 1 causes data corruption",
      "evidence": "Line 42: 'Step 2 depends on Step 1 completing'"
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
      "severity": "CRITICAL",
      "type": "relationship_loss",
      "description": "40% of temporal dependencies lost (2/5)",
      "recommendation": "Restore Step 1 → Step 2 ordering constraint"
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
```

**Execute comparison**:
```bash
# Agent returns enhanced comparison JSON
COMPARISON="<parse JSON response>"
```

---

## Step 4: Generate Human-Readable Report

**Format with execution equivalence focus**:

```markdown
## Semantic Comparison: {{arg1}} vs {{arg2}}

### Execution Equivalence Summary

- **Execution Equivalence Score**: {score}/1.0 ({interpretation})
- **Claim Preservation**: {claim_score}/1.0 ({shared}/{total} claims)
- **Relationship Preservation**: {rel_score}/1.0 ({preserved}/{total} relationships)
- **Graph Structure**: {graph_score}/1.0

**Interpretation**: {interpretation based on score}

---

### Component Scores

**Claim Preservation** ({claim_score}/1.0):
- Shared claims: {shared_count}
- Unique to {{arg1}}: {unique_a_count}
- Unique to {{arg2}}: {unique_b_count}

**Relationship Preservation** ({rel_score}/1.0):
- Temporal: {temporal_preserved}/{temporal_total} preserved
- Conditional: {conditional_preserved}/{conditional_total} preserved
- Exclusion: {exclusion_preserved}/{exclusion_total} preserved
- Cross-document: {cross_doc_preserved}/{cross_doc_total} preserved

**Graph Structure** ({graph_score}/1.0):
- Topology: {topology_a} → {topology_b} ({match})
- Connectivity: {edge_preservation}%
- Critical path: {path_similarity}%

---

### Warnings ({warning_count})

{for each warning with severity CRITICAL or HIGH:}
**{severity}**: {description}
- **Affected**: {affected_claims}
- **Risk**: {risk description}
- **Recommendation**: {recommendation}

---

### Lost Relationships ({lost_count})

{for each lost relationship:}
{id}. **{type}**: {from_claim} → {to_claim}
   - Constraint: {constraint}
   - Evidence: {evidence}
   - Risk: {risk description}
   - Consequence if violated: {violation_consequence}

---

### Structural Changes ({change_count})

{for each structural change:}
{id}. **{type}**
   - Original ({{arg1}}): {original structure}
   - Modified ({{arg2}}): {modified structure}
   - Risk: {risk assessment}
   - Impact: {execution impact}

---

### Shared Claims ({shared_count})

{for each shared claim:}
{id}. **{claim}**
   - Match: {similarity}%
   - Type: {type} {type_match indicator}
   - Document A: {location} (ID: {doc_a_id})
   - Document B: {location} (ID: {doc_b_id})

---

### Unique to {{arg1}} ({unique_a_count})

{for each unique_to_a:}
{id}. **{claim}**
   - Source: {location} (ID: {doc_a_id})
   - Type: {type}
   - Confidence: {confidence}

---

### Unique to {{arg2}} ({unique_b_count})

{for each unique_to_b:}
{id}. **{claim}**
   - Source: {location} (ID: {doc_b_id})
   - Type: {type}
   - Confidence: {confidence}

---

### Analysis

**Execution Equivalence**: {score}/1.0

**Key Findings**:
- {primary finding based on score}
- {relationship preservation status}
- {structural change summary}

**Recommendation**: {APPROVE/REVIEW/REJECT based on score}

**Confidence**: {summary.confidence}

**Methodology**: Claim extraction + relationship extraction + execution equivalence scoring
```

---

## Execution Equivalence Criteria

**Score Thresholds**:

- **≥0.95**: **APPROVE** - Execution equivalent (minor cosmetic differences acceptable)
- **0.75-0.94**: **REVIEW REQUIRED** - Moderate relationship changes (manual review needed)
- **0.50-0.74**: **REJECT RECOMMENDED** - Significant execution differences
- **<0.50**: **REJECT CRITICAL** - Execution will fail or produce wrong results

**Decision Criteria**:

1. Claim preservation alone is NOT sufficient for execution equivalence
2. Relationship preservation is CRITICAL for execution equivalence
3. Graph structure changes indicate procedural differences
4. Warnings guide manual review focus

---

### Score Interpretation and Vulnerabilities

#### **≥0.95** - Execution Equivalent

**Characteristics**:
- All claims, relationships, and decision paths explicitly preserved
- Minor wording variations or formatting changes only
- No ambiguity in interpretation

**Vulnerabilities**: None - documents produce identical results when followed

**Approval**: Automatic - safe to use without review

---

#### **0.85-0.94** - Functional Equivalence with Abstraction Risks

**Characteristics**:
- Core logic and decision paths intact
- Relationships may be implied rather than explicit
- Abstraction level differs from original

**Vulnerabilities**:

1. **Abstraction Ambiguity**
   ```
   Original: "ALL of {A, B, C} must be satisfied"
   Score 0.87: "A required", "B required", "C required" (separate statements)
   Risk: Could interpret as "ANY of A, B, C" instead of "ALL required"
   Impact: Partial completion when full completion required
   ```

2. **Lost Mutual Exclusivity**
   ```
   Original: "Steps 2 and 3 CANNOT run in parallel (data corruption risk)"
   Score 0.89: "Step 2: migrate data", "Step 3: update schema"
   Risk: Exclusion constraint lost
   Impact: Concurrent execution → data corruption
   ```

3. **Conditional Logic Flattening**
   ```
   Original: "IF system powered on THEN memory dump ELSE disk removal"
   Score 0.86: "Memory dump procedure", "Disk removal procedure"
   Risk: Decision tree collapsed, creates contradictions
   Impact: Cannot determine correct action, might perform wrong procedure
   ```

4. **Temporal Dependency Loss**
   ```
   Original: "Step 2 depends on Step 1 completing first"
   Score 0.91: "Step 1: schema migration", "Step 2: data migration"
   Risk: Dependency becomes implicit ordering only
   Impact: Could skip prerequisite → cascading failures
   ```

5. **Cross-Reference Navigation Breaks**
   ```
   Original: "Technical Architect (see Section 2.3)"
   Score 0.88: "Technical Architect (see Project Roles document)"
   Risk: Section anchor removed, cannot navigate to specific location
   Impact: Multi-hop reasoning fails, precision lost
   ```

6. **Escalation Path Ambiguity**
   ```
   Original: "MEDIUM incident escalates to HIGH if privilege escalation possible"
   Score 0.90: "MEDIUM incident severity", "HIGH for privilege escalation"
   Risk: Trigger condition disconnected from action
   Impact: Unclear when to escalate, might miss critical situations
   ```

**Approval**: Manual review required - assess if abstraction is acceptable for use case

**Risk Assessment**: Moderate - careful readers might infer correctly, but no guarantee

---

#### **0.50-0.74** - Significant Execution Differences

**Characteristics**:
- Major relationship losses (>40% of critical relationships lost)
- Structural changes fundamentally alter execution flow
- Multiple vulnerability types compound

**Vulnerabilities**: All issues from 0.85-0.94 range, plus:
- Entire decision branches missing
- Critical prerequisites omitted entirely
- Contradictory instructions present
- Safety constraints removed

**Approval**: Reject recommended - significant rework needed

**Risk Assessment**: High - execution will likely differ from original intent

---

#### **<0.50** - Critical Execution Failure

**Characteristics**:
- Majority of relationships destroyed (>60% lost)
- Logic structure unrecognizable from original
- Core decision paths missing

**Vulnerabilities**: Complete execution failure
- Cannot determine correct actions
- Contradictions make document unusable
- Critical safety constraints absent
- Procedural ordering completely lost

**Approval**: Reject critical - document will produce wrong results or fail entirely

**Risk Assessment**: Critical - following this document leads to failures, data loss, or unsafe operations

---

### Vulnerability Summary Table

| Score Range | Relationship Preservation | Primary Risk | Failure Mode |
|-------------|--------------------------|--------------|--------------|
| **≥0.95** | Explicit (>95%) | None | N/A - Safe |
| **0.85-0.94** | Mostly explicit (85-95%) | Abstraction ambiguity | Misinterpretation possible |
| **0.75-0.84** | Partial (75-85%) | Relationship inference required | Execution may differ |
| **0.50-0.74** | Significant loss (50-75%) | Logic structure damaged | Likely execution failures |
| **<0.50** | Majority lost (<50%) | Core logic destroyed | Certain execution failures |

**Key Insight**: The score primarily reflects relationship preservation, not just claim matching. A score of 0.85 means ~15% of critical relationships are lost or abstracted, creating interpretation vulnerabilities that ≥0.95 avoids.

---

## Limitations

1. **Relationship Inference**: System extracts only explicitly stated relationships. Heavily implied relationships may be missed.

2. **Domain Knowledge**: Some relationships require domain expertise to identify (e.g., knowing that schema migration must precede data migration).

3. **Nested Complexity**: Very deeply nested conditionals (IF within IF within IF) may not be fully captured.

4. **Cross-Document Completeness**: Multi-document comparison requires all referenced documents to be provided (cannot follow external references).

5. **Execution Context**: System cannot execute code/procedures to verify equivalence - relies on structural analysis.

---

## Examples

### Example 1: Execution Equivalent (High Score)

**Command**:
```bash
/compare-docs docs/deployment-v1.md docs/deployment-v2.md
```

**Result**:
```
Execution Equivalence Score: 0.98/1.0 (Execution equivalent)
Claim Preservation: 1.0 (6/6 claims)
Relationship Preservation: 0.95 (5/5 relationships preserved)
Graph Structure: 1.0 (linear chain preserved)

Recommendation: APPROVE - Documents are execution equivalent.
```

---

### Example 2: Relationship Loss (Low Score)

**Command**:
```bash
/compare-docs docs/incident-response-original.md docs/incident-response-compressed.md
```

**Result**:
```
Execution Equivalence Score: 0.32/1.0 (CRITICAL - execution will fail)
Claim Preservation: 1.0 (10/10 claims)
Relationship Preservation: 0.0 (0/8 conditional relationships preserved)
Graph Structure: 0.1 (decision tree flattened to list)

CRITICAL WARNING: 100% of conditional logic lost (8/8 decision points)
- IF-THEN-ELSE context removed
- Creates 2 apparent contradictions
- Cannot determine correct actions for given situations

Recommendation: REJECT CRITICAL - Compressed document loses all conditional logic.
Lost relationships will cause incorrect actions to be taken.
```

---

### Example 3: Cross-Document Reference Break (Medium Score)

**Command**:
```bash
/compare-docs docs/approval-process-original.md docs/approval-process-compressed.md
```

**Result**:
```
Execution Equivalence Score: 0.48/1.0 (Significant execution differences)
Claim Preservation: 1.0 (6/6 claims)
Relationship Preservation: 0.0 (0/7 cross-document references preserved)
Graph Structure: 0.0 (reference graph destroyed)

HIGH WARNING: Section numbering removed
- Breaks 7 cross-document references ("see Section 2.3")
- Navigation structure destroyed
- Cannot follow multi-hop reasoning (e.g., "Who appoints Finance Manager?")

Recommendation: REJECT - Cross-document references broken. Users cannot navigate to referenced content.
```

---

## Use Cases

**Best suited for**:
- Deployment procedures (temporal dependencies critical)
- Incident response guides (conditional logic critical)
- Multi-document systems (cross-references critical)
- Approval workflows (hierarchical conjunctions critical)
- Security policies (exclusion constraints critical)
- Technical specifications with ordering requirements
- Procedural documentation with decision trees

**Also works for simpler documents**:
- Reference documentation
- FAQs
- Glossaries
- Simple requirement lists

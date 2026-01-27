---
name: compare-docs
description: >
  Use to validate semantic equivalence when compressing, modifying, or comparing documentation.
  Required by shrink-doc for validation.
---

# Semantic Document Comparison

**Task**: Compare two documents semantically: `{{arg1}}` vs `{{arg2}}`

**Goal**: Determine if documents contain the same semantic content AND preserve relationships
(temporal, conditional, cross-document) despite different wording/organization.

---

## Procedure

### Step 1: Extract Claims from Both Documents (IN PARALLEL)

**⚡ CRITICAL**: Invoke BOTH extraction agents in a single message with two Task tool calls.

**Why Parallel Execution**:
- Extractions are completely independent (no cross-contamination risk)
- Running sequentially wastes time (~50% slower for no accuracy benefit)
- Step 2 waits for both to complete before comparing

**⚠️ ENCAPSULATION (M269)**: The extraction algorithm is in a separate internal document.
Do NOT attempt extraction manually - invoke subagents which read their own instructions.

**Subagent invocation** (invoke BOTH in a single message):

```
Task tool #1:
  subagent_type: "general-purpose"
  model: "opus"
  description: "Extract claims from Document A"
  prompt: |
    Read the instructions at: plugin/skills/compare-docs/EXTRACTION-AGENT.md

    Then extract all semantic claims and relationships from:
    - DOCUMENT: {{arg1}}

    Return COMPLETE JSON (not summary).

Task tool #2:
  subagent_type: "general-purpose"
  model: "opus"
  description: "Extract claims from Document B"
  prompt: |
    Read the instructions at: plugin/skills/compare-docs/EXTRACTION-AGENT.md

    Then extract all semantic claims and relationships from:
    - DOCUMENT: {{arg2}}

    Return COMPLETE JSON (not summary).
```

**After both complete**, save results:
- Document A extraction → `/tmp/compare-doc-a-extraction.json`
- Document B extraction → `/tmp/compare-doc-b-extraction.json`

### Step 2: Compare Claims and Relationships

**⚠️ ENCAPSULATION (M269)**: The comparison algorithm is in a separate internal document.

**Subagent invocation**:

```
Task tool:
  subagent_type: "general-purpose"
  model: "opus"
  description: "Compare document extractions"
  prompt: |
    Read the instructions at: plugin/skills/compare-docs/COMPARISON-AGENT.md

    Compare these two document extractions:
    - Document A data: /tmp/compare-doc-a-extraction.json
    - Document B data: /tmp/compare-doc-b-extraction.json

    Return execution equivalence score and detailed comparison JSON.
```

### Step 3: Generate Human-Readable Report

Format the comparison results as a report with:
- Execution Equivalence Summary (score, interpretation)
- Component Scores (claim, relationship, graph)
- Warnings (CRITICAL/HIGH severity first)
- Lost Relationships
- Structural Changes
- Shared Claims count
- Unique claims per document

---

## Reproducibility Notes

This command aims for high reproducibility but cannot guarantee perfect determinism due to
LLM semantic judgment.

**Expected Reproducibility**:
- Same session, same documents: ±0-1%
- Different sessions, temp=0, pinned model: ±1-2%
- Different model versions: ±5-10%

**Best Practices**: Focus on score interpretation range (≥0.95, 0.85-0.94, etc.) not exact decimal.

---

## Score Interpretation

**Score Thresholds (Default)**:

| Score | Decision | Meaning |
|-------|----------|---------|
| ≥0.95 | **APPROVE** | Execution equivalent |
| 0.75-0.94 | **REVIEW REQUIRED** | Moderate relationship changes |
| 0.50-0.74 | **REJECT RECOMMENDED** | Significant execution differences |
| <0.50 | **REJECT CRITICAL** | Execution will fail |

**Context-Specific Thresholds (M271)**:

| Context | Required Threshold | Rationale |
|---------|-------------------|-----------|
| `/cat:shrink-doc` validation | **= 1.0** (exact) | Compression must preserve ALL content |
| General comparison | ≥0.95 (default) | Minor cosmetic differences acceptable |

**Decision Criteria**:

1. Claim preservation alone is NOT sufficient for execution equivalence
2. Relationship preservation is CRITICAL for execution equivalence
3. Graph structure changes indicate procedural differences
4. Warnings guide manual review focus

---

## Score Vulnerabilities Reference

### 0.85-0.94 Range - Abstraction Risks

At this score range, watch for:
- **Abstraction Ambiguity**: "ALL of {A,B,C}" split into separate statements
- **Lost Mutual Exclusivity**: Concurrency constraints removed
- **Conditional Logic Flattening**: IF-THEN-ELSE collapsed to separate procedures
- **Temporal Dependency Loss**: Step ordering becomes implicit only
- **Cross-Reference Breaks**: Section anchors removed
- **Escalation Path Ambiguity**: Trigger conditions disconnected

### <0.75 Range - Critical Issues

- Entire decision branches missing
- Critical prerequisites omitted
- Contradictory instructions present
- Safety constraints removed

---

## Verification

- [ ] Both extraction agents invoked in parallel (single message)
- [ ] Comparison agent invoked with both extraction results
- [ ] Score interpretation matches threshold table
- [ ] Warnings reviewed for CRITICAL/HIGH severity
- [ ] Report generated with all required sections

---

## Limitations

1. **Relationship Inference**: Only explicitly stated relationships extracted
2. **Domain Knowledge**: Some relationships require domain expertise to identify
3. **Nested Complexity**: Deeply nested conditionals may not be fully captured
4. **Cross-Document Completeness**: Cannot follow external references
5. **Execution Context**: Relies on structural analysis, not actual execution

---

## Use Cases

**Best suited for**:
- Deployment procedures (temporal dependencies critical)
- Incident response guides (conditional logic critical)
- Approval workflows (hierarchical conjunctions critical)
- Security policies (exclusion constraints critical)
- Technical specifications with ordering requirements

**Also works for simpler documents**:
- Reference documentation, FAQs, glossaries

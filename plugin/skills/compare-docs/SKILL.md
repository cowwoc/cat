---
description: >
  Use to validate semantic equivalence when compressing, modifying, or comparing documentation.
  Required by shrink-doc for validation.
user-invocable: false
---

# Semantic Document Comparison

**Issue**: Compare two documents semantically: `{{arg1}}` vs `{{arg2}}`

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

### Step 2: Compare and Generate Report (Single Subagent)

**CRITICAL (M295): Entire comparison MUST run in subagent to avoid context pollution.**

The comparison subagent performs BOTH comparison AND report generation. The main agent
receives ONLY the final formatted report, not intermediate JSON data.

**⚠️ ENCAPSULATION (M269)**: The comparison algorithm is in a separate internal document.

**Determine threshold before spawning:**
- If invoked from `/cat:shrink-doc` → Required threshold = **1.0 (exact)**
- Otherwise → Default threshold = **0.95**

**Subagent invocation**:

```
Task tool:
  subagent_type: "general-purpose"
  model: "opus"
  description: "Compare documents and generate report"
  prompt: |
    Read the instructions at: plugin/skills/compare-docs/COMPARISON-AGENT.md

    Compare these two document extractions:
    - Document A data: /tmp/compare-doc-a-extraction.json
    - Document B data: /tmp/compare-doc-b-extraction.json

    **Required threshold: {threshold}** (1.0 for shrink-doc, 0.95 otherwise)

    After comparison, generate the FINAL REPORT directly (do not return JSON).

    **Report format (output this exactly):**

    ╔══════════════════════════════════════════════════════╗
    ║  EXECUTION EQUIVALENCE: {SCORE}                      ║
    ║  Required Threshold: {threshold}                     ║
    ║  Status: {PASS if score >= threshold, else FAIL}     ║
    ╚══════════════════════════════════════════════════════╝

    ## Component Scores
    | Component | Score |
    |-----------|-------|
    | Claim Preservation | {score} |
    | Relationship Preservation | {score} |
    | Graph Structure | {score} |

    ## Warnings
    {List CRITICAL/HIGH severity warnings first}

    ## Lost Relationships
    {If any}

    ## Summary
    - Shared Claims: {count}
    - Unique to A: {count}
    - Unique to B: {count}

    Return ONLY this formatted report. Do NOT return raw JSON.
```

**Why single subagent for compare+report (M295):**
- Keeps ~10K+ tokens of extraction/comparison data OUT of main agent context
- Main agent receives only the ~500 token formatted report
- Prevents context pollution that degrades main agent quality

### Step 3: Run Second Comparison (MANDATORY - M350)

**⚠️ MANDATORY**: Due to ±10-35% extraction variance, run comparison TWICE for reliable scores.

Repeat Step 1 and Step 2 to get a second independent score for each document pair.

**Why this is mandatory:**
- Single comparison may be an outlier (extraction agents classify claims differently each run)
- Best-2-of-3 eliminates variance-caused false PASS/FAIL decisions
- M350 failure: skipping this step led to unreliable validation

**Parallel execution**: For efficiency, spawn the second comparison in parallel with the first
(4 extraction agents total for a single document pair, then 2 comparison agents).

**Consensus rules:**

| Threshold | Run 1 | Run 2 | Action |
|-----------|-------|-------|--------|
| 0.95 (general) | Both ≥0.95 | - | PASS (use average) |
| 0.95 (general) | Both <0.95 | - | FAIL (use average) |
| 0.95 (general) | Disagree (>0.05 diff) | - | Run tiebreaker (3rd comparison) |
| 1.0 (shrink-doc) | Both = 1.0 | - | PASS |
| 1.0 (shrink-doc) | One ≠ 1.0 | - | Run tiebreaker |
| 1.0 (shrink-doc) | ≥2 of 3 = 1.0 | - | PASS |
| 1.0 (shrink-doc) | <2 of 3 = 1.0 | - | FAIL (iterate) |

### Step 4: Relay Report to User

The main agent relays the consensus result with both/all scores shown.

**Report format with consensus:**
```
Run 1: 0.97 | Run 2: 0.95 | Consensus: 0.96 (PASS)
```

**If Status = FAIL for shrink-doc context:** The calling workflow MUST iterate. Do not proceed to approval.

---

## Reproducibility Notes (M321, M346, M350)

This command aims for high reproducibility but cannot guarantee perfect determinism due to
LLM semantic judgment in claim extraction.

**Temperature Control**: The Task tool does not support a temperature parameter. Subagents
run with default temperature settings, which contributes to variance between invocations.

**Expected Reproducibility**:
- Same session, same documents: ±0-1%
- Different sessions, pinned model: ±1-5%
- Different model versions: ±5-10%
- **Different agent invocations: ±10-35%** (claim type classification variance)

**Per-File Reporting Requirement (M346)**:

When validating multiple files in batch operations, report EACH file individually:

| File | Tokens Before | Tokens After | Reduction | Score | Status |
|------|---------------|--------------|-----------|-------|--------|
| agent-architecture.md | 1,245 | 823 | 34% | 0.51 | FAIL |
| build-verification.md | 567 | 312 | 45% | 0.97 | FAIL |
| ... | ... | ... | ... | ... | ... |

Do NOT report aggregate scores across files. Each file's equivalence is independent.
If all files report identical scores (e.g., all 1.0), this suggests fabrication - verify each was validated.

**Why Scores Can Vary Significantly (M321)**:

Extraction agents make judgment calls on claim types:
- "MUST include X (M122)" → classified as `negation` OR `simple`?
- "if needed" clauses → classified as `conditional` OR `simple`?

Different classifications lead to different relationship counts and structural change detection.
The 0.7x penalty for relationship_preservation < 0.9 can swing scores dramatically (e.g., 0.93 → 0.65).

**Best Practices**:
1. Focus on score interpretation range (≥0.95, 0.85-0.94, etc.) not exact decimal
2. If scores vary >20% between runs, re-run extraction and average
3. For shrink-doc validation requiring 1.0, consider multiple comparison runs

**MANDATORY: Best-2-of-3 Validation (M346, M350)**

**This is now Step 3 in the Procedure section above.** All comparisons MUST run twice minimum.

See Step 3 for consensus rules and execution details.

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
- [ ] **Second comparison run completed (M350)** - NOT optional
- [ ] Consensus calculated from 2+ runs
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

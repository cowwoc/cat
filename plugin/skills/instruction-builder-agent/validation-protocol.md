<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Validation Protocol

**Reference document** — Used by skill-builder for two purposes:
- **Section 1 (Extraction):** Auto-generating test cases from skill instructions, and extracting units for
  the semantic pre-check during compression
- **Section 2 (Comparison):** The optional semantic pre-check gate during compression

---

## Section 1: Semantic Extraction Algorithm

### What is a Semantic Unit?

A discrete statement that affects execution behavior:
- Requirements, prohibitions, constraints
- Sequences, conditions, dependencies
- References to other documents

**Example:** "Check pwd before rm-rf" is ONE unit capturing both actions AND their ordering.

---

### Nine Categories (Mutually Exclusive, Priority Order)

Check categories in order. **First match wins.**

#### Priority 1: EXCLUSION
**Markers:** "cannot" + (both/together/simultaneously/co-occur), "mutually exclusive", "either...or" (exclusive)

**Examples:**
- "Steps 2 and 3 cannot run together" → EXCLUSION
- "Cannot have both A and B enabled" → EXCLUSION

#### Priority 2: CONJUNCTION
**Markers:** "all of" + list, "both...and...required", "requires all"

**Examples:**
- "Review AND approval AND signoff ALL required" → CONJUNCTION
- "Requires all of: tests, review, approval" → CONJUNCTION

#### Priority 3: PROHIBITION
**Markers:** NEVER, MUST NOT, CANNOT + action (without co-occurrence indicator), "forbidden", "prohibited"

**Examples:**
- "NEVER rm-rf without checking pwd" → PROHIBITION
- "MUST NOT skip validation" → PROHIBITION
- "Cannot run step 2" (no co-occurrence) → PROHIBITION

#### Priority 4: CONDITIONAL
**Markers:** IF...THEN, WHEN + consequence, "unless", "depends on whether/if"

**Examples:**
- "IF pwd in target THEN cd first" → CONDITIONAL
- "When build fails, notify team" → CONDITIONAL
- "Depends on whether config exists" → CONDITIONAL

#### Priority 5: CONSEQUENCE
**Markers:** "causes", "results in", "leads to", "triggers"

**Examples:**
- "Deleting pwd causes shell breakage" → CONSEQUENCE
- "Invalid input results in rejection" → CONSEQUENCE

#### Priority 6: DEPENDENCY
**Markers:** "required for", "prerequisite for", "necessary for", "depends on" + noun (without if/whether)

**Examples:**
- "SSL cert required for HTTPS" → DEPENDENCY
- "Valid config depends on schema file" → DEPENDENCY
- "Authentication prerequisite for API access" → DEPENDENCY

#### Priority 7: SEQUENCE
**Markers:** "before", "after", "then", "first", "prior to", "following", "Step N...Step M"

**Examples:**
- "Check pwd before rm-rf" → SEQUENCE
- "Run tests, then deploy" → SEQUENCE
- "Step 1 must complete before Step 2" → SEQUENCE

#### Priority 8: REFERENCE
**Markers:** "see", "refer to", "defined in", "documented in", "follow...in {path}"

**Examples:**
- "See ops/deploy.md for checklist" → REFERENCE
- "Defined in config/settings.json" → REFERENCE

#### Priority 9: REQUIREMENT (Default)
**Markers:** "must", "required", "should", "shall", or no specific markers

**Examples:**
- "Must restart Claude Code" → REQUIREMENT
- "Should validate input" → REQUIREMENT
- "Restart the service" → REQUIREMENT

---

### Disambiguation Rules

#### "cannot" Disambiguation
- "cannot" + co-occurrence indicator (both/together/simultaneously) → EXCLUSION
- "cannot" + "until" → DEPENDENCY (temporal prerequisite, not general prohibition)
- "cannot" + action alone → PROHIBITION

**Examples:**
- "Cannot run A and B together" → EXCLUSION
- "Cannot deploy until tests pass" → DEPENDENCY (tests → deploy)
- "Cannot skip validation" → PROHIBITION

#### "depends on" Disambiguation
- "depends on whether/if" → CONDITIONAL
- "depends on" + noun phrase → DEPENDENCY
- "depends on [noun] being [participle]":
  - If binary state (present/absent, enabled/disabled, green/red) → DEPENDENCY (blocking prerequisite)
  - If behavior branches based on state → CONDITIONAL (execution path changes)

**The distinction:** Does it BLOCK (dependency) or BRANCH (conditional)?

#### Compound Statements
When a statement contains multiple markers, classify by highest priority marker, but capture embedded
semantics in normalization.

**Example:** "MUST NOT run A before B"
- Has "MUST NOT" (PROHIBITION, priority 3)
- Has "before" (SEQUENCE, priority 7)
- Classification: **PROHIBITION**
- Normalization: `prohibited: [sequence: A → B]`

---

### Normalization

For each extracted unit, provide both original text and normalized form.

**Normalize to consistent grammatical form while preserving semantic content:**
- **Tense:** Present tense ("validate" not "validated")
- **Voice:** Active/imperative ("validate input" not "input must be validated")
- **Mood:** Imperative/declarative ("validate" not "you should validate")

#### Basic Normalization

| Category | Normalized Form |
|----------|-----------------|
| EXCLUSION | `exclusion: A, B` |
| CONJUNCTION | `conjunction: [A, B, C]` |
| PROHIBITION | `must not: X` (strict) or `prohibit: X` (standard) |
| CONDITIONAL | `conditional: X → Y` or `conditional: X → Y \| Z` (with else) |
| CONSEQUENCE | `consequence: X → Y` |
| DEPENDENCY | `dependency: X → Y` |
| SEQUENCE | `must: X → Y` (strict) or `sequence: X → Y` (standard) |
| REFERENCE | `reference: path` |
| REQUIREMENT | `must: X` (strict) or `require: X` (standard) or `should: X` (advisory) |

#### Strength Distinction

| Original | Normalized | Meaning |
|----------|------------|---------|
| "MUST X", "NEVER X", "CRITICAL: X" | `must: X` | Strict — violation is catastrophic |
| "must X", "required", "X" (imperative) | `require: X` | Standard — violation is problematic |
| "should X", "recommended" | `should: X` | Advisory — violation is suboptimal |

**Strict markers** (use `must:` or `must not:`): ALL CAPS, explicit severity, violation consequences mentioned.
**Standard markers** (use `require:` or `prohibit:`): mixed case, default imperatives without severity.
**Advisory markers** (use `should:`): should, recommended, prefer, consider, ideally.

---

### Extraction Rules

1. **Granularity:** One unit per semantic statement
2. **Completeness:** Extract ALL units that affect execution
3. **No Duplicates:** If same constraint appears multiple ways, extract once
4. **Original Language:** Preserve exact wording for feedback
5. **Skip:** Pure examples without constraints, meta-commentary, formatting
6. **Quote:** Include the minimal verbatim span from the source document in the `quote` field

---

### Output Format (JSON)

```json
{
  "units": [
    {
      "id": "unit_1",
      "category": "SEQUENCE",
      "original": "Check pwd before rm-rf",
      "normalized": "sequence: check pwd → rm-rf",
      "quote": "Check pwd before rm-rf",
      "location": "line 15"
    },
    {
      "id": "unit_2",
      "category": "PROHIBITION",
      "original": "MUST NOT run A before B",
      "normalized": "prohibited: [sequence: A → B]",
      "quote": "MUST NOT run A before B",
      "location": "line 23"
    }
  ],
  "metadata": {
    "total_units": 2,
    "by_category": {
      "SEQUENCE": 1,
      "PROHIBITION": 1
    }
  }
}
```

---

### Testability Classification

After extracting units, classify each as behaviorally testable or not:

| Category | Testable? | Rationale |
|----------|-----------|-----------|
| REQUIREMENT | Yes | Core behavior to verify |
| PROHIBITION | Yes | Verify forbidden action is not taken |
| CONDITIONAL | Yes | Test both branches (condition met / not met) |
| SEQUENCE | Yes | Verify ordering constraint |
| DEPENDENCY | Yes | Verify Y fails or degrades without X |
| EXCLUSION | Yes | Verify mutual exclusivity |
| CONSEQUENCE | Yes | Verify X triggers Y |
| CONJUNCTION | No | ALL-of requirements — tested via individual REQUIREMENT units |
| REFERENCE | No | Cross-document pointer — no behavior to test |

---

## Section 2: Semantic Comparison Algorithm

### Input

Two sets of extracted semantic units (from Section 1 above):
- **Document A (Original):** Extracted units from the pre-compression skill
- **Document B (Compressed):** Extracted units from the compressed skill

---

### Comparison Algorithm

#### Step 1: Build Normalized Index

For each document, create a map of normalized forms:
```
Doc A: { "sequence: check pwd → rm-rf": unit_1, ... }
Doc B: { "sequence: check pwd → rm-rf": unit_3, ... }
```

#### Step 2: Match Units

For each normalized form in Doc A:
- If exists in Doc B → **PRESERVED**
- If not in Doc B → **LOST**

For each normalized form in Doc B:
- If not in Doc A → **ADDED**

#### Step 3: Classify Severity for Each LOST Unit

| Severity | Categories |
|----------|-----------|
| **HIGH** | PROHIBITION, REQUIREMENT, CONDITIONAL, EXCLUSION |
| **MEDIUM** | CONSEQUENCE, DEPENDENCY, SEQUENCE |
| **LOW** | CONJUNCTION, REFERENCE |

**Special rule:** EXCLUSION units are always HIGH.

#### Step 4: Determine Gate Decision

```
If any LOST unit has severity HIGH:
    Gate decision = FAIL → retry compression immediately (mark lost unit text as protected)
If all LOST units are MEDIUM or LOW:
    Gate decision = WARN → proceed to SPRT re-benchmark
If no LOST units:
    Gate decision = PASS → proceed to SPRT re-benchmark
```

**Note:** ADDED units are informational only. A document can pass the gate even with additions (compressed
doc adding clarifications is acceptable). Only LOST units trigger the gate.

---

### Matching Rules

#### Semantic Equivalence (Not Exact String Match)

Compare units by **semantic meaning**, not literal string matching.

**Equivalence requires ALL of:**
1. **Same category** — Both must be same category (SEQUENCE, PROHIBITION, etc.)
2. **Same strength** — `must:` ≠ `require:` ≠ `should:`
3. **Same semantic intent** — The constraint/requirement means the same thing
4. **Same target** — Entity being acted on must match semantically
5. **Same relationship structure** — Order and connections must match

**Examples of EQUIVALENT units:**
- `required: validate credentials` ≈ `required: check auth` (synonym action, same target domain)
- `sequence: verify input → process data` ≈ `sequence: validate input → handle data` (synonym verbs)
- `prohibited: skip validation` ≈ `prohibited: bypass validation step` (same constraint)

**Examples of NOT EQUIVALENT:**
- `must: X` ≠ `require: X` (different strength)
- `sequence: A → B` ≠ `sequence: B → A` (different order)
- `prohibited: X` ≠ `required: X` (opposite meaning)

#### Double Negation Equivalence

**Prohibition of omission equals requirement:**
- `prohibit: skip validation` ≈ `require: validate` (same semantic intent)

**When category changes but meaning is preserved, units are EQUIVALENT:**
- Original: "MUST NOT skip validation" → `must not: skip validation`
- Compressed: "MUST validate" → `must: validate`
- Result: **EQUIVALENT** (same constraint, different framing)

---

### Severity Classification Summary

Use this table when identifying which LOST units to mark as protected:

| Category | Severity | Action on LOSS |
|----------|----------|----------------|
| PROHIBITION | HIGH | Immediately retry compression; mark as protected |
| REQUIREMENT | HIGH | Immediately retry compression; mark as protected |
| CONDITIONAL | HIGH | Immediately retry compression; mark as protected |
| EXCLUSION | HIGH | Immediately retry compression; mark as protected |
| CONSEQUENCE | MEDIUM | Proceed to SPRT (warn only) |
| DEPENDENCY | MEDIUM | Proceed to SPRT (warn only) |
| SEQUENCE | MEDIUM | Proceed to SPRT (warn only) |
| CONJUNCTION | LOW | Informational only |
| REFERENCE | LOW | Informational only |

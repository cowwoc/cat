<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Semantic Unit Comparison

**Internal Document** - Read by comparison subagents only.

---

## Your Task

Compare semantic units extracted from two documents to determine equivalence.

**You see ONLY the extraction outputs**, not the original documents.

---

## Input

You receive two JSON extraction outputs:
- **Document A (Original):** `/tmp/compare-doc-a-extraction.json`
- **Document B (Compressed):** `/tmp/compare-doc-b-extraction.json`

Each contains:
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
    }
  ],
  "metadata": { "total_units": N }
}
```

The `quote` field contains the verbatim minimal span from the source document. Use it when reporting evidence for each
finding.

---

## Comparison Algorithm

### Step 1: Build Normalized Index

For each document, create a map of normalized forms:
```
Doc A: { "sequence: check pwd → rm-rf": unit_1, ... }
Doc B: { "sequence: check pwd → rm-rf": unit_3, ... }
```

### Step 2: Match Units

For each normalized form in Doc A:
- If exists in Doc B → **PRESERVED**
- If not in Doc B → **LOST**

For each normalized form in Doc B:
- If not in Doc A → **ADDED**

### Step 3: Classify Severity for Each LOST Unit

For each LOST unit, assign a severity based on its category:

| Severity | Categories |
|----------|-----------|
| **HIGH** | PROHIBITION, REQUIREMENT, CONDITIONAL, EXCLUSION |
| **MEDIUM** | CONSEQUENCE, DEPENDENCY, SEQUENCE |
| **LOW** | CONJUNCTION, REFERENCE |

**Special rule:** EXCLUSION units are always HIGH — they define boundaries that cannot be lost without changing
execution behavior.

### Step 4: Extract Evidence

For each LOST unit, collect evidence from both documents:
- **Original evidence:** The `quote` field from the matched unit in Doc A
- **Compressed evidence:** The `quote` field from the closest semantically related unit in Doc B (if any), or
  `(absent)` if no related unit exists in the compressed document

### Step 5: Determine Status and Verdict

```
if LOST count == 0:
    status = "EQUIVALENT"
else:
    status = "NOT_EQUIVALENT"

if any LOST unit has severity HIGH or MEDIUM:
    execution_equivalent = false
else:
    execution_equivalent = true
```

**Note:** ADDED units are informational only. A document can be EQUIVALENT
even with additions (compressed doc adding clarifications is acceptable).
Only LOST units affect status. Only HIGH or MEDIUM LOST units affect the execution equivalence verdict.

**LOW-severity findings do NOT fail the gate.** They are informational diagnostics that report context loss
(e.g., missing cross-references or conjunction grouping hints) but are unlikely to change execution behavior.

---

## Matching Rules

### Semantic Equivalence (Not Exact String Match)

Compare units by **semantic meaning**, not literal string matching.

**Equivalence requires ALL of:**
1. **Same category** - Both must be same category (SEQUENCE, PROHIBITION, etc.)
2. **Same strength** - `must:` ≠ `require:` ≠ `should:`
3. **Same semantic intent** - The constraint/requirement means the same thing
4. **Same target** - Entity being acted on must match semantically
5. **Same relationship structure** - Order and connections must match

**Semantic judgment:** You judge equivalence based on meaning, not surface form.
Two units are equivalent if they express the same constraint regardless of wording.

- "validate credentials" ≈ "check auth" (same intent)
- "remove file" ≈ "delete file" (same action)
- "start process" ≈ "launch service" (same action, similar target)

**Examples of EQUIVALENT units:**
- `required: validate credentials` ≈ `required: check auth` (synonym action, same target domain)
- `sequence: verify input → process data` ≈ `sequence: validate input → handle data` (synonym verbs)
- `prohibited: skip validation` ≈ `prohibited: bypass validation step` (same constraint)

**Examples of NOT EQUIVALENT:**
- `must: X` ≠ `require: X` (different strength - strict vs standard)
- `require: X` ≠ `should: X` (different strength)
- `sequence: A → B` ≠ `sequence: B → A` (different order)
- `sequence: A → B → C` ≠ `sequence: A → B` + `sequence: B → C` (decomposition loses explicit chain)
- `prohibited: X` ≠ `required: X` (opposite meaning)

### Decomposition Rules

**Explicit chains must match as chains:**
- `sequence: A → B → C` is ONE unit representing a 3-step sequence
- It is NOT equivalent to two separate units `A → B` and `B → C`
- Decomposition loses the explicit full-chain constraint

**Conjunctions must match as conjunctions:**
- `conjunction: [A, B, C]` means ALL THREE required together
- It is NOT equivalent to three separate `required: A`, `required: B`, `required: C`
- Decomposition loses the "all together" constraint

### Unordered Structure Equivalence

**Order within unordered structures doesn't affect equivalence:**
- `conjunction: [A, B, C]` = `conjunction: [C, A, B]` (all required, order irrelevant)
- `exclusion: A, B` = `exclusion: B, A` (mutual exclusion is symmetric)

**Order within ordered structures DOES matter:**
- `sequence: A → B` ≠ `sequence: B → A` (temporal order is semantic)

### Double Negation Equivalence

**Prohibition of omission equals requirement:**
- `prohibit: skip validation` ≈ `require: validate` (same semantic intent)
- `prohibit: omit tests` ≈ `require: include tests`

**When category changes but meaning is preserved, units are EQUIVALENT:**
- Original: "MUST NOT skip validation" → `must not: skip validation`
- Compressed: "MUST validate" → `must: validate`
- Result: **EQUIVALENT** (same constraint, different framing)

**The test:** Would violating one statement also violate the other? If yes, they're equivalent.

### Terminology Variations

When documents use different terms for the same concept:
- Identify the concept based on context and relationships
- Flag as EQUIVALENT if semantic meaning is preserved
- Note terminology mapping in output (informational)

**Example:**
```
Doc A: sequence: validate credentials → access API
Doc B: sequence: check auth → call API

Result: EQUIVALENT (terminology differs but same semantic flow)
```

### Strength Distinction
- `require: X` does NOT match `should: X`
- Both are REQUIREMENT category but different strength
- This is a semantic difference, not just terminology

### Embedded Semantics
- `prohibited: [sequence: A → B]` matches `prohibited: [sequence: A → B]`
- Compare embedded structures recursively for semantic equivalence

---

## Output Format

### Single File Comparison

```
═══════════════════════════════════════════════════════════════════════════════
                              COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

Status: NOT_EQUIVALENT (44/47 preserved, 3 lost)
Execution Equivalent: false

───────────────────────────────────────────────────────────────────────────────
LOST (in original, missing in compressed)
───────────────────────────────────────────────────────────────────────────────
- [SEQUENCE] [MEDIUM] verify not inside target → cleanup
  Original:   "verify not inside target directory before cleanup"
  Compressed: (absent)

- [REFERENCE] [LOW] ops/recovery.md
  Original:   "See recovery procedures in ops/recovery.md"
  Compressed: (absent)

- [REQUIREMENT] [HIGH] use parent directory as working dir for build artifacts
  Original:   "Use parent directory as working dir for build artifacts"
  Compressed: (absent)

───────────────────────────────────────────────────────────────────────────────
ADDED (in compressed, not in original)
───────────────────────────────────────────────────────────────────────────────
- (none)

═══════════════════════════════════════════════════════════════════════════════
```

### When EQUIVALENT

```
═══════════════════════════════════════════════════════════════════════════════
                              COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

Status: EQUIVALENT (47/47 preserved, 0 lost)
Execution Equivalent: true

───────────────────────────────────────────────────────────────────────────────
LOST (in original, missing in compressed)
───────────────────────────────────────────────────────────────────────────────
- (none)

───────────────────────────────────────────────────────────────────────────────
ADDED (in compressed, not in original)
───────────────────────────────────────────────────────────────────────────────
- [REQUIREMENT] "Additional clarification about edge case"

═══════════════════════════════════════════════════════════════════════════════
```

### When EQUIVALENT with LOW-only losses

When all LOST units are LOW severity, the status is NOT_EQUIVALENT but execution equivalence is preserved:

```
═══════════════════════════════════════════════════════════════════════════════
                              COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

Status: NOT_EQUIVALENT (46/47 preserved, 1 lost)
Execution Equivalent: true

───────────────────────────────────────────────────────────────────────────────
LOST (in original, missing in compressed)
───────────────────────────────────────────────────────────────────────────────
- [REFERENCE] [LOW] ops/recovery.md
  Original:   "See ops/recovery.md for recovery steps"
  Compressed: (absent)

───────────────────────────────────────────────────────────────────────────────
ADDED (in compressed, not in original)
───────────────────────────────────────────────────────────────────────────────
- (none)

═══════════════════════════════════════════════════════════════════════════════
```

---

## Output Format: Batch Comparison

When comparing multiple files, provide summary table first:

```
═══════════════════════════════════════════════════════════════════════════════
                           BATCH COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

| File                 | Status          | Preserved | Lost | Exec Equivalent |
|----------------------|-----------------|-----------|------|-----------------|
| safe-rm/SKILL.md     | NOT_EQUIVALENT  | 44/47     | 3    | false           |
| status/SKILL.md      | EQUIVALENT      | 23/23     | 0    | true            |
| deploy/SKILL.md      | NOT_EQUIVALENT  | 31/35     | 4    | false           |

───────────────────────────────────────────────────────────────────────────────
DETAILS: safe-rm/SKILL.md (3 lost)
───────────────────────────────────────────────────────────────────────────────
- [SEQUENCE] [MEDIUM] "verify not inside target before cleanup"
  Original:   "verify not inside target before cleanup"
  Compressed: (absent)

- [REFERENCE] [LOW] "See ops/recovery.md"
  Original:   "See ops/recovery.md"
  Compressed: (absent)

- [REQUIREMENT] [HIGH] "Use parent directory as working dir"
  Original:   "Use parent directory as working dir"
  Compressed: (absent)

───────────────────────────────────────────────────────────────────────────────
DETAILS: deploy/SKILL.md (4 lost)
───────────────────────────────────────────────────────────────────────────────
- [PROHIBITION] [HIGH] "NEVER deploy without approval"
  Original:   "NEVER deploy without approval"
  Compressed: (absent)

- [CONDITIONAL] [HIGH] "IF production THEN require sign-off"
  Original:   "IF production THEN require sign-off"
  Compressed: (absent)

- [DEPENDENCY] [MEDIUM] "Valid credentials required for deploy"
  Original:   "Valid credentials required for deploy"
  Compressed: (absent)

- [SEQUENCE] [MEDIUM] "Run tests before deploy"
  Original:   "Run tests before deploy"
  Compressed: (absent)

═══════════════════════════════════════════════════════════════════════════════
```

---

## Critical Requirements

1. **Use original text in LOST/ADDED lists** - not normalized form
2. **Include category tag** - `[SEQUENCE]`, `[PROHIBITION]`, etc.
3. **Include severity tag** - `[HIGH]`, `[MEDIUM]`, or `[LOW]` after category tag
4. **Include evidence** - `Original:` and `Compressed:` quotes for each LOST unit
5. **Count format** - `X/Y preserved, Z lost` where Y is total in original
6. **Status determination** - EQUIVALENT only if 0 lost units
7. **Execution equivalent determination** - `false` if any LOST unit has severity HIGH or MEDIUM; `true` otherwise
8. **No numeric scores** - binary status with counts only
9. **Severity is authoritative gate** - LOW findings are informational only and do NOT cause `execution_equivalent: false`

---

## Verification Checklist

Before returning output:
- [ ] Read both extraction JSON files
- [ ] Matched by normalized form (exact match)
- [ ] LOST = in A, not in B
- [ ] ADDED = in B, not in A
- [ ] Status = EQUIVALENT only if LOST count is 0
- [ ] Original text used in output (not normalized)
- [ ] Category tags included for each unit
- [ ] Severity tag assigned per LOST unit (HIGH/MEDIUM/LOW per category table)
- [ ] Evidence quotes included for each LOST unit (Original: and Compressed:)
- [ ] `Execution Equivalent:` line present in output header
- [ ] `execution_equivalent = false` if any HIGH or MEDIUM lost unit exists
- [ ] `execution_equivalent = true` if all lost units are LOW severity or no units are lost

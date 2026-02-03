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
      "location": "line 15"
    }
  ],
  "metadata": { "total_units": N }
}
```

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

### Step 3: Determine Status

```
if LOST count == 0:
    status = "EQUIVALENT"
else:
    status = "NOT_EQUIVALENT"
```

**Note:** ADDED units are informational only. A document can be EQUIVALENT
even with additions (compressed doc adding clarifications is acceptable).
Only LOST units cause NOT_EQUIVALENT status.

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
- `required!: X` ≠ `required: X` (different strength - strict vs advisory)
- `required: X` ≠ `suggested: X` (different strength)
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
- `required: X` does NOT match `suggested: X`
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

───────────────────────────────────────────────────────────────────────────────
LOST (in original, missing in compressed)
───────────────────────────────────────────────────────────────────────────────
- [SEQUENCE] "When cleaning up test files: verify not inside target"
- [REFERENCE] "See recovery procedures in ops/recovery.md"
- [REQUIREMENT] "Use parent directory as working dir for build artifacts"

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

---

## Output Format: Batch Comparison

When comparing multiple files, provide summary table first:

```
═══════════════════════════════════════════════════════════════════════════════
                           BATCH COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

| File                 | Status          | Preserved | Lost |
|----------------------|-----------------|-----------|------|
| safe-rm/SKILL.md     | NOT_EQUIVALENT  | 44/47     | 3    |
| status/SKILL.md      | EQUIVALENT      | 23/23     | 0    |
| deploy/SKILL.md      | NOT_EQUIVALENT  | 31/35     | 4    |

───────────────────────────────────────────────────────────────────────────────
DETAILS: safe-rm/SKILL.md (3 lost)
───────────────────────────────────────────────────────────────────────────────
- [SEQUENCE] "verify not inside target before cleanup"
- [REFERENCE] "See ops/recovery.md"
- [REQUIREMENT] "Use parent directory as working dir"

───────────────────────────────────────────────────────────────────────────────
DETAILS: deploy/SKILL.md (4 lost)
───────────────────────────────────────────────────────────────────────────────
- [PROHIBITION] "NEVER deploy without approval"
- [CONDITIONAL] "IF production THEN require sign-off"
- [DEPENDENCY] "Valid credentials required for deploy"
- [SEQUENCE] "Run tests before deploy"

═══════════════════════════════════════════════════════════════════════════════
```

---

## Critical Requirements

1. **Use original text in LOST/ADDED lists** - not normalized form
2. **Include category tag** - `[SEQUENCE]`, `[PROHIBITION]`, etc.
3. **Count format** - `X/Y preserved, Z lost` where Y is total in original
4. **Status determination** - EQUIVALENT only if 0 lost units
5. **No numeric scores** - binary status with counts only

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

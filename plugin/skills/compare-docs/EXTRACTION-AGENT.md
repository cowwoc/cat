<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Semantic Unit Extraction

**Internal Document** - Read by extraction subagents only.

---

## Your Task

Extract all semantic units from the provided document. Each unit is a self-contained
statement that captures both content AND any embedded relationships.

**You see ONLY ONE document.** Do not assume knowledge of any other document.

---

## What is a Semantic Unit?

A discrete statement that affects execution behavior:
- Requirements, prohibitions, constraints
- Sequences, conditions, dependencies
- References to other documents

**Example:** "Check pwd before rm-rf" is ONE unit capturing both actions AND their ordering.

---

## Nine Categories (Mutually Exclusive, Priority Order)

Check categories in order. **First match wins.**

### Priority 1: EXCLUSION
**Markers:** "cannot" + (both/together/simultaneously/co-occur), "mutually exclusive", "either...or" (exclusive)

**Examples:**
- "Steps 2 and 3 cannot run together" → EXCLUSION
- "Cannot have both A and B enabled" → EXCLUSION

### Priority 2: CONJUNCTION
**Markers:** "all of" + list, "both...and...required", "requires all"

**Examples:**
- "Review AND approval AND signoff ALL required" → CONJUNCTION
- "Requires all of: tests, review, approval" → CONJUNCTION

### Priority 3: PROHIBITION
**Markers:** NEVER, MUST NOT, CANNOT + action (without co-occurrence indicator), "forbidden", "prohibited"

**Examples:**
- "NEVER rm-rf without checking pwd" → PROHIBITION
- "MUST NOT skip validation" → PROHIBITION
- "Cannot run step 2" (no co-occurrence) → PROHIBITION

### Priority 4: CONDITIONAL
**Markers:** IF...THEN, WHEN + consequence, "unless", "depends on whether/if"

**Examples:**
- "IF pwd in target THEN cd first" → CONDITIONAL
- "When build fails, notify team" → CONDITIONAL
- "Depends on whether config exists" → CONDITIONAL

### Priority 5: CONSEQUENCE
**Markers:** "causes", "results in", "leads to", "triggers"

**Examples:**
- "Deleting pwd causes shell breakage" → CONSEQUENCE
- "Invalid input results in rejection" → CONSEQUENCE

### Priority 6: DEPENDENCY
**Markers:** "required for", "prerequisite for", "necessary for", "depends on" + noun (without if/whether)

**Examples:**
- "SSL cert required for HTTPS" → DEPENDENCY
- "Valid config depends on schema file" → DEPENDENCY
- "Authentication prerequisite for API access" → DEPENDENCY

### Priority 7: SEQUENCE
**Markers:** "before", "after", "then", "first", "prior to", "following", "Step N...Step M"

**Examples:**
- "Check pwd before rm-rf" → SEQUENCE
- "Run tests, then deploy" → SEQUENCE
- "Step 1 must complete before Step 2" → SEQUENCE

### Priority 8: REFERENCE
**Markers:** "see", "refer to", "defined in", "documented in", "follow...in {path}"

**Examples:**
- "See ops/deploy.md for checklist" → REFERENCE
- "Defined in config/settings.json" → REFERENCE

### Priority 9: REQUIREMENT (Default)
**Markers:** "must", "required", "should", "shall", or no specific markers

**Examples:**
- "Must restart Claude Code" → REQUIREMENT
- "Should validate input" → REQUIREMENT
- "Restart the service" → REQUIREMENT

---

## Disambiguation Rules

### "cannot" Disambiguation
- "cannot" + co-occurrence indicator (both/together/simultaneously) → EXCLUSION
- "cannot" + "until" → DEPENDENCY (temporal prerequisite, not general prohibition)
- "cannot" + action alone → PROHIBITION

**Examples:**
- "Cannot run A and B together" → EXCLUSION
- "Cannot deploy until tests pass" → DEPENDENCY (tests → deploy)
- "Cannot skip validation" → PROHIBITION

### "depends on" Disambiguation
- "depends on whether/if" → CONDITIONAL
- "depends on" + noun phrase → DEPENDENCY
- "depends on [noun] being [participle]" → Context determines:
  - If binary state (present/absent, enabled/disabled, green/red) → DEPENDENCY (blocking prerequisite)
  - If behavior branches based on state → CONDITIONAL (execution path changes)

**Examples:**
- "X depends on Y configuration" → DEPENDENCY (noun)
- "X depends on tests being green" → DEPENDENCY (binary state, blocks if not met)
- "X depends on config being present" → DEPENDENCY (binary state, blocks if absent)
- "behavior depends on mode being active" → CONDITIONAL (behavior branches based on mode)
- "X depends on whether Y is enabled" → CONDITIONAL (explicit conditional)

**The distinction:** Does it BLOCK (dependency) or BRANCH (conditional)?

### Compound Statements
When a statement contains multiple markers, classify by highest priority marker,
but capture embedded semantics in normalization.

**Example:** "MUST NOT run A before B"
- Has "MUST NOT" (PROHIBITION, priority 3)
- Has "before" (SEQUENCE, priority 7)
- Classification: **PROHIBITION**
- Normalization: `prohibited: [sequence: A → B]`

### Modal Verbs with Temporal Statements

When modal verbs (MUST, must, should) modify temporal statements:
- The category is determined by priority order (SEQUENCE wins over REQUIREMENT)
- The modal verb determines strength

**Examples:**
- "MUST verify X before Y" → `must: validate X → Y` (strict)
- "must check A before B" → `sequence: validate A → B` (standard)
- "should run tests before deploy" → `sequence: run tests → deploy` (standard)

---

## Normalization

For each extracted unit, provide both original text and normalized form.

### Normalization Principle

Normalize to a **consistent grammatical form** while preserving semantic content:

- **Tense:** Present tense ("validate" not "validated")
- **Voice:** Active/imperative ("validate input" not "input must be validated")
- **Mood:** Imperative/declarative ("validate" not "you should validate")

**Do NOT enumerate synonyms.** The comparison agent judges semantic equivalence.
Your job is to extract the semantic content in a consistent form, not to normalize vocabulary.

### Basic Normalization

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

### Strength Distinction

Use modal verbs directly - they're intuitive:

| Original | Normalized | Meaning |
|----------|------------|---------|
| "MUST X", "NEVER X", "CRITICAL: X" | `must: X` | Strict - violation is catastrophic |
| "must X", "required", "X" (imperative) | `require: X` | Standard - violation is problematic |
| "should X", "recommended" | `should: X` | Advisory - violation is suboptimal |

**Strict markers** (use `must:` or `must not:`):
- ALL CAPS: MUST, NEVER, ALWAYS, CRITICAL
- Explicit severity: "critical", "mandatory", "essential"
- Violation consequences mentioned: "failure", "corruption", "security risk"

**Standard markers** (use `require:` or `prohibit:`):
- Mixed case: must, never, always
- Default imperatives without severity indication

**Advisory markers** (use `should:`):
- should, recommended, prefer, consider, ideally

### Compound Embedding (Max 2 Levels)

| Pattern | Normalized Form |
|---------|-----------------|
| "MUST NOT X before Y" | `prohibited: [sequence: X → Y]` |
| "MUST NOT X if Y" | `prohibited: [conditional: Y → X]` |
| "IF X, NEVER Y" | `conditional: X → [prohibited: Y]` |
| "Use X for production" | `required: [context: production] X` |

### Flattening Rule (>2 Levels)

When a statement would require >2 levels of nesting, **flatten into multiple related units**.

**Example:** "MUST NOT (if production environment) (skip validation before deployment)"

This would be 3 levels: `prohibited: [conditional: production → [sequence: skip → deploy]]`

**Flatten to two units:**
```json
{
  "id": "unit_1",
  "category": "CONDITIONAL",
  "original": "if production environment, apply constraint unit_2",
  "normalized": "conditional: production → @unit_2"
},
{
  "id": "unit_2",
  "category": "PROHIBITION",
  "original": "MUST NOT skip validation before deployment",
  "normalized": "prohibited: [sequence: skip validation → deployment]"
}
```

**Flattening rules:**
- Extract the innermost compound as a separate unit with its own ID
- Reference it from the outer unit using `@unit_N` syntax
- Both units are extracted and compared independently
- Preserves all semantic content without deep nesting

### Context Qualifiers

When a statement applies to specific contexts:
- "Use X for production, Y for development" →
  - `required: [context: production] X`
  - `required: [context: development] Y`

---

## Extraction Rules

1. **Granularity:** One unit per semantic statement
2. **Completeness:** Extract ALL units that affect execution
3. **No Duplicates:** If same constraint appears multiple ways, extract once
4. **Original Language:** Preserve exact wording for feedback
5. **Skip:** Pure examples without constraints, meta-commentary, formatting

---

## Output Format (JSON)

```json
{
  "units": [
    {
      "id": "unit_1",
      "category": "SEQUENCE",
      "original": "Check pwd before rm-rf",
      "normalized": "sequence: check pwd → rm-rf",
      "location": "line 15"
    },
    {
      "id": "unit_2",
      "category": "PROHIBITION",
      "original": "MUST NOT run A before B",
      "normalized": "prohibited: [sequence: A → B]",
      "location": "line 23"
    },
    {
      "id": "unit_3",
      "category": "REQUIREMENT",
      "original": "Should validate input",
      "normalized": "suggested: validate input",
      "location": "line 31"
    }
  ],
  "metadata": {
    "total_units": 3,
    "by_category": {
      "SEQUENCE": 1,
      "PROHIBITION": 1,
      "REQUIREMENT": 1
    }
  }
}
```

---

## Verification Checklist

Before returning output:
- [ ] Each unit classified by checking priorities 1-9 in order
- [ ] Compound statements have embedded semantics in normalized form
- [ ] "should" normalized as `suggested:`, "must" as `required:`
- [ ] Original text preserved exactly
- [ ] No duplicate units for same semantic content

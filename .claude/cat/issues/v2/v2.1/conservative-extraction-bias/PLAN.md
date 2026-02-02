# Plan: conservative-extraction-bias

## Problem
The compare-docs extraction agents show ±10-35% variance in relationship classification, particularly
for temporal dependencies. Independent runs on the same document classified the same 4 temporal
relationships differently (explicit vs implicit), causing score swings from 0.64 to 1.0.

The root cause: EXTRACTION-AGENT.md lacks explicit guidance on how to handle ambiguous cases.
Different agents make different judgment calls.

## Satisfies
None - infrastructure improvement for validation reliability

## Root Cause
EXTRACTION-AGENT.md (lines 94-125) defines relationship types but doesn't specify:
1. When to classify temporal dependencies from examples as explicit relationships
2. What to do when relationship classification is ambiguous
3. The conservative principle: if removing text could affect execution, keep it

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** May extract more relationships, causing false negatives (score drops)
- **Mitigation:** Conservative bias is the intended behavior - better to flag potential issues than miss them

## Files to Modify
- `plugin/skills/compare-docs/EXTRACTION-AGENT.md` - Add conservative extraction principle
- `plugin/skills/shrink-doc/COMPRESSION-AGENT.md` - Add clarity improvement principle

## Proposed Changes

### Part A: Extraction Agent (conservative when reading)

### A1. Add Conservative Extraction Principle (new section after "Your Task")

```markdown
## Conservative Extraction Principle

**When in doubt, extract it.**

If removing text could realistically lead to a loss of execution equivalence, treat it as:
- An explicit claim (not implicit)
- An explicit relationship (not derivable from context)

This bias is intentional: it's better to flag potential semantic loss than to miss it.

**Apply this principle when:**
- Temporal ordering is shown via example but not stated as a rule
- A relationship could be "derived" from other claims but is also stated explicitly
- Examples illustrate constraints that aren't repeated in abstract form
```

### A2. Update Temporal Dependency Extraction (enhance existing section)

Add to "### 1. Temporal Dependencies":

```markdown
**Conservative Classification:**
- If an example shows step ordering (e.g., "Run A, then B, then C"), extract as explicit temporal
  relationship even if ordering seems "obvious" from context
- If ordering constraints appear in BOTH abstract rules AND concrete examples, extract BOTH
- Err on the side of explicit: an "unnecessary" relationship is better than a missed one

**Example:**
Document says: "Always validate input before processing"
Later shows: "Example: validate(data); process(data);"

Extract: temporal relationship "validate → process" as EXPLICIT (not "derivable from rule")
Rationale: The example reinforces the constraint; removing it could reduce clarity
```

### A3. Add Classification Disambiguation Table

Add to relationship section:

```markdown
**Ambiguous Cases - Always Choose Explicit:**

| Scenario | Classification | Rationale |
|----------|---------------|-----------|
| Example shows ordering | explicit temporal | Example provides execution guidance |
| Rule + example both show constraint | extract BOTH | Redundancy is intentional emphasis |
| "Obvious" from domain knowledge | explicit | Agents lack domain expertise |
| Could be derived from other claims | explicit | Derivation may fail |
```

---

### Part B: Compression Agent (clarify when writing)

### B1. Add Clarity Improvement Principle (new section after "Goal")

```markdown
## Clarity Improvement Principle

**Improve clarity, don't just preserve content.**

When compressing, actively reduce ambiguity by:
- Converting implicit temporal ordering to explicit statements
- Normalizing vague language to precise constraints
- Making relationships explicit even if "derivable" from context
- Replacing negative instructions ("don't do X") with positive actionable ones ("do Y")

This reduces extraction variance during validation and improves execution equivalence.

**Apply this principle when:**
- Text uses vague ordering ("then", "next") - clarify to explicit "Step N before Step M"
- Examples show constraints without stating them - add explicit constraint statement
- Relationships are implied but not stated - state them explicitly
```

### B2. Add Normalization Techniques (new section)

Add after "Compression Approach":

```markdown
## Normalization for Clarity

When compressing, apply these normalizations to reduce ambiguity:

**Temporal Ordering:**
- BEFORE: "Run the tests, then deploy"
- AFTER: "Run tests before deployment" (explicit temporal marker)

**Implicit Constraints:**
- BEFORE: Example shows `validate(); process();` without explanation
- AFTER: "Validate before processing (required ordering)"

**Vague Quantifiers:**
- BEFORE: "Wait a bit before retrying"
- AFTER: "Wait before retrying" or remove if no constraint intended

**Conditional Clarity:**
- BEFORE: "You might want to check X"
- AFTER: "Check X if [condition]" or remove if optional

**Negative → Positive:**
- BEFORE: "Don't skip validation"
- AFTER: "Validate before proceeding" (actionable positive instruction)

- BEFORE: "Never deploy without testing"
- AFTER: "Test before deploying" (same constraint, positive framing)

**Principle:** If the original text conveys a constraint (even implicitly), the compressed
version should state that constraint MORE explicitly, not less. Prefer positive actionable
instructions over negative prohibitions when the positive form is equally clear.
```

### B3. Add Anti-Pattern Examples

```markdown
## Compression Anti-Patterns

**DO NOT reduce clarity when compressing:**

| Original | Bad Compression | Good Compression |
|----------|-----------------|------------------|
| "Always run A before B" | "Run A and B" | "Run A before B" (preserved) |
| "Step 1, then Step 2, then Step 3" | "Complete all steps" | "Step 1 → Step 2 → Step 3" |
| Example: `init(); start();` | (removed) | "Initialize before starting" |
| "MUST validate OR reject" | "Handle input" | "Validate input; reject if invalid" |
| "Don't skip the tests" | "Don't skip tests" | "Run tests before proceeding" |
| "Never commit without review" | (kept as-is) | "Get review before committing" |

**Key insight:** Compression should reduce TOKENS, not CONSTRAINTS.
```

## Acceptance Criteria

**Extraction Agent:**
- [ ] Conservative extraction principle documented
- [ ] Temporal dependency section updated with conservative bias
- [ ] Disambiguation table added for ambiguous cases

**Compression Agent:**
- [ ] Clarity improvement principle documented
- [ ] Normalization techniques section added
- [ ] Anti-pattern examples added

**Validation:**
- [ ] No test regressions (python3 /workspace/run_tests.py passes)

## Execution Steps

### Extraction Agent (EXTRACTION-AGENT.md)

1. **Step 1:** Add "Conservative Extraction Principle" section after "Your Task"
   - Verify: Section exists and states "when in doubt, extract it"

2. **Step 2:** Update temporal dependency extraction with conservative classification guidance
   - Verify: Examples section shows explicit extraction for ordering

3. **Step 3:** Add disambiguation table for ambiguous cases
   - Verify: Table covers example ordering, rule+example redundancy, domain knowledge, derivation

### Compression Agent (COMPRESSION-AGENT.md)

4. **Step 4:** Add "Clarity Improvement Principle" section after "Goal"
   - Verify: Section states "improve clarity, don't just preserve content"

5. **Step 5:** Add "Normalization for Clarity" section after "Compression Approach"
   - Verify: Includes temporal ordering, implicit constraints, vague quantifiers examples

6. **Step 6:** Add "Compression Anti-Patterns" section
   - Verify: Shows bad vs good compression examples with constraint preservation

### Validation

7. **Step 7:** Run tests to verify no regressions
   - Verify: `python3 /workspace/run_tests.py` passes

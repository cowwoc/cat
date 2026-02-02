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

## Proposed Changes

### 1. Add Conservative Extraction Principle (new section after "Your Task")

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

### 2. Update Temporal Dependency Extraction (enhance existing section)

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

### 3. Add Classification Disambiguation Table

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

## Acceptance Criteria
- [ ] Conservative extraction principle documented
- [ ] Temporal dependency section updated with conservative bias
- [ ] Disambiguation table added for ambiguous cases
- [ ] No test regressions (existing extractions still work)

## Execution Steps
1. **Step 1:** Add "Conservative Extraction Principle" section after "Your Task"
   - Verify: Section exists and states "when in doubt, extract it"

2. **Step 2:** Update temporal dependency extraction with conservative classification guidance
   - Verify: Examples section shows explicit extraction for ordering

3. **Step 3:** Add disambiguation table for ambiguous cases
   - Verify: Table covers example ordering, rule+example redundancy, domain knowledge, derivation

4. **Step 4:** Run existing tests to verify no regressions
   - Verify: `python3 /workspace/run_tests.py` passes

# Plan: add-rca-cause-signatures

## Goal

Improve recurrence detection in RCA mistake tracking by adding structured cause signatures that link mistakes with the same root cause even when they manifest differently across tools or contexts. This reduces under-counting of recurrences and improves accuracy of the RCA A/B test metrics.

## Parent Requirements

None

## Approaches

### A: Enum-based Cause Signatures (Recommended)
- **Risk:** LOW
- **Scope:** 3-4 files (minimal)
- **Description:** Add a controlled-vocabulary `cause_signature` field combining cause type, barrier type, and context. Prevents fingerprint drift from wording variations while remaining human-readable.

### B: Free-text Fingerprints
- **Risk:** HIGH
- **Scope:** 3-4 files (minimal)
- **Description:** Use MD5 hashes or free-text signatures. Creates maintenance burden and fingerprint drift as language varies slightly between mistakes.

> Approach A selected: structured enums prevent drift and remain maintainable long-term.

## Research Findings

MAST research developed an LLM-as-Judge pipeline for scalable failure pattern annotation. Empirical bug study of Logic Bugs found 22-30% of failures are recurring across different tool contexts (e.g., same missing barrier manifesting in different hook contexts). Structured enum signatures prevent drift compared to free-text fingerprints, improving pattern detection accuracy.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Field is additive; existing records without signature treated as unclassified. Must not break existing mistake recording workflow.
- **Mitigation:** Field is optional. Workflow prompts for linkage only when signature matches existing entries. Test with existing mistake records to verify backward compatibility.

## Files to Modify

- `plugin/skills/learn/SKILL.md` - Update Step 3 (RCA process) to capture and compare cause_signature
- `plugin/skills/learn/rca-methods.md` - Document signature format and vocabulary
- `plugin/skills/learn/RCA-AB-TEST.md` - Update tracking fields section to include cause_signature field
- `plugin/skills/learn/first-use.md` - Update mistake recording instructions with cause_signature example
- `.cat/mistakes/mistakes-YYYY-MM.json` (test fixture) - Add cause_signature examples

## Pre-conditions

- [ ] All dependent issues are closed

## Main Agent Waves

(None - no skills that spawn subagents)

## Sub-Agent Waves

### Wave 1

- Define cause_signature enum vocabulary
  - Files: `plugin/skills/learn/rca-methods.md`
  - Create controlled list of `<cause_type>:<barrier_type>:<context>` values (e.g., `compliance_failure:hook_absent:pre_tool_use`, `knowledge_gap:doc_missing:plugin_rules`)
  - Document rationale for each combination

- Update learn skill SKILL.md to capture and compare signatures
  - Files: `plugin/skills/learn/SKILL.md`
  - Step 3: After RCA analysis, prompt for cause_signature selection from enum
  - Add signature comparison logic: before recording new mistake, check if signature matches any existing entries
  - If match found, prompt for recurrence_of linkage

- Update RCA-AB-TEST.md to document the tracking field
  - Files: `plugin/skills/learn/RCA-AB-TEST.md`
  - Add cause_signature to "Tracking Fields" section with example values
  - Document that field is optional (backward compatible)

- Update first-use.md with signature instructions
  - Files: `plugin/skills/learn/first-use.md`
  - Add example showing cause_signature in JSON output
  - Explain how matching signatures improve recurrence detection

### Wave 2

- End-to-end test: record two mistakes with same cause_signature
  - Files: (test implementation)
  - Record first mistake with signature `compliance_failure:hook_absent:pre_tool_use`
  - Record second mistake with same signature
  - Verify workflow prompts for recurrence_of linkage on second entry

## Post-conditions

- [ ] cause_signature field added to mistakes JSON schema (documented in rca-methods.md and RCA-AB-TEST.md)
- [ ] Signature format is structured enum-based: `<cause_type>:<barrier_type>:<context>` with controlled vocabulary documented in rca-methods.md
- [ ] Learn skill Step 3 includes: (1) signature selection from enum, (2) comparison against existing entries, (3) prompt for recurrence_of linkage if match found
- [ ] RCA-AB-TEST.md updated with cause_signature in "Tracking Fields" section
- [ ] Existing mistake records without cause_signature treated as unclassified (no migration required, field is additive)
- [ ] All existing mistake recording tests pass (backward compatibility verified)
- [ ] E2E: Two mistakes recorded with same cause_signature; second prompts for recurrence_of linkage

# Plan: enhance-optimize-doc-iteration

## Goal
Enhance optimize-doc-agent (formerly optimize-doc-agent) with root cause categorization on validation failure and
targeted re-compression, using compare-docs' binary execution-equivalence verdict as the pass/fail gate and its
diagnostic metadata (severity, evidence) to guide intelligent retry.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Retry logic must not loop indefinitely; targeted re-compression must not introduce inconsistencies
  between re-compressed sections and untouched sections
- **Mitigation:** Cap retry iterations (max 2 targeted retries after initial failure); re-compression operates on
  section boundaries only; binary verdict from compare-docs is the sole gate — no override or degraded-pass mode

## Dependencies
- `2.1-enhance-compare-docs-grading` — requires severity classification and evidence fields in compare-docs output
  to enable root cause categorization and targeted retry
- `2.1-rename-shrink-doc-to-optimize-doc` — skill directories and references must be renamed first

## Files to Modify
- `plugin/skills/optimize-doc-agent/SKILL.md` — update to load first-use.md via skill-loader (currently has no
  first-use.md)
- `plugin/skills/optimize-doc-agent/first-use.md` — new file: complete optimize-doc methodology with root cause
  categorization, targeted re-compression, and iteration workflow
- `plugin/concepts/optimize-doc-iteration.md` — new concept doc: compression failure patterns, root cause taxonomy,
  targeted retry strategies

## Pre-conditions
- [ ] `2.1-rename-shrink-doc-to-optimize-doc` is closed (skill directories renamed)
- [ ] `2.1-enhance-compare-docs-grading` is closed (severity and evidence fields available in compare-docs output)

## Sub-Agent Waves

### Wave 1: Root Cause Categorization
- Create `plugin/skills/optimize-doc-agent/first-use.md` with complete optimize-doc methodology:
  - Core compression workflow: compress → validate via compare-docs → check binary verdict
  - When `execution_equivalent: false`, categorize root causes using compare-docs diagnostics:
    - **Over-aggressive merging**: Multiple semantic units collapsed into one, losing distinctions
    - **Dropped qualifiers**: Conditional logic, strength distinctions (must vs. should), or scope boundaries removed
    - **Lost prohibitions**: Negative constraints ("do NOT", "never") omitted during compression
    - **Flattened compound semantics**: Nested conditions or multi-level embeddings simplified beyond recognition
    - **Broken references**: Cross-references to other sections or documents invalidated by restructuring
  - Map each compare-docs HIGH/MEDIUM finding to a root cause category using the evidence quotes
  - Files: `plugin/skills/optimize-doc-agent/first-use.md`

### Wave 2: Targeted Re-Compression
- Add targeted retry workflow to first-use.md:
  - On validation failure, identify which document sections contain the failing semantic units (using evidence
    quotes and location data from compare-docs)
  - Re-compress only those sections, preserving the rest of the compressed document unchanged
  - Apply root-cause-specific constraints during retry:
    - Over-aggressive merging → keep units separate, compress wording only
    - Dropped qualifiers → preserve all conditional/strength markers verbatim
    - Lost prohibitions → treat negative constraints as immutable during compression
    - Flattened compounds → preserve nesting structure, compress leaf content only
    - Broken references → preserve all cross-reference targets verbatim
  - Re-validate with compare-docs after each targeted retry
  - Binary verdict (`execution_equivalent`) is the sole gate — no partial credit
  - Maximum 2 targeted retries after initial failure; if still failing, report the remaining findings and stop
  - Files: `plugin/skills/optimize-doc-agent/first-use.md`

### Wave 3: Documentation and Verification
- Create `plugin/concepts/optimize-doc-iteration.md`:
  - Compression failure pattern taxonomy with examples
  - Root cause → retry strategy mapping table
  - Diminishing returns guidance: when to stop compressing
  - Binary gate principle: execution equivalence is non-negotiable
  - Files: `plugin/concepts/optimize-doc-iteration.md`
- Update SKILL.md if needed to reference first-use.md properly
  - Files: `plugin/skills/optimize-doc-agent/SKILL.md`
- Run `mvn -f client/pom.xml test` and confirm all tests pass
  - Files: (build verification)

## Post-conditions
- [ ] optimize-doc-agent has a first-use.md with complete compression methodology
- [ ] Validation failures are categorized into root cause types using compare-docs severity/evidence
- [ ] Targeted re-compression retries only failing sections with root-cause-specific constraints
- [ ] Maximum 2 targeted retries; stops and reports if still failing
- [ ] Binary verdict (`execution_equivalent`) from compare-docs is the sole pass/fail gate — no override
- [ ] `plugin/concepts/optimize-doc-iteration.md` documents failure patterns and retry strategies
- [ ] `mvn -f client/pom.xml test` exits 0 with no regressions

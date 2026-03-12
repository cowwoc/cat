# Plan: enhance-compare-docs-grading

## Goal
Enhance compare-docs-agent with severity classification, evidence extraction, and root cause categorization while
preserving a binary execution-equivalence verdict as the final gate output. Richer diagnostics help debug and iterate;
the pass/fail decision remains unambiguous.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Adding intermediate analysis must not weaken or complicate the binary gate; severity classification
  must be deterministic and not introduce subjectivity into the final verdict
- **Mitigation:** Binary verdict is computed independently from diagnostic metadata — any unit marked MISSING or
  CHANGED with semantic impact fails the gate regardless of severity; diagnostics are advisory output alongside
  the verdict, not inputs to it

## Files to Modify
- `plugin/skills/compare-docs-agent/COMPARISON-AGENT.md` — add severity classification (HIGH/MEDIUM/LOW) per
  finding, evidence extraction (quotes from both documents), and explicit binary execution-equivalence verdict
- `plugin/skills/compare-docs-agent/first-use.md` — update output format documentation to show severity, evidence
  fields, and binary verdict; update verification checklist
- `plugin/skills/compare-docs-agent/EXTRACTION-AGENT.md` — add location quotes to extraction output so the
  comparison agent can cite evidence from both sides
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/CompareDocsGradingTest.java` — unit tests for severity
  classification logic and binary verdict derivation (if Java handler exists; otherwise test via empirical-test)

## Pre-conditions
None

## Execution Waves

### Wave 1: Evidence Extraction in Extractors
- Update EXTRACTION-AGENT.md to include verbatim quote snippets in each extracted semantic unit's JSON output
  - New field: `"quote": "<verbatim text from source document>"`
  - Quote should be the minimal span that captures the semantic unit
  - This enables the comparison agent to cite evidence from both documents when reporting findings
  - Files: `plugin/skills/compare-docs-agent/EXTRACTION-AGENT.md`

### Wave 2: Severity Classification and Binary Verdict in Comparator
- Update COMPARISON-AGENT.md:
  - Add severity classification per finding:
    - **HIGH**: PROHIBITION, REQUIREMENT, or CONDITIONAL unit lost or materially changed — would alter execution
      behavior
    - **MEDIUM**: CONSEQUENCE, DEPENDENCY, or SEQUENCE unit lost or changed — could alter execution under specific
      conditions
    - **LOW**: CONJUNCTION, REFERENCE unit lost or changed — context loss but unlikely to change execution
    - EXCLUSION units lost are always HIGH (they define boundaries)
  - Add evidence extraction per finding: include quotes from both documents (from extractor output) showing what
    was present in original and what appears (or is absent) in compressed version
  - Add explicit binary verdict field to output:
    - `"execution_equivalent": true` — no HIGH or MEDIUM findings; compressed document will produce identical
      execution behavior
    - `"execution_equivalent": false` — one or more HIGH or MEDIUM findings; compressed document may produce
      different execution behavior
  - LOW-severity findings do NOT fail the gate — they are informational diagnostics only
  - Files: `plugin/skills/compare-docs-agent/COMPARISON-AGENT.md`

### Wave 3: Documentation and Verification
- Update first-use.md:
  - Document new output fields: `severity`, `evidence`, `execution_equivalent`
  - Update output format examples showing enriched comparison results
  - Update verification checklist to confirm binary verdict is present and correct
  - Clarify that `execution_equivalent` is the authoritative gate — all other fields are diagnostic
  - Files: `plugin/skills/compare-docs-agent/first-use.md`
- Run `mvn -f client/pom.xml test` if Java test files were created
  - Files: (build verification)

## Post-conditions
- [ ] Each comparison finding includes severity (HIGH/MEDIUM/LOW) and evidence (quotes from both documents)
- [ ] Comparison output includes explicit `"execution_equivalent": true|false` binary verdict
- [ ] Binary verdict logic: false if any HIGH or MEDIUM finding exists, true otherwise
- [ ] LOW-severity findings are informational only and do not affect the verdict
- [ ] Extraction agents include verbatim quote snippets in their output
- [ ] first-use.md documents the enriched output format and binary verdict semantics
- [ ] Existing three-agent blind architecture is preserved unchanged
- [ ] `mvn -f client/pom.xml test` exits 0 with no regressions (if applicable)

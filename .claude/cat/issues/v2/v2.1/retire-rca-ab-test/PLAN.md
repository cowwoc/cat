# Plan: retire-rca-ab-test

## Goal

Retire the RCA A/B test (243 accumulated mistakes, 11-13% recurrence across all methods — within noise) and
standardize all new mistake recording on Method C (Causal Barrier Analysis). Remove the modulo-3 assignment routing
from the learn skill workflow and update the test documentation to record the final conclusion.

## Parent Requirements

None

## Research Findings

A/B test outcome data:

| Method | Cases | Recurrences | Recurrence Rate |
|--------|-------|-------------|-----------------|
| A (5-Whys) | 79 | 10 | 12% |
| B (Taxonomy) | 87 | 12 | 13% |
| C (Causal Barrier) | 77 | 9 | 11% |

All three methods are within noise margin — no statistically significant winner. The test ran well past the 90-mistake
final determination threshold (79/87/77 per method = 243 total), meeting the lock-in condition documented in
RCA-AB-TEST.md § "Analysis Schedule".

Method C is selected as the standard on structural grounds:
- Closest alignment with AgentDebug backward-tracing research and the healthcare hierarchy-of-controls framework
- Multi-factor candidate enumeration (avoids single-chain bias of Method A)
- Explicit symptom vs cause distinction
- Method B's taxonomy classification value is absorbed as the `module` field already present in Method C candidate
  enumeration

Method A (5-Whys) is strictly weaker: single-chain, no symptom/cause distinction, arbitrary stopping point.

## Approaches

### A: Update skill files only (no Java changes)

- **Risk:** LOW
- **Scope:** 3 files (phase-analyze.md, rca-methods.md, RCA-AB-TEST.md)
- **Description:** The A/B assignment logic lives entirely in Markdown skill files. No Java source encodes the
  modulo-3 rule. Only the three skill files need updating; tests already exercise historical A/B records correctly
  and do not need changes.

### B: Update skill files + update tests to reflect standardized method

- **Risk:** LOW
- **Scope:** 5 files (adds RecordLearningTest.java and fixture updates)
- **Description:** Same skill file changes as A, plus update test fixtures that use `rca_method: "A"` to use `"C"`,
  ensuring tests reflect the post-retirement standard.

> **Selected: Approach A.** Test files in RecordLearningTest.java use `rca_method: "A"` as *historical* records —
> they are correct regardless of which method is current. RootCauseAnalyzerTest.java tests the analyzer's ability to
> read any rca_method value; those tests must continue to use A/B/C values to verify backward compatibility.
> Changing test fixtures would reduce coverage of historical-record reading. No Java changes are required.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Retrospective analysis tools must continue to read historical A/B records; test infrastructure must
  not silently break.
- **Mitigation:** Preserve `rca_method` field in JSON schema; retain Methods A and B as documented historical
  references in rca-methods.md; keep RootCauseAnalyzerTest unchanged.

## Files to Modify

- `plugin/skills/learn/RCA-AB-TEST.md` — add test conclusion section documenting final results and Method C
  selection; mark test as CONCLUDED; retain full historical specification
- `plugin/skills/learn/rca-methods.md` — remove Method Assignment (modulo-3 rule) section; add "Standardized
  Method" header pointing to Method C; mark Methods A and B as "Historical Reference Only (retired)"
- `plugin/skills/learn/phase-analyze.md` — remove A/B TEST IN PROGRESS block and modulo-3 routing instructions;
  replace with fixed "use Method C (Causal Barrier)" instruction; update rca_method JSON examples from `"A|B|C"` to
  `"C"` and rca_method_name from `"5-whys|taxonomy|causal-barrier"` to `"causal-barrier"`

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update `plugin/skills/learn/RCA-AB-TEST.md`:
  - Add a `## Test Conclusion` section at the top (after the header, before `## Purpose`) with:
    - Status: CONCLUDED (2026-03-10)
    - Final results table: Method A 79/10/12%, Method B 87/12/13%, Method C 77/9/11%
    - Decision: No statistically significant winner. Method C selected as standard on structural grounds
      (closest to backward-tracing research and hierarchy-of-controls framework; absorbs Method B taxonomy
      classification as candidate enumeration field; Method A strictly weaker due to single-chain bias)
    - Outcome: A/B routing retired. All new mistakes use Method C. Historical records preserved unchanged.
  - Keep all existing content intact below the new section (historical specification)
  - Files: `plugin/skills/learn/RCA-AB-TEST.md`

- Update `plugin/skills/learn/rca-methods.md`:
  - Remove the `## Method Assignment` section (the modulo-3 rule block)
  - Add a `## Current Standard` section at the top (after the license header, before Method A) containing:
    ```
    ## Current Standard

    All new mistakes use **Method C: Causal Barrier Analysis**.
    Methods A and B are retained as historical reference only and are no longer assigned to new mistakes.
    ```
  - Add a `> **RETIRED** — historical reference only. New mistakes use Method C.` callout immediately after the
    `## Method A: 5-Whys (Control)` heading (before the paragraph text)
  - Add a `> **RETIRED** — historical reference only. New mistakes use Method C.` callout immediately after the
    `## Method B: Modular Error Taxonomy` heading (before the paragraph text)
  - Update the `## Recording Format` JSON example: change `"rca_method": "A|B|C"` to `"rca_method": "C"` and
    `"rca_method_name": "5-whys|taxonomy|causal-barrier"` to `"rca_method_name": "causal-barrier"`
  - Files: `plugin/skills/learn/rca-methods.md`

- Update `plugin/skills/learn/phase-analyze.md`:
  - Remove the `**A/B TEST IN PROGRESS**` block (lines referencing RCA-AB-TEST.md and the modulo-3 assignment rule)
  - Replace with a single fixed instruction:
    ```
    **Method: Causal Barrier Analysis (Method C)**
    All new mistakes use Method C. See [rca-methods.md](rca-methods.md) for the full template.
    ```
  - Update the JSON snippet in the recording schema that shows `"rca_method": "A|B|C"` → `"rca_method": "C"`
    and `"rca_method_name": "5-whys|taxonomy|causal-barrier"` → `"rca_method_name": "causal-barrier"`
    (both occurrences)
  - Do NOT change any other sections of phase-analyze.md
  - Files: `plugin/skills/learn/phase-analyze.md`

- Update `plugin/skills/learn/first-use.md`:
  - Remove the `## A/B Test: RCA Method Comparison` section entirely (the section starting at the line
    `## A/B Test: RCA Method Comparison` through the end of the lock-in checklist)
  - Remove the line referencing `Mistake ID modulo 3` assignment in the recording template if present
  - The `RCA Method: {rca_method_name}` display line in the template output is fine to keep (it still shows the
    method used for each recorded mistake)
  - Files: `plugin/skills/learn/first-use.md`

### Wave 2

- Commit all four modified files as a single `feature:` commit:
  - Message: `feature: retire RCA A/B test and standardize on Method C (Causal Barrier Analysis)`
  - Body: `243 mistakes assigned (79/87/77 per method), all within 11-13% recurrence rate noise band. No
    statistically significant winner. Method C selected on structural superiority. Modulo-3 routing removed.
    Historical rca_method field values preserved unchanged.`
- Update `STATE.md` status to "closed" and progress to 100%
- Files: all four skill files + STATE.md

## Post-conditions

- [ ] RCA-AB-TEST.md contains a `## Test Conclusion` section documenting: CONCLUDED status, final result table
  (A=79/10/12%, B=87/12/13%, C=77/9/11%), decision rationale, and Method C selection
- [ ] rca-methods.md has `## Current Standard` section, Methods A and B marked as RETIRED, and Recording Format
  updated to use `"rca_method": "C"` / `"rca_method_name": "causal-barrier"`
- [ ] phase-analyze.md no longer contains `A/B TEST IN PROGRESS`, modulo-3 routing, or `"A|B|C"` method values;
  uses fixed Method C reference
- [ ] first-use.md no longer contains `## A/B Test` section or modulo-3 assignment references
- [ ] Existing mistakes with rca_method `"A"` or `"B"` are unchanged (no migration of historical data)
- [ ] `mvn -f client/pom.xml test` passes with no failures (RootCauseAnalyzerTest still reads A/B/C records
  correctly; RecordLearningTest historical fixtures unmodified)
- [ ] E2E: The learn skill workflow no longer prompts the agent to check mistake ID modulo 3; new mistakes
  recorded after this change use `rca_method: "C"` and `rca_method_name: "causal-barrier"`

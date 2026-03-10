# Plan: remove-rca-ab-test-file

## Current State

`plugin/skills/learn/` contains `RCA-AB-TEST.md`, a test infrastructure document for comparing three RCA
methods (A: 5-Whys, B: Taxonomy, C: Causal Barrier). The A/B test concluded when `retire-rca-ab-test` was
merged, selecting Method C (Causal Barrier) as the winner. `rca-methods.md` still contains Method A and
Method B sections alongside the modulo-3 assignment rule. `phase-analyze.md` and `first-use.md` still
reference the A/B test with "ACTIVE" status banners and method selection tables. `mistake-categories.md`
still shows `rca_method: "{A|B|C}"` in the JSON schema.

## Target State

`RCA-AB-TEST.md` is deleted. `rca-methods.md` retains only Method C (Causal Barrier Analysis) plus the
Cause Signature Vocabulary, Prevention Strength Gate, and Recording Format sections — all test scaffolding
(Method Assignment section, Method A section, Method B section) is removed. All active learn skill
documentation references only Method C. JSON schema examples updated to reflect only `"C"` as the
`rca_method` value.

## Parent Requirements

None — tech debt cleanup.

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None. Method C is already the only method used going forward. Removing Methods A
  and B from documentation does not break existing mistake entries, which retain their `rca_method` field
  values for historical tracing. The `rca_method` field itself remains valid — only its enumeration
  description changes.
- **Mitigation:** All existing tests run after changes. E2E manual check: invoke learn skill and confirm
  only Method C is referenced.

## Files to Modify

- `plugin/skills/learn/RCA-AB-TEST.md` — delete this file entirely
- `plugin/skills/learn/rca-methods.md` — remove `## Method Assignment` section (lines 10-15), remove
  `## Method A: 5-Whys (Control)` section (lines 17-45), remove `## Method B: Modular Error Taxonomy`
  section (lines 46-77); retain `## Method C: Causal Barrier Analysis` and all subsequent sections
  unchanged
- `plugin/skills/learn/phase-analyze.md` — in Step 3, remove the `**A/B TEST IN PROGRESS**` banner line
  (currently `**A/B TEST IN PROGRESS** - See [RCA-AB-TEST.md](RCA-AB-TEST.md) for full specification.`);
  remove the Quick Reference method table (the three-row table listing Methods A, B, C); update the
  `"rca_method": "A|B|C"` JSON snippet to `"rca_method": "C"`; update the
  `"rca_method_name": "5-whys|taxonomy|causal-barrier"` to `"rca_method_name": "causal-barrier"` (appears
  at lines ~95 and ~384)
- `plugin/skills/learn/first-use.md` — remove the `## A/B Test: RCA Method Comparison` section in its
  entirety (from `## A/B Test: RCA Method Comparison` through the `### Lock-In Process` subsection
  ending at `4. Update this section to document final result`; preserve `## Error Handling` and everything
  after it)
- `plugin/skills/learn/mistake-categories.md` — update `"rca_method": "{A|B|C}"` to
  `"rca_method": "C"` and `"rca_method_name": "{5-whys|taxonomy|causal-barrier}"` to
  `"rca_method_name": "causal-barrier"` in the JSON entry format example

## Pre-conditions

- [ ] `retire-rca-ab-test` issue is closed (dependency)
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Delete `plugin/skills/learn/RCA-AB-TEST.md`
  - Use `cat:safe-rm-agent` or `git rm` to remove the file
  - Files: `plugin/skills/learn/RCA-AB-TEST.md`

- Edit `plugin/skills/learn/rca-methods.md`: remove Method Assignment, Method A, and Method B sections
  - Remove lines 10-77 (from `## Method Assignment` through the closing ` ``` ` of Method B block)
  - The file should begin with the existing header and proceed directly to `## Method C: Causal Barrier
    Analysis`
  - Files: `plugin/skills/learn/rca-methods.md`

### Wave 2

- Edit `plugin/skills/learn/phase-analyze.md`: remove A/B test banner and method table; update JSON
  schema entries
  - Remove the line: `**A/B TEST IN PROGRESS** - See [RCA-AB-TEST.md](RCA-AB-TEST.md) for full
    specification.`
  - Remove the Quick Reference table (header + 3 data rows for Methods A, B, C)
  - Update both occurrences of `"rca_method": "A|B|C"` to `"rca_method": "C"`
  - Update both occurrences of `"rca_method_name": "5-whys|taxonomy|causal-barrier"` to
    `"rca_method_name": "causal-barrier"`
  - Files: `plugin/skills/learn/phase-analyze.md`

- Edit `plugin/skills/learn/first-use.md`: remove entire A/B Test section
  - Remove from `## A/B Test: RCA Method Comparison` through `4. Update this section to document final
    result` (inclusive), leaving `## Error Handling` as the next section after `## Related Skills`
  - Files: `plugin/skills/learn/first-use.md`

- Edit `plugin/skills/learn/mistake-categories.md`: update JSON schema example
  - Change `"rca_method": "{A|B|C}"` to `"rca_method": "C"`
  - Change `"rca_method_name": "{5-whys|taxonomy|causal-barrier}"` to
    `"rca_method_name": "causal-barrier"`
  - Files: `plugin/skills/learn/mistake-categories.md`

### Wave 3

- Run `mvn -f client/pom.xml test` to confirm no regressions
- Update `STATE.md` progress to 100%

## Post-conditions

- [ ] `RCA-AB-TEST.md` no longer exists at `plugin/skills/learn/RCA-AB-TEST.md`
- [ ] `rca-methods.md` contains no `## Method Assignment`, `## Method A`, or `## Method B` sections
- [ ] `rca-methods.md` retains `## Method C: Causal Barrier Analysis` and all subsequent sections intact
- [ ] `phase-analyze.md` contains no reference to `RCA-AB-TEST.md` and no A/B test banner
- [ ] `first-use.md` contains no `## A/B Test` section and no references to Methods A or B
- [ ] `mistake-categories.md` JSON schema example shows `"rca_method": "C"` and
  `"rca_method_name": "causal-barrier"`
- [ ] No remaining `grep` matches for `RCA-AB-TEST` in `plugin/skills/learn/` (excluding history)
- [ ] All existing tests pass (exit code 0 from `mvn -f client/pom.xml test`)
- [ ] E2E verification: invoke the learn skill and confirm Step 3 references only Method C (Causal
  Barrier Analysis)

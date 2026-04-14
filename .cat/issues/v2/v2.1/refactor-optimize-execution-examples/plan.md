# Plan: refactor-optimize-execution-examples

## Current State
`plugin/skills/optimize-execution/first-use.md` (738 lines) contains two large reference sections that
are documentation for human readers, not instructions agents need during execution:
1. **"Worked Example: Subagent Delegation Trade-off Analysis"** (lines 676–720, ~44 lines): full mathematical
   derivation of cost-weighted delegation savings, duplicating formulas already defined in Steps 3–4.
2. **"Manual Extraction (advanced / debugging)"** subsection in Step 3 (lines 169–191, ~22 lines): fallback
   bash for inspecting JSONL directly when session-analyzer is unavailable — rarely needed and clutters
   the primary path.
3. **Verbose prose in "Optimization Pattern Details"** (lines 367–463, ~97 lines): restatements of detection
   criteria already covered in Step 6 bullets; the detection table and key principle sentences are high-value
   but the surrounding prose is redundant.

Every invocation loads this entire file, paying ~1,050 extra tokens for material that doesn't affect execution.

## Target State
- Sections 1 and 2 moved to `plugin/concepts/delegation-analysis.md` (new concept file); replaced in
  `first-use.md` with 1-line reference links.
- Section 3 collapsed: detection table and "Subagent Content Relay Anti-Pattern" section retained verbatim;
  verbose prose restatements removed.
- `plugin/concepts/delegation-analysis.md` contains the worked example and JSONL manual extraction content
  with a proper license header.

## Parent Requirements
None — token efficiency improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — skill behavior is unchanged; agents follow identical instruction steps
- **Mitigation:** Post-condition verifies all 7 analysis steps still present in first-use.md

## Files to Modify
- `plugin/skills/optimize-execution/first-use.md` — remove sections 1–3, add reference links to concept file
- `plugin/concepts/delegation-analysis.md` — new file; moved content from sections 1 and 2

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Create delegation-analysis.md concept file
- Create `plugin/concepts/delegation-analysis.md` with license header
  - Content: the "Worked Example: Subagent Delegation Trade-off Analysis" section (verbatim)
  - Content: the "Manual Extraction (advanced / debugging)" subsection (verbatim)
  - Add an introductory sentence linking back to the optimize-execution skill for context
  - Files: `plugin/concepts/delegation-analysis.md`

### Job 2: Refactor first-use.md
- Remove "Worked Example: Subagent Delegation Trade-off Analysis" section (lines 676–720); replace with:
  `See [delegation-analysis.md](plugin/concepts/delegation-analysis.md) for a worked example with full token calculations.`
- In Step 3, remove "Manual Extraction (advanced / debugging)" subsection (lines 169–191); replace with:
  `If session-analyzer is unavailable, see [delegation-analysis.md](plugin/concepts/delegation-analysis.md) for manual JSONL extraction steps.`
- In "Optimization Pattern Details" (lines 367–463): remove restatement prose for Pipelining Opportunities,
  Script Extraction Opportunities, and Token Efficiency Patterns narrative paragraphs; retain verbatim:
  - The 5-row "Token Efficiency Patterns" detection table (Pattern / Detection / Estimated Savings)
  - The "Subagent Content Relay Anti-Pattern" section (definition, detection criteria, correct/anti-pattern examples, impact)
  - The "Script Extraction Opportunities" principle sentence and detection note
  - Files: `plugin/skills/optimize-execution/first-use.md`

## Post-conditions
- [ ] All 7 analysis steps (Steps 1–7) still present in `plugin/skills/optimize-execution/first-use.md`
- [ ] `plugin/concepts/delegation-analysis.md` exists with license header and contains the worked example and manual extraction content
- [ ] Reference links in `first-use.md` use path `plugin/concepts/delegation-analysis.md` (not relative, not `.claude/` path)
- [ ] Detection table (5 rows: Pattern / Detection / Estimated Savings) present in `first-use.md`
- [ ] "Subagent Content Relay Anti-Pattern" section present in `first-use.md`
- [ ] `plugin/concepts/delegation-analysis.md` does not overlap with `plugin/concepts/subagent-delegation.md` (different content: trade-off math vs. delegation principles)
- [ ] `first-use.md` token count reduced by ≥900 tokens (measured via `wc -w` before/after)
- [ ] Tests passing: `mvn -f client/pom.xml verify -e` exits 0

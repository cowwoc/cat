# Plan

## Goal

Reorganize instruction-builder-agent first-use.md with targeted structural improvements: (1) Reorder sections so
Subagent Command Allowlist is immediately before Procedure (move Document Structure section earlier), (2) Add missing
Verification checklist groups for Steps 8 and 10.

Note: The original plan's goals (b) and (c) are already satisfied by the current file — the isolation notes at lines
223 and 664-665 express different concepts (not duplicates), and the Verification section is already grouped by phase.

## Pre-conditions

(none)

## Post-conditions

- [ ] Document Structure section appears before Subagent Command Allowlist, placing the allowlist immediately before
  Procedure
- [ ] Verification section includes a checklist group for Step 8 (failure analysis / instruction-analyzer)
- [ ] Verification section includes checklist items covering Step 10 (in-place hardening mode)
- [ ] All semantic units from original document preserved (no content loss)
- [ ] All existing tests pass

## Jobs

### Job 1

- Reorder `## Document Structure: XML vs Markdown` (lines 90-167) to appear before `## Subagent Command Allowlist`
  (lines 57-87), so the allowlist is the last section before `## Procedure`
- Add `### Failure analysis` verification group for Step 8 covering: instruction-analyzer-agent spawned correctly,
  analysis report presented, iteration cap of 5 respected
- Extend or add verification items for Step 10 (in-place hardening mode) covering: batch-mode findings path naming,
  batch summary table, in-place mode precondition check (test-results.json must exist)

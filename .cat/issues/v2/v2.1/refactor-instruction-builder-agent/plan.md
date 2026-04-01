# Plan

## Goal

Refactor instruction-builder-agent's first-use.md by applying its own design methodology to itself. Centralize the
curiosity gate (from 4 scattered config reads to 1), flatten step numbering (eliminate decimal sub-steps 4.1-4.5 and
7.1-7.4), reduce the verification checklist (from ~80 items to ~30 outcome-oriented checks), extract the Subagent
Command Allowlist into a shared section, add an explicit Prerequisites section, and condense subagent prohibition
prompts by ~40%.

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged: the instruction-builder produces identical outputs for all existing workflows
- [ ] All tests passing: `mvn -f client/pom.xml test` exits 0
- [ ] Code quality improved: step numbering is sequential (no decimal sub-steps), curiosity gate is read once, verification checklist is grouped by phase
- [ ] E2E verification: invoke instruction-builder on a simple test skill and confirm it completes the design subagent phase successfully

## Jobs

### Job 1

Refactor `plugin/skills/instruction-builder-agent/first-use.md` with the following structural changes. The file is
the only file modified in this issue.

**Step 1: Add Prerequisites section after "Invocation Restriction" and before "Document Structure"**

Insert a new `## Prerequisites` section containing:
- **Required inputs:** `GOAL` and `EXISTING_INSTRUCTION_PATH` (or `"N/A"`)
- **Curiosity level:** Single read of `curiosity` from effective config (`get-config-output effective`), stored as
  `CURIOSITY`. Document that this value gates Steps 5-11 (when `CURIOSITY = low`, skip test evaluation, adversarial
  hardening, compression, and organic tests)

This replaces the 4 scattered `effort`/`curiosity` gate checks currently in Steps 4, 5, 6, and 7.

**Step 2: Extract Subagent Command Allowlist to shared section**

Move the "## Subagent Command Allowlist" section (currently embedded within Step 4) to a top-level section placed
after Prerequisites and before Procedure. Keep all three categories (test-run, grader/analyzer, all subagents) and
the full allowlist content. Add a single "instruction-based isolation limitation" note here instead of the two
separate "Note on instruction-based isolation" paragraphs currently in the test-run and grader sub-sections.

**Step 3: Flatten step numbering**

Renumber all steps to be sequential 1-based integers. The current structure maps to:

| Current | New | Content |
|---------|-----|---------|
| Step 1 | Step 1 | Collect Existing Instruction Content |
| Step 2 | Step 2 | Delegate Design Phase to Task Subagent |
| Step 3 | Step 3 | Compact-Output Pass |
| Step 4 (intro) | Step 4 | Write Draft and Prepare Test Infrastructure (compute TEST_DIR, TEST_MODEL, sanity check) |
| Step 4.1 | Step 5 | Auto-Generate Test Cases |
| Step 4.2 | Step 6 (subsection) | Incremental Test Case Selection (make a subsection header within Step 6, not a top-level step) |
| Step 4.3 | Step 6 | SPRT Test Execution (includes pipeline, grading, result inspection) |
| Step 4.4 | Step 7 | SPRT Failure Investigation |
| Step 4.5 | Step 8 | Analyze and Iterate |
| Step 5 | Step 9 | Adversarial TDD Loop |
| Step 6 | Step 10 | In-Place Hardening Mode (Optional) |
| Step 7 | Step 11 | Compression Phase |
| Step 8 | (omit) | Create Organic Test Cases (omit — not present in source first-use.md) |

Within each new step, use descriptive subsection headers (####) instead of decimal sub-steps for internal structure
(e.g., "#### Incremental Test Case Selection", "#### SPRT Parameters", "#### Pipeline Control Flow").

**Step 4: Reduce verification checklist**

Replace the current ~80-item checklist with ~30 items grouped by phase:

- **Design phase** (~3 items): design subagent returned complete draft, compact-output applied, correctness priority
- **Test generation** (~7 items): per-unit extraction, action-based assertions, production-sequence prompts, negative
  scenarios, YAML frontmatter, user approval, no system_reminders
- **SPRT execution** (~8 items): parameters correct, fresh subagents, TEST_MODEL from extract-model, grader subagents,
  temp files only, literal strings passed, result inspection checklist, token summary
- **Failure investigation** (~4 items): auto-runs on reject, checks performed, conclusion routing correct
- **Adversarial hardening** (~5 items): follows protocol, target_type correct, no inline embedding, prior test check,
  batch findings paths
- **Compression** (~5 items): never interleaved with hardening, semantic pre-check, retries capped, acceptance
  criteria identical, subagent file restrictions

Remove items that merely duplicate procedure text (e.g., "Step 2 design subagent tool prohibition explicitly lists
NotebookEdit alongside other prohibited tools" — this is already in the Step 2 procedure text).

**Step 5: Condense subagent prohibition prompts**

For each subagent prompt section (test-run, grader, compression, analyzer):
- Remove duplicate "Note on instruction-based isolation" paragraphs (now in shared section)
- Consolidate repeated file-access restrictions into a reference to the shared allowlist
- Remove verbose re-listings of prohibited tools when they duplicate the shared allowlist
- Keep all actual constraints — only remove redundant text that restates the same constraint

**Step 6: Replace curiosity gate checks**

In each step that currently has an inline curiosity/effort gate check (reading config and checking value), replace
with a reference to the `CURIOSITY` variable set in Prerequisites:

- Old pattern: `Read effort from config... If effort = low, skip...`
- New pattern: `If CURIOSITY = low, skip this step.`

**Step 7: Update index.json to closed status**

After all changes are made and verified:
```bash
cd ${WORKTREE_PATH} && sed -i 's/"open"/"closed"/' .cat/issues/v2/v2.1/refactor-instruction-builder-agent/index.json
```

Commit all changes together: `refactor: restructure instruction-builder-agent first-use.md`

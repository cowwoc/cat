# Plan: clarify-git-rebase-conflict-handling

## Problem

The `CONFLICT` status handling section in `plugin/skills/git-rebase-agent/first-use.md` is incomplete: it states that
the agent "examines conflicting_files and decides" but does not provide numbered steps for re-running the rebase
tool, resolving conflict markers, staging files, calling `git rebase --continue`, or calling `git rebase --abort`
when giving up. Agents encountering a CONFLICT response must improvise rather than follow documented procedure.

## Parent Requirements

None

## Reproduction Code

```
# Trigger scenario: rebase with intentional conflict
"${CLAUDE_PLUGIN_ROOT}/client/bin/git-rebase" "$WORKTREE_PATH" "$TARGET_BRANCH"
# Returns: {"status": "CONFLICT", "backup_branch": "backup-before-rebase-...", "conflicting_files": [...]}
# → Agent has no numbered steps to follow for resolution or abort
```

## Expected vs Actual

- **Expected:** The `CONFLICT` status section in `first-use.md` provides numbered, mechanical steps covering:
  re-running the rebase tool, inspecting conflict markers, staging resolved files, calling
  `git rebase --continue`, and calling `git rebase --abort` when abandoning.
- **Actual:** The section says "Agent examines conflicting_files and decides: manual resolution, alternative strategy,
  or abort" without providing steps. The `--continue` and `--abort` commands are mentioned only in a bash code block
  under `## Handling Conflicts` but are not cross-referenced from the Result Handling table's CONFLICT row.

## Root Cause

The `## Result Handling` table's CONFLICT row delegates to the `## Handling Conflicts` section for resolution detail,
but that section lacks explicit numbered steps that map to the `git-rebase` tool's JSON output (e.g., iterating over
`conflicting_files`, staging, calling `--continue`). The instructions for using `--continue` vs `--abort` are present
but scattered and not reachable from the primary result-handling flow.

## Alternatives Considered

### A: Inline numbered steps inside Result Handling table (rejected)
Add a numbered list directly inside the CONFLICT row of the Result Handling table.
- **Risk:** HIGH — Markdown tables do not render nested lists reliably; formatting degrades.
- **Rejected because:** Poor readability in table cells.

### B: Expand `## Handling Conflicts` section with numbered steps and cross-reference from table (chosen)
Replace the current prose under `## Handling Conflicts` with explicit numbered steps. Update the CONFLICT row in the
Result Handling table to direct the agent to this expanded section.
- **Risk:** LOW — All changes are in one file, no logic changes, no tool changes.
- **Chosen because:** Keeps table rows concise, puts detail in a dedicated section agents can follow step-by-step.

### C: Create a separate `conflict-resolution.md` companion file (rejected)
Extract conflict handling into a new file and link from `first-use.md`.
- **Risk:** MEDIUM — Introduces an additional file to maintain; agents may not load it unless first-use.md references it.
- **Rejected because:** Unnecessary complexity for a self-contained documentation fix.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Documentation-only change; no runtime behavior changes.
- **Mitigation:** Read existing section carefully before editing to preserve correct content; verify all references
  to the CONFLICT case are consistent after the change.

## Files to Modify

- `plugin/skills/git-rebase-agent/first-use.md` — Expand `## Handling Conflicts` section with numbered steps for
  `--continue` and `--abort`; update CONFLICT row in Result Handling table to reference the expanded section.

## Related Files to Check (read-only)

- `plugin/skills/work-merge-agent/first-use.md` — References `cat:git-rebase-agent` CONFLICT handling; verify its
  guidance is consistent with the updated `first-use.md`. Update if inconsistent.
- `plugin/skills/merge-subagent-agent/first-use.md` — Contains CONFLICT handling instructions; verify consistency.

## Test Cases

- [ ] CONFLICT status row in Result Handling table directs agent to numbered steps section
- [ ] Numbered steps cover: inspect conflicting_files, resolve markers, stage files, `git rebase --continue`,
  repeat for each commit, and `git rebase --abort` when giving up
- [ ] `--abort` step includes restoring from backup_branch
- [ ] E2E: agent encountering a CONFLICT response can follow the documented steps mechanically without ambiguity

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- Read `plugin/skills/git-rebase-agent/first-use.md` in full.
  - Locate the `## Handling Conflicts` section (currently lines ~136-162).
  - Locate the `CONFLICT` row in the `## Result Handling` table (currently lines ~79-83).
  - Replace the content of `## Handling Conflicts` with the following expanded numbered-step structure:

    ```
    ## Handling Conflicts

    **CRITICAL: Persist through conflicts. Never switch to cherry-pick mid-rebase.**

    When the `git-rebase` tool returns `status: CONFLICT`, follow these steps:

    **Step 1: Inspect conflicting files**
    ```bash
    # The JSON output lists conflicting_files — examine each one
    git status
    ```

    **Step 2: Resolve conflict markers in each file**
    Open each conflicting file and resolve the `<<<<<<<` / `=======` / `>>>>>>>` markers.
    Use unambiguous references (see "Conflict Resolution References" below) rather than
    `--ours`/`--theirs`.

    **Step 3: Stage each resolved file**
    ```bash
    git add <resolved-file>
    ```

    **Step 4: Continue the rebase**
    ```bash
    git rebase --continue
    ```

    **Step 5: Repeat Steps 1–4 for each conflicting commit**
    The rebase stops once per conflicting commit. Repeat until all commits are replayed.

    **Step 6: Delete the backup branch after successful resolution**
    ```bash
    git branch -D <backup_branch>
    ```

    **To abort instead of resolving:**
    If conflicts are too complex or you decide to abandon the rebase:
    ```bash
    git rebase --abort
    git reset --hard <backup_branch>
    git branch -D <backup_branch>
    ```
    ```

  - Update the `CONFLICT` row in the Result Handling table so the "Agent Recovery Action" reads:
    `Follow the numbered steps in **## Handling Conflicts** below. Backup preserved at backup_branch.
    Delete backup after resolution or abort is complete.`

- Read `plugin/skills/work-merge-agent/first-use.md`. Locate any block that describes what to do when
  `git-rebase` returns a CONFLICT status (search for the text "CONFLICT" or "rebase" near conflict-handling
  instructions). If that block describes steps for resolving conflicts or aborting, ensure it either:
  (a) defers to "Follow the numbered steps in **## Handling Conflicts** in the git-rebase-agent skill", or
  (b) is already consistent with the 6-step --continue path and --abort path defined in the updated
      `git-rebase-agent/first-use.md`.
  If it gives different or contradictory instructions, update that block to reference the expanded
  `## Handling Conflicts` section in `git-rebase-agent/first-use.md`.

- Read `plugin/skills/merge-subagent-agent/first-use.md`. Locate any block that describes CONFLICT handling
  for a git rebase (search for "CONFLICT" or "rebase" near conflict instructions). Apply the same consistency
  check: if the block gives steps for resolving or aborting that contradict the updated 6-step/abort path,
  update it to reference or match the `## Handling Conflicts` section in `git-rebase-agent/first-use.md`.
  If no such block exists, no change is needed.

- Commit all changes with message: `bugfix: clarify git-rebase CONFLICT handling with explicit --continue and --abort steps`

## Post-conditions

- [ ] `plugin/skills/git-rebase-agent/first-use.md` `## Handling Conflicts` section contains numbered steps 1–6
  for `--continue` path and an explicit `--abort` path with backup restoration
- [ ] The CONFLICT row in the Result Handling table references the `## Handling Conflicts` section
- [ ] `plugin/skills/work-merge-agent/first-use.md` CONFLICT guidance is consistent with updated steps
- [ ] `plugin/skills/merge-subagent-agent/first-use.md` CONFLICT guidance is consistent with updated steps
- [ ] E2E: Trigger a conflict scenario and confirm the documented steps produce correct and unambiguous guidance

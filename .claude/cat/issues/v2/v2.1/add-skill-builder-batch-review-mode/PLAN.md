# Plan: add-skill-builder-batch-review-mode

## Goal

Enhance the skill-builder-agent workflow with two improvements:

1. Increase the adversarial TDD loop hard cap from 3 rounds to 10 rounds.
2. Enable running the adversarial TDD loop against a single skill file in a worktree in a single session,
   committing the final hardened file in place without creating per-round issues. Running against multiple skills
   (sequentially or in parallel) should also be possible.

## Background

The current skill-builder-agent `first-use.md` (Step 4: Adversarial TDD Loop) caps hardening at 3 iterations.
This cap is too low for complex skills where genuine loopholes persist past round 3. Raising the cap to 10 allows
the loop to converge naturally on well-hardened instructions without artificial truncation.

Additionally, there is no mechanism for in-place skill hardening. Currently, reviewing a skill in a worktree
requires creating a separate issue for each skill. In-place hardening mode lets a practitioner pass a single skill
file path in a checked-out worktree, iterate RED→BLUE up to 10 rounds in-memory against `CURRENT_INSTRUCTIONS`,
then commit the final hardened version exactly once — without spawning per-round issues. The same workflow can be
applied to multiple skills (sequentially or in parallel) when the caller passes a directory path (or `--batch <dir>`).

## Pre-conditions

- `plugin/skills/skill-builder-agent/first-use.md` exists and contains Step 4 with the 3-round cap.
- The adversarial TDD loop structure (red-team / blue-team subagent prompts) is already present.
- No existing batch-review mode is defined in any skill-builder file.

## Post-conditions

- The adversarial TDD round cap in Step 4 is updated from 3 to 10 in `first-use.md`.
- A new in-place hardening mode section is documented and implemented in `first-use.md`, covering:
  - Primary entry: caller passes a single skill file path in a worktree. The full RED→BLUE loop runs in-memory
    (up to 10 rounds). The final hardened content is written back to the file and committed exactly once.
  - Secondary entry: caller passes a directory path (or `--batch <dir>`). The single-skill workflow is applied
    to every `SKILL.md` and `first-use.md` under the directory.
  - Output: single-skill commit message with round/loophole counts; batch summary table when directory mode used.
- E2E verification: invoke skill-builder on a sample skill file and confirm 10-round cap applies; confirm in-place
  hardening mode produces a single commit after convergence.

## Implementation Steps

### Step 1: Update adversarial TDD round cap

In `plugin/skills/skill-builder-agent/first-use.md`, locate Step 4 (Adversarial TDD Loop).

Find the termination condition text:

```
If round < 3 and `major_loopholes_found` was true: continue to next iteration.
```

Update the cap from `3` to `10`:

```
If round < 10 and `major_loopholes_found` was true: continue to next iteration.
```

Also update the prose description near the top of Step 4 from "or 3 iterations complete" to "or 10 iterations
complete".

Verify the Verification checklist at the bottom of the file still reads correctly after the change (it references
"3 iterations" implicitly via the loop prose — update any such references).

### Step 2: Replace batch-review mode section with in-place hardening mode section

Replace the existing `### Step 5: Batch-Review Mode (Optional)` section in `first-use.md` with
`### Step 5: In-Place Hardening Mode (Optional)`.

The section must document:

**Primary workflow — single skill file:**

In-place hardening mode activates when the caller passes a single skill file path inside the current worktree.

1. Read the file content as `CURRENT_INSTRUCTIONS`.
2. Run the full RED→BLUE loop (up to 10 rounds) as defined in Step 4. Do NOT commit between rounds — all
   iterations run in-memory against `CURRENT_INSTRUCTIONS` until convergence (`major_loopholes_found: false`)
   or the round cap is reached.
3. Write the final hardened content back to the file using the Edit tool.
4. `git commit` the file exactly once, after convergence, with message:
   `refactor: harden <relative-skill-path> via adversarial TDD (N rounds, M loopholes closed)`.

**Secondary workflow — directory / batch mode:**

If the caller passes a directory path (or `--batch <dir>`) instead of a single file, enumerate all `SKILL.md`
and `first-use.md` files under the directory recursively. Apply the single-skill workflow to each file.
Skills can be processed sequentially (default, safe for shared worktrees) or in parallel (when skills are
independent and each subagent commits only its own file).

Skip files that are not valid skill files (missing Purpose or Procedure sections). If a skill file fails
validation after blue-team patching, log the failure and continue to the next skill.

After all skill files are processed (or user types `abort`), display a batch summary table:

| Skill | Rounds | Loopholes Closed |
|-------|--------|-----------------|
| ...   | ...    | ...             |

### Step 3: Update the Verification checklist

Update the existing TDD checklist item from "3 iterations" to "10 iterations":

```
- [ ] Adversarial TDD loop completed (either converged or 10 iterations reached)
```

Add two new checklist items at the bottom of the `## Verification` section:

```
- [ ] In-place hardening mode produces a single commit per skill after convergence
- [ ] If batch mode was used: summary table shows all skills reviewed with round counts
```

### Step 4: E2E manual verification

After implementing the changes, manually verify:

1. Open `first-use.md` and confirm the round cap reads `10` (not `3`) in both the loop logic and prose.
2. Confirm `Step 5: In-Place Hardening Mode` section is present with primary single-skill workflow, secondary
   batch workflow, skip conditions, and batch summary table subsections.
3. Confirm the Verification checklist references `10 iterations`, the single-commit check, and the batch-mode item.

## Notes

- If issue `add-adversarial-tdd-to-skill-builder` is still open, coordinate: that issue may touch the same Step 4
  section. Rebase onto its branch before merging to avoid conflicts.
- Batch-review mode is additive — it does not change single-skill invocation behavior.
- The 10-round cap is a hard ceiling. The loop still terminates early when red-team returns
  `major_loopholes_found: false`.

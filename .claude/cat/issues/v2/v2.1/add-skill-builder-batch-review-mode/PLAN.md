# Plan: add-skill-builder-batch-review-mode

## Goal

Enhance the skill-builder-agent workflow with two improvements:

1. Increase the adversarial TDD loop hard cap from 3 rounds to 10 rounds.
2. Add a batch-review mode where skill-builder runs RED→BLUE→apply iteratively against all skill files in a single
   worktree, committing fixes in place rather than creating per-skill issues.

## Background

The current skill-builder-agent `first-use.md` (Step 4: Adversarial TDD Loop) caps hardening at 3 iterations.
This cap is too low for complex skills where genuine loopholes persist past round 3. Raising the cap to 10 allows
the loop to converge naturally on well-hardened instructions without artificial truncation.

Additionally, there is no mechanism for bulk skill review. Currently, reviewing multiple skills requires creating a
separate issue for each skill. A batch-review mode would let a practitioner run skill-builder in a checked-out
worktree, iterate RED→BLUE on every skill file, commit the hardened versions in place, and complete the entire
review in a single session without spawning per-skill issues.

## Pre-conditions

- `plugin/skills/skill-builder-agent/first-use.md` exists and contains Step 4 with the 3-round cap.
- The adversarial TDD loop structure (red-team / blue-team subagent prompts) is already present.
- No existing batch-review mode is defined in any skill-builder file.

## Post-conditions

- The adversarial TDD round cap in Step 4 is updated from 3 to 10 in `first-use.md`.
- A new batch-review mode section is documented and implemented in `first-use.md`, covering:
  - Entry condition: caller passes `--batch` flag or specifies a directory of skill files.
  - Iteration: for each skill file in the worktree, run RED→BLUE→apply, then `git commit` the hardened file.
  - Termination: all skills processed, or user aborts.
  - Output: summary of skills reviewed, rounds per skill, and loopholes closed.
- E2E verification: invoke skill-builder on a sample skill and confirm 10-round cap applies; confirm batch-review
  mode instructions allow single-worktree iterative workflow.

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

### Step 2: Add batch-review mode section

Insert a new section `### Step 5: Batch-Review Mode (Optional)` immediately after Step 4 in `first-use.md`.

The section must document:

**Entry condition:**

Batch-review mode activates when the caller passes a directory path (or `--batch <dir>`) instead of a single skill
file. The directory must be inside the current worktree. skill-builder enumerates all `SKILL.md` and `first-use.md`
files under the directory recursively.

**Per-skill loop:**

For each skill file:

1. Read the current file content as `CURRENT_INSTRUCTIONS`.
2. Run the full RED→BLUE loop for this skill (up to 10 rounds) as defined in Step 4. Do NOT commit
   between rounds — all iterations run in-memory against `CURRENT_INSTRUCTIONS` until convergence
   (red-team returns `major_loopholes_found: false`) or the round cap is reached.
3. Write the final hardened content back to the file using the Edit tool.
4. `git commit` the file exactly once, after convergence, with message:
   `refactor: harden <relative-skill-path> via adversarial TDD (N rounds, M loopholes closed)`.
5. Log: skill path, rounds completed, loopholes closed count.

**Termination:**

After all skill files are processed (or user types `abort`), display a batch summary table:

| Skill | Rounds | Loopholes Closed |
|-------|--------|-----------------|
| ...   | ...    | ...             |

**Constraints:**

- Process skills sequentially (not in parallel) to avoid merge conflicts on the same worktree.
- Skip files that are not valid skill files (missing Purpose or Procedure sections).
- If a skill file fails validation after blue-team patching, log the failure and continue to the next skill.

### Step 3: Update the Verification checklist

Add a new checklist item at the bottom of the `## Verification` section:

```
- [ ] If batch mode was used: summary table shows all skills reviewed with round counts
```

Also update the existing TDD checklist item from "3 iterations" to "10 iterations":

```
- [ ] Adversarial TDD loop completed (either converged or 10 iterations reached)
```

### Step 4: E2E manual verification

After implementing the changes, manually verify:

1. Open `first-use.md` and confirm the round cap reads `10` (not `3`) in both the loop logic and prose.
2. Confirm `Step 5: Batch-Review Mode` section is present with entry condition, per-skill loop, termination, and
   constraints subsections.
3. Confirm the Verification checklist references `10 iterations` and includes the batch-mode item.

## Notes

- If issue `add-adversarial-tdd-to-skill-builder` is still open, coordinate: that issue may touch the same Step 4
  section. Rebase onto its branch before merging to avoid conflicts.
- Batch-review mode is additive — it does not change single-skill invocation behavior.
- The 10-round cap is a hard ceiling. The loop still terminates early when red-team returns
  `major_loopholes_found: false`.

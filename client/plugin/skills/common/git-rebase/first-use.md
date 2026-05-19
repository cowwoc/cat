<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Git Rebase Skill

**Purpose**: Safely rebase branches with automatic backup, conflict detection, and recovery guidance.

## project.md Merge Policy Check

**Check project.md for configured merge preferences before rebasing.**

```bash
# Check if Git Workflow section exists in project.md
MERGE_POLICY=$(grep -A10 "### Merge Policy" .cat/project.md 2>/dev/null)

if echo "$MERGE_POLICY" | grep -qi "MUST.*merge commit"; then
  echo "⚠️ WARNING: project.md prefers merge commits over rebase"
  echo "Rebasing may conflict with configured workflow."
  echo ""
  echo "project.md specifies merge commits should be used, which preserves"
  echo "branch history. Rebasing rewrites history to be linear."
  echo ""
  echo "Proceed only if you understand the implications:"
  echo "  - Rebasing will create linear history (no merge commits)"
  echo "  - This overrides the project.md preference"
  echo ""
  echo "To honor project.md preference, use 'git merge --no-ff' instead."
fi
```

## Pre-Rebase Path Consistency Validation

Before creating a backup or starting the rebase, the `git-rebase` tool automatically validates that the worktree's
tracked paths and content references are consistent with the target branch.

If inconsistencies are detected, the tool returns `ERROR` status **before any rebase begins**. No backup is created.
The error message identifies two categories of problems:

- **Tracked-path renames:** Files or directories that were renamed in the worktree branch differ from the target branch
  (e.g., `old/cat` → `.cat`). Rebasing with mismatched paths causes pervasive conflicts.
- **Content references:** File contents reference old path names that no longer exist (e.g., scripts or docs that
  hardcode the old directory name).

The error message lists each affected file and the expected path. This is an actionable preflight failure, not a
terminal rebase result. Before reporting the rebase as blocked, update the current branch so its tracked paths and
content references use the target branch's renamed paths, then rerun the `git-rebase` tool.

When resolving tracked-path renames:
1. Inspect the target branch to identify where each listed old path moved.
2. Move or reapply the current branch's changes onto the corresponding new target-branch path.
3. Remove the old tracked paths from the current branch.
4. Check for content references to the old paths and update them to the new paths.
5. Commit or amend the path-alignment changes as appropriate for the current workflow.
6. Rerun the deterministic `git-rebase` tool.

Only escalate the preflight block to the user after trying the path-alignment recovery or when the mapping is
ambiguous enough that choosing a destination would risk losing work.

### Preflight Block Completion Rule (MANDATORY)

A `decision: block` from pre-rebase path consistency validation is an intermediate state, not terminal completion.

The agent must continue autonomously in this order:
1. Investigate each mismatch and build explicit `src -> dst` mapping.
2. Apply path-alignment changes in the worktree branch.
3. Re-run deterministic `git-rebase`.
4. Repeat until preflight passes or an ambiguity/risk threshold requires user escalation.

Stopping after the first preflight `decision: block` is a workflow failure.

Escalate to user only when at least one condition is true:
- Multiple plausible destinations exist and picking one could lose/overwrite branch-specific work.
- Target-branch mapping cannot be derived from commit intent + tree evidence.
- Additional destructive action would be required to proceed safely.

**Example:** If the target branch renamed `old/cat/` to `.cat/`, the worktree branch must also use `.cat/` in both
tracked paths and file contents. The validation catches this before any history is rewritten.

## Common Operations

### Rebase onto target branch (Deterministic Script)

For deterministic execution with automatic backup and conflict detection:

```bash
"${CAT_PLUGIN_ROOT}/client/bin/git-rebase" "$WORKTREE_PATH" "$TARGET_BRANCH"
```

TARGET_BRANCH is required. The tool outputs JSON.

### Integration Boundary After Rebase

When rebase is part of issue-branch integration, resolve all conflicts in the issue worktree branch during this step.
Do not resolve merge conflicts in `/workspace`.

After rebase and verification succeed in the issue worktree, advance the target branch in `/workspace` using:

```bash
git merge --ff-only <issue-branch>
```

If `--ff-only` fails because the target branch moved, re-run this rebase flow against the updated target branch.

#### Result Handling

The tool outputs one of three JSON formats:

**Success (`status: OK` or `status: CONFLICT`):**
```json
{"status": "OK", "backup_branch": "backup-before-rebase-...", "commits_rebased": 3}
{"status": "CONFLICT", "backup_branch": "backup-before-rebase-...", "conflicting_files": [...]}
```

**Error (`decision: block`):**
```json
{"decision": "block", "reason": "error message\nbackup_branch: backup-before-rebase-..."}
```
When a backup_branch was created before the error, its name appears on a `backup_branch:` line within the reason.

| Status | Meaning | Agent Recovery Action |
|--------|---------|----------------------|
| `OK` | Rebase completed successfully | Report commits rebased, verify no content changes. Delete backup branch (see below). |
| `CONFLICT` | Rebase stopped due to conflicts | The launcher already ran `git rebase --abort`, so no interactive rebase state remains. Start a manual rebase session as documented in **## Handling Conflicts** below, then resolve conflicts. Backup preserved at backup_branch. Delete backup after resolution or abort is complete. |
| `block` decision | Rebase failed (not a conflict) | Parse backup_branch from the reason field if present. Restore from backup if needed. Delete backup after the error is handled. |

**On OK status:** After a successful rebase:
- Delete the backup: `git branch -D <backup_branch>`
- If this rebase was a retry after a prior failed attempt, also delete the prior attempt's backup branch
  (branches matching `backup-before-rebase-*`)
- The backup exists only during verification — leaving it permanently clutters the repository

**On CONFLICT status:** Resolve conflicts using the numbered steps in [Handling Conflicts](#handling-conflicts) below.
After resolution is complete (or error handled): `git branch -D <backup_branch>`

**On block decision:** After handling the error (investigation complete, alternative approach taken, or
issue escalated to user):
- If the reason contains `backup_branch:`, delete it: `git branch -D <branch-name-from-reason>`
- The backup exists only during investigation — leaving it permanently clutters the repository

### Interactive rebase (reorder, edit, squash)

For operations requiring judgment (reordering commits, editing history, complex squashing):

```bash
git rebase -i <base-commit>

# In editor:
# pick   - use commit as-is
# reword - change commit message
# edit   - stop to amend commit
# squash - combine with previous commit
# fixup  - like squash but discard message
# drop   - remove commit
```

### Critical: Hash Format in Rebase Todo

**When generating sed scripts to transform the git rebase todo file, ALWAYS use the abbreviated hash from
`git log --format='%h' -1 <commit>`.** Git rebase -i uses 7-character abbreviated hashes in the todo file. Using a
longer hash (9+ characters) in sed patterns will silently fail to match, causing commits to be replayed as normal picks
instead of squash/fixup.

```bash
# CORRECT: use abbreviated hash matching git rebase -i format
HASH=$(git log --format='%h' -1 "$COMMIT")
sed -i "s/^pick $HASH/fixup $HASH/" "$1"

# WRONG: 9-char or full hash will not match git rebase -i entries
sed -i 's/^pick 27200257c/fixup 27200257c/' "$1"
```

This silent failure is particularly dangerous because `git rebase` reports success even when no substitution occurred.

## Handling Conflicts

**CRITICAL: Persist through conflicts. Never switch to cherry-pick mid-rebase.**

Rebase conflicts are normal and expected when branches have diverged. The solution is to resolve conflicts and continue,
not to abandon rebase for cherry-picking.

**Step 0: Start a manual rebase session (required after launcher `CONFLICT`).**

The deterministic `git-rebase` launcher always aborts before returning `status: CONFLICT`. That means conflict
markers and the interactive rebase state are not preserved. You must start a native rebase session before using
`git rebase --continue`.

```bash
# Use the pinned target from the CONFLICT JSON output if available
PINNED_TARGET="${TARGET_BRANCH}"

# Confirm clean state before starting manual rebase
git status --porcelain

# Start a native rebase session that can be continued interactively
FORK_POINT=$(git merge-base HEAD "$PINNED_TARGET")
git rebase --onto "$PINNED_TARGET" "$FORK_POINT"
```

If this command exits with conflicts, continue with Steps 1-6 below.

**Step 1: Inspect the conflicting files.**

```bash
git status
```

The output lists each conflicting file with its conflict type. The most important types are:

| git status description | Meaning |
|------------------------|---------|
| `both modified` | Both branches changed the file — classic merge conflict with markers |
| `deleted by us` | The commit being replayed (your branch) deleted the file; the target branch still has it |
| `deleted by them` | The target branch deleted the file; the commit being replayed (your branch) still has it |

**Step 2: For each conflicting file, resolve by conflict type.**

**"both modified" (classic conflict):**

```bash
# Edit file to resolve conflict markers (<<<<<<<, =======, >>>>>>>)
# Then stage the resolved file
git add <file>
```

**"deleted by us" — your branch deleted the file, target branch still has it:**

Determine intent: does the current commit's deletion still make sense, or should the file be preserved?

- If your branch intentionally deleted the file (e.g., renamed it, removed a feature): accept the deletion.

  ```bash
  git rm <file>
  ```

- If the file should be preserved (e.g., target branch added important content): keep the target's version.

  ```bash
  git checkout HEAD -- <file>
  git add <file>
  ```

**CRITICAL:** Do NOT `git rm` or delete any file unless you have verified it is the file your branch intentionally
removed. Never delete files outside the conflict set. If unsure, keep the target's version:
`git checkout HEAD -- <file>`.

**"deleted by them" — target branch deleted the file, your branch still has it:**

Determine intent: does the commit being replayed still need the file?

- If the deletion on the target branch is correct (file is gone for a good reason): accept the deletion.

  ```bash
  git rm <file>
  ```

- If your branch has meaningful changes to the file that supersede the deletion: restore your version.

  ```bash
  git checkout REBASE_HEAD -- <file>
  git add <file>
  ```

**Step 3: Stage each resolved file** after all conflict markers are removed.

**Step 4: Continue the rebase after all files in the current commit are resolved.**

```bash
git rebase --continue
```

**Step 5: Repeat Steps 1–4 for each subsequent conflicting commit** until the rebase completes.

**Step 6: Delete the backup branch after successful resolution.**

```bash
git branch -D <backup_branch>
```

**Abort path** (use only when resolution is not feasible and you must restore the original state):

```bash
git rebase --abort
git reset --hard <backup_branch>  # ACKNOWLEDGED
git branch -D <backup_branch>
```

### Conflict Resolution References

**Use explicit ref names instead of `--ours`/`--theirs`.** During rebase, git checks out the target branch and replays
your commits on top. This **inverts** the meaning of `--ours` and `--theirs` compared to merge:

| Context | `--ours` | `--theirs` |
|---------|----------|------------|
| `git merge` | Current branch | Branch being merged in |
| `git rebase` | Target branch (e.g., v2.1) | Commit being replayed (source branch) |

This inversion is a common source of bugs — the agent intends to take the target branch's version but `--theirs`
gives the opposite.

**Use unambiguous references instead:**

| To take... | Use | Avoid |
|------------|-----|-------|
| Target branch version (e.g., v2.1) | `git checkout HEAD -- <file>` | `git checkout --ours <file>` |
| Replayed commit version (your branch) | `git checkout REBASE_HEAD -- <file>` | `git checkout --theirs <file>` |

`HEAD` always points to the target during rebase. `REBASE_HEAD` always points to the commit being replayed. These
references are unambiguous in any context.

```bash
# Take target branch version for a file (e.g., file already merged via another issue)
git checkout HEAD -- path/to/file.java
git add path/to/file.java

# Take your branch's version for a file
git checkout REBASE_HEAD -- path/to/file.java
git add path/to/file.java

# For commits already merged to the target branch, take HEAD's version.
# This makes the commit empty, and git drops it automatically.
```

### "Skipped previously applied" Messages

When rebasing, git may report "skipped previously applied commit" for commits whose changes already exist on the target
branch (perhaps added via separate commits). This is normal - git detects content duplication and skips redundant
commits. Continue the rebase.

### Why Rebase Over Cherry-Pick

| Approach | Pros | Cons |
|----------|------|------|
| Rebase | Preserves linear history, single operation | Must resolve conflicts sequentially |
| Cherry-pick | Can select specific commits | Creates duplicate commits, complex history |

**Rule:** Always complete a rebase. Cherry-pick is only appropriate for extracting a single commit to a different
branch, not for integrating branch changes.

## Safe Rebase Patterns

```bash
# Only rebase local/feature branches (not shared ones)
git rebase main  # While ON main - rewrites shared history!

# Avoid --all flag (rewrites ALL branches)
git rebase --all  # Rewrites ALL branches!

# SAFE - rebase feature branch onto main
git checkout feature
git rebase main
```

## Error Recovery

If rebase went wrong:
- Abort rebase: `git rebase --abort`
- Restore from backup: `git reset --hard $BACKUP  # ACKNOWLEDGED`
- Check reflog: `git reflog` to find the target entry, resolve to a commit hash (avoids TOCTOU),
  then reset to the resolved hash:
  ```bash
  RESTORE_COMMIT=$(git rev-parse HEAD@{N})  # resolve once — positional N is stable at this point
  git show "$RESTORE_COMMIT" --stat         # verify it is the correct commit
  git reset --hard "$RESTORE_COMMIT"  # ACKNOWLEDGED
  ```

## Verification After Amend/Fixup Operations

**CRITICAL: When using rebase to amend or fixup an earlier commit, verify the target commit actually contains the
expected changes.**

```bash
# After rebase completes, verify the target commit has expected files
TARGET_COMMIT="<original-hash>"  # Note: hash changes after rebase!

# Find new commit with same message
NEW_COMMIT=$(git log --oneline --all | grep "<partial-message>" | head -1 | cut -d' ' -f1)

# Verify it contains expected files
git show "$NEW_COMMIT" --stat

# Check specific file exists in commit
git show "$NEW_COMMIT" -- path/to/expected/file.md

# If file is NOT in the commit, the amend/fixup FAILED silently
```

**Common failure mode:** Rebase reports "Successfully rebased" but the fixup commit was dropped due to
conflicts. Always verify the target commit's contents before proceeding.

## Success Criteria

- [ ] Backup created before rebase
- [ ] Working directory was clean
- [ ] Conflicts resolved (if any)
- [ ] History looks correct
- [ ] No commits lost
- [ ] **Target commit contains expected changes (for amend/fixup)**
- [ ] Backup removed after verification

## Semantic Porting Delegation and Enforcement (MANDATORY)

Rebase completion requires semantic migration coverage review for current-branch files touched by base semantic changes.

### Delegation Boundary

Delegate only analysis/reporting tasks to delegated agents:
- Semantic intent extraction from base commits (commit message + diff).
- Candidate target discovery from the union of files touched by rebased commits (commit-level touched-file set),
  not only final net diff vs target.
- Deep triage report with per-hit classification: `PORT`, `KEEP`, `UNCERTAIN` and rationale.
- Post-port verification report (coverage and residual-risk summary).

Delegation enforcement:
- If delegated agent tooling is available, these analysis/reporting tasks must be delegated.
- Main agent may perform them directly only if delegated agents are unavailable/blocked; this exception must be recorded with
concrete blocker evidence.

Delegation acceptance gate (MANDATORY):
- Treat delegation as successful only when the delegated agent returns concrete analysis artifacts (intent map, candidate
  inventory, and `PORT`/`KEEP`/`UNCERTAIN` triage with rationale).
- If the delegated agent response is non-executing (for example generic "what do you want me to do next", missing artifact,
  empty report, or off-task output), mark it as delegation failure for this attempt.
- On delegation failure, retry with one clarified follow-up; if it fails again, replace with a new delegated agent attempt.
- If repeated attempts fail, record blocker evidence (agent ids + failure outputs) before main-agent fallback.

Delegated output correctness checks (MANDATORY):
- Reject delegated output that redefines or infers pinned inputs (for example replacing provided `old_merge_base`/
  `new_merge_base` with different refs or `origin/main`-derived values).
- Reject delegated output that computes candidate inventory from the wrong range (must be commit-union from `old_mb..HEAD`
  for this branch run, not net diff and not unrelated branch roots).
- Reject delegated output that marks files as PORT/KEEP/UNCERTAIN when those files are outside the required candidate
  inventory for the run.
- Reject delegated output that provides file:line hints not present in the current working tree content.
- On rejection, classify `delegation_failure=invalid_analysis`, record why, and continue startup protocol
  (retry/replacement/blocker path).

Delegated agent startup protocol (MANDATORY):
- Spawn delegation agents using the engine's isolated/no-history mode with a minimal bootstrap message.
- Do not treat spawn-turn output as execution; treat it as startup-only.
- Trigger execution using the engine's delegated-agent follow-up mechanism with explicit concrete deliverables.
- If first follow-up response is non-executing, send one final execution follow-up.
- If second follow-up response is still non-executing, terminate that attempt.
- Replacement attempt must use a fresh delegated agent id and restate concrete deliverables.
- After two failed agent attempts, classify delegation as blocked for this run and continue with blocker-exception flow.

Bootstrap-loop detector (MANDATORY):
- If two delegated-agent attempts in the same run both return startup acknowledgements without artifacts, classify
  `delegation_blocker=bootstrap_loop` and stop spawning more delegated agents for that run.
- Do not run third+ delegated-agent attempts after `bootstrap_loop` is detected.
- Proceed via blocker-exception fallback once and record all failed attempt transcripts.

Do not delegate history-rewrite execution tasks:
- `git rebase` execution.
- Rebase conflict resolution.
- Final apply/revert decisions that rewrite branch history.

Main agent remains accountable for all history mutations.

### Required Workflow

1. Verify pre-rebase starting point (MANDATORY):
   - If the user requested starting from a specific baseline (pre-rebase, pre-merge, pre-squash, or explicit ref),
     restore to that baseline before any history rewrite step.
   - Record evidence: `restored_head`, requested baseline, restore source (branch/tag/reflog/commit), and target branch.
   - Validate the restored state matches the requested baseline and intended operation preconditions.
   - If validation fails or baseline evidence is ambiguous, stop and correct the baseline before continuing.
2. Build semantic intent map from the pinned merge-base delta range:
   - Capture `old_merge_base` before starting rebase.
   - Determine `new_merge_base` after rebase target is selected/resolved.
   - Scan semantic intents from `(old_merge_base, new_merge_base]` only.
   - Do not substitute an unpinned `merge-base..target` shortcut.
3. Build candidate inventory from rebased-commit touched files:
   - Use the union of paths touched by commits being replayed/rebased.
   - Include candidate files even if they are absent from final `target..HEAD` net diff.
   - Do not scope candidate discovery to net tree diff alone.
4. Request delegated deep triage of candidate targets.
   - Record delegation evidence (delegated agent id/name + returned artifact/report path/summary).
   - Validate output via delegation acceptance gate before proceeding.
   - Apply delegated agent startup protocol before accepting blocker-exception fallback.
   - Apply bootstrap-loop detector and short-circuit repeated non-executing agent attempts.
   - Apply delegated output correctness checks; do not consume invalid delegated analysis.
5. Review triage output and resolve all high-confidence candidates.
6. Apply all required `PORT` changes in the current branch.
7. Run delegated post-port coverage verification.
8. Finish rebase flow only after semantic coverage gate passes.

### Semantic Coverage Gate

Fail the workflow if either condition holds:
- Any high-confidence candidate remains `UNCERTAIN` without further investigation.
- Any high-confidence `PORT` candidate remains unapplied.
- Semantic-intent extraction did not use the pinned range `(old_merge_base, new_merge_base]`.
- Candidate discovery was scoped only to final net diff and omitted rebased-commit touched files.
- Requested pre-rebase restore was not verified with concrete restore evidence before rebase start.
- Delegation-required analysis was performed in main agent without recorded delegated agent evidence or blocker exception.
- Delegated output failed acceptance gate and workflow continued without retry/replacement or blocker evidence.
- Delegated agent startup protocol was not followed before declaring delegation blocked.
- Bootstrap-loop was detected but additional delegated-agent attempts were still made.
- Invalid delegated analysis was accepted without correctness-check rejection.

Trust policy for applying semantic ports:
- `trust = low`: ask user before applying `PORT` changes.
- `trust >= medium`: apply `PORT` changes autonomously.
- `trust = high`: do not prompt for permission to continue workflow.

### User Notification Rule

Only notify user about semantic porting when semantic ports were applied.
Notification must include:
- Semantic intent(s) applied.
- Files changed.
- Ambiguous `KEEP` decisions (if any).

If no semantic ports were applied, do not emit semantic-port warning/prompt.

### Semantic Porting Completion Gate (MANDATORY)

Do not report rebase complete until all of the following evidence is produced:
- Pre-rebase restore evidence (`restored_head`, restore source, target branch).
- Semantic intent map (base commits reviewed + extracted intents).
- Candidate inventory from rebased-commit touched-file union.
- Per-candidate triage table with `PORT` / `KEEP` / `UNCERTAIN` and rationale.
- Applied port patch list (or explicit `none applied` with justification).
- Post-port verification output proving coverage gate pass.
- Delegation evidence for analysis/reporting phases, or explicit blocker exception evidence if delegation was unavailable.
- Delegation acceptance evidence (or failure/retry logs plus blocker exception evidence).
- Delegated agent startup transcript evidence (initial response + follow-up response + replacement response, when applicable).
- Delegation blocker classification when blocked (for example `bootstrap_loop`).
- Delegated output correctness-check evidence (pass/fail + rejection reasons when failed).

If any evidence item is missing, the workflow is incomplete and must continue.

### Rebase-Finish Is Not Completion (MANDATORY)

A successful `git rebase` finish (including conflict resolution) is only a history-rewrite milestone.
Do not report the `git-rebase` workflow as complete until all semantic-porting gates and evidence requirements pass.

If rebase finishes but semantic intent/triage/verification has not run:
- Status must be reported as `in_progress` (not complete).
- Continue immediately with semantic-porting workflow steps.
- Do not exit the skill after rebase-only success.

### Final Completion Checklist Addendum

In addition to Success Criteria, require:
- [ ] Pre-rebase restore verified (when requested).
- [ ] Semantic intent map completed.
- [ ] Candidate triage completed (`PORT` / `KEEP` / `UNCERTAIN`).
- [ ] All high-confidence `PORT` items applied or escalated per trust policy.
- [ ] No unresolved high-confidence `UNCERTAIN` items remain.
- [ ] Post-port coverage verification recorded.
- [ ] Delegation evidence recorded (or blocker exception documented).
- [ ] Delegation acceptance gate satisfied (or failure/retry evidence documented).
- [ ] Delegated agent startup protocol evidence recorded.
- [ ] Delegation blocker classification recorded when fallback was used.
- [ ] Delegated output correctness checks recorded.

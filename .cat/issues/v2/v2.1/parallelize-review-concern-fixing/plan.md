# Plan: parallelize-review-concern-fixing

## Goal

Parallelize the review phase concern-fixing workflow so that each stakeholder concern is assigned to a dedicated
fix subagent running simultaneously in its own isolated worktree branch. After all subagents complete, their commits
are collected and merged into the issue branch before re-verification runs. This replaces the current single
sequential fix-subagent approach with a true parallel architecture.

## Parent Requirements

None

## Impact Notes

`2.1-move-review-artifacts-to-cat-work` modifies the same
`plugin/skills/work-review-agent/first-use.md` file to update `.cat/review/` path references. User confirmed
these changes are independent and no ordering dependency is required. The parallel concern-fixing architecture is a
significant behavioral change from the current sequential single-subagent approach.

## Approaches

### A: Parallel Worktree per Concern (Chosen)

- **Risk:** HIGH
- **Scope:** 1 file (work-review-agent/first-use.md)
- **Description:** Each concern fix subagent is spawned simultaneously via the Task tool in a single message,
  working in a dedicated temporary worktree branch (e.g., `fix-concern-N-<issue_branch>`). After all complete,
  the main agent merges each worktree branch into the issue branch using `git cherry-pick` or `git merge`,
  handles conflicts using a defined strategy, then cleans up temporary worktrees and branches.

### B: Sequential Fix Subagents per Concern (Current/Rejected)

- **Risk:** LOW
- **Scope:** 0 files (no change)
- **Description:** Keep the current single subagent that addresses all concerns together. Rejected because it
  cannot exploit parallelism for large concern sets and is the current implementation.

### C: Parallel Subagents Sharing the Same Worktree (Rejected)

- **Risk:** HIGH
- **Scope:** 1 file
- **Description:** Spawn N subagents all working in the same issue worktree. Rejected because concurrent writes
  to the same files without coordination cause race conditions, corrupt commits, and unpredictable merge failures.
  Isolated worktrees per subagent are required.

> Approach A is chosen because it provides true isolation per concern (preventing cross-concern file corruption),
> enables genuine parallelism (all subagents run simultaneously), and follows CAT's existing worktree isolation
> patterns (as used by wave-parallel implementation subagents). The merge step after subagents complete maps
> directly to existing `git merge` tooling already used in the merge phase.

## Risk Assessment

- **Risk Level:** HIGH
- **Concerns:**
  - Two concern fix subagents may modify the same file — requires a defined merge conflict resolution strategy
  - Temporary worktrees accumulate if a subagent crashes mid-execution — requires cleanup on failure paths
  - The main review agent must coordinate N async subagents before re-running stakeholder review — increases
    orchestration complexity
  - Conflict resolution via sequential cherry-pick may fail on pathological diffs — requires fallback to
    sequential retry
- **Mitigation:**
  - Define a clear merge conflict resolution strategy: sequential `git cherry-pick` per concern branch, using
    `--strategy-option theirs` for last-write-wins on conflicts, with per-file conflict logging
  - Wrap worktree creation and cleanup in explicit steps; clean up on both success and failure paths
  - Subagent failure fallback: if a concern fix subagent returns FAILED or produces no commits, the main agent
    retries that concern sequentially (up to 1 retry) before escalating to the user

## Files to Modify

- `plugin/skills/work-review-agent/first-use.md` — Replace the auto-fix loop's single planning + implementation
  subagent pair with a parallel-subagent orchestration protocol. Specifically, replace Steps 3 and 4 of the
  auto-fix loop (spawn planning subagent + spawn implementation subagent) with:
  1. A shared planning subagent (still sequential) that produces a per-concern fix plan
  2. N simultaneous concern fix subagents spawned in a single Task tool message, each in an isolated worktree
  3. A post-subagent merge step that integrates all concern worktree branches into the issue branch
  4. Cleanup of temporary worktrees and branches

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update `plugin/skills/work-review-agent/first-use.md` to implement the parallel concern-fixing protocol.
  - Files: `plugin/skills/work-review-agent/first-use.md`

  **Exact changes required:**

  #### 1. Locate the auto-fix loop section

  Find the section header `**Auto-fix loop for concerns marked as FIX:**` (approximately line 380 in the current
  file). The changes below replace everything inside the loop from step 3 (planning subagent) through step 4
  (implementation subagent), while preserving all surrounding text including:
  - The loop preamble (`AUTOFIX_ITERATION=0`, `While FIX-marked concerns exist...`)
  - Priority ordering rule
  - Step 1 (increment `AUTOFIX_ITERATION++`)
  - Step 2 (construct `concerns_formatted` and `detail_file_paths`)
  - Step 5 (validate implementation subagent output)
  - Step 6 (persistent concern tracking)
  - Steps 7–10 (re-run review, merge prior concerns, parse, continue loop)
  - All text after the loop: Evaluate Remaining Concerns, Step 6 Deferred Concern Review, Return Result

  #### 2. Replace the planning subagent (step 3) with a shared planning subagent

  The shared planning subagent is still sequential (one per loop iteration) and produces a per-concern fix plan
  structured so each concern's instructions are self-contained. Replace the existing step 3 Task tool block with:

  ```
  3. Spawn a shared planning subagent to produce a per-concern fix plan:
     ```
     Task tool:
       description: "Plan per-concern fixes (iteration ${AUTOFIX_ITERATION})"
       subagent_type: "cat:work-execute"
       model: "sonnet"
       prompt: |
         Analyze the following stakeholder review concerns for issue ${ISSUE_ID} and produce a
         per-concern fix plan. Each concern MUST have a self-contained section with all information
         a separate subagent would need to implement the fix without additional context.

         ## Issue Configuration
         ISSUE_ID: ${ISSUE_ID}
         WORKTREE_PATH: ${WORKTREE_PATH}
         BRANCH: ${BRANCH}
         TARGET_BRANCH: ${TARGET_BRANCH}

         ## Concerns to Analyze (CRITICAL first)
         ${concerns_formatted}

         ## Concern Detail Files
         ${detail_file_paths}

         ## Instructions
         - CRITICAL concerns MUST be addressed first (list them first in the plan).
         - For each concern produce a self-contained section with:
           1. Exact file path(s) to modify
           2. What the current code does (quote the relevant lines if possible)
           3. What the fixed code must look like (write out the replacement)
           4. Why this fixes the concern
         - If a concern cannot be fixed with a code change, mark it UNFIXABLE and explain why.
         - Do NOT implement the fixes — only plan them.

         ## Return Format
         Return a fix plan with one section per concern:

         ### Concern N: [severity] [brief description]
         - Files: [exact file paths]
         - Current code: [quote or describe]
         - Fixed code: [replacement or description of change]
         - Rationale: [why this fixes the concern]
         [or: UNFIXABLE: [reason]]
     ```
  ```

  #### 3. Replace the implementation subagent (step 4) with N parallel concern fix subagents

  Delete the existing step 4 Task tool block entirely. Replace with the following multi-part step 4:

  ```
  4. **Spawn N parallel concern fix subagents (one per FIX-marked concern):**

     **Worktree naming convention:** For each concern N (1-indexed, ordered by severity CRITICAL first), create
     a temporary branch name: `fix-concern-${AUTOFIX_ITERATION}-${N}-${BRANCH}`.

     Before spawning subagents, create one isolated worktree per concern using the main agent's Bash tool:

     ```bash
     # For each concern N (1-indexed):
     CONCERN_BRANCH="fix-concern-${AUTOFIX_ITERATION}-${N}-${BRANCH}"
     CONCERN_WORKTREE="${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/${CONCERN_BRANCH}"
     cd "${WORKTREE_PATH}" && \
       git worktree add -b "${CONCERN_BRANCH}" "${CONCERN_WORKTREE}" HEAD
     ```

     Run worktree creation for all N concerns in a single chained Bash call (create all before spawning any
     subagent). If worktree creation fails for concern N, do NOT spawn that concern's subagent — mark it as
     FAILED immediately and include it in the failed-concerns list.

     **Spawn all N concern fix subagents in a single message** (not sequentially — use N simultaneous Task tool
     calls in one response):

     For each concern N, the delegation prompt is:

     ```
     Task tool:
       description: "Fix concern ${N}: ${concern_brief_description} (iteration ${AUTOFIX_ITERATION})"
       subagent_type: "cat:work-execute"
       model: "sonnet"
       prompt: |
         Fix the following stakeholder review concern for issue ${ISSUE_ID}.

         ## Issue Configuration
         ISSUE_ID: ${ISSUE_ID}
         WORKTREE_PATH: ${CONCERN_WORKTREE_N}
         BRANCH: ${CONCERN_BRANCH_N}
         TARGET_BRANCH: ${TARGET_BRANCH}

         ## Your Assigned Concern (ONLY fix this concern — do NOT modify files unrelated to it)
         ${concern_N_formatted}

         ## Fix Plan for This Concern
         ${fix_plan_section_N}

         ## Concern Detail File (if applicable)
         ${concern_N_detail_file_path}

         ## Instructions
         - Work exclusively in the worktree at ${CONCERN_WORKTREE_N}.
         - Fix ONLY the files listed in the fix plan for this concern.
         - Do NOT modify any file not referenced in the fix plan section above.
         - Read concern detail file for full context (if path provided and file exists).
         - Commit your fix using the same commit type as the primary implementation (e.g., bugfix:,
           feature:). These commits will be merged into the main implementation branch.
         - Use git commit without --no-verify.
         - **ABSOLUTE PROHIBITION:** Do NOT read, write, or modify `.cat/config.json` for any reason.

         ## Return Format
         ```json
         {
           "status": "SUCCESS|PARTIAL|FAILED",
           "commits": [{"hash": "...", "message": "...", "type": "..."}],
           "files_changed": N,
           "concern_addressed": true|false
         }
         ```
     ```

     Each subagent receives only the fix plan section for its assigned concern (not the full plan). The
     `${CONCERN_WORKTREE_N}` and `${CONCERN_BRANCH_N}` values are specific to concern N.

  4b. **Wait for all N concern fix subagents to complete.**

     Collect results from all N subagents. For each subagent result:
     - `SUCCESS`: subagent produced at least one commit touching concern-relevant files.
     - `PARTIAL`: subagent produced some commits but not all concern files were touched.
     - `FAILED` or no commits: subagent failed to produce any useful change.

  4c. **Merge concern worktree branches into the issue branch:**

     Perform all merges sequentially from the ISSUE branch (not from individual concern worktrees). Use the
     main agent's Bash tool:

     ```bash
     cd "${WORKTREE_PATH}"
     # For each concern N whose subagent returned SUCCESS or PARTIAL (in severity order: CRITICAL first):
     CONCERN_BRANCH="fix-concern-${AUTOFIX_ITERATION}-${N}-${BRANCH}"
     MERGE_RESULT=$(git merge --no-ff "${CONCERN_BRANCH}" \
       -m "merge: concern ${N} fixes (${CONCERN_BRANCH})" 2>&1)
     if [[ $? -ne 0 ]]; then
       # Conflict detected — apply last-write-wins strategy per file
       git checkout --theirs . && git add -A
       git commit -m "merge: concern ${N} fixes (conflict resolved via --theirs)"
       echo "WARNING: Merge conflict for concern ${N} resolved via last-write-wins (--theirs)."
     fi
     ```

     Log a warning for every conflict resolved via `--theirs`. After all merges complete, the issue worktree
     (`WORKTREE_PATH`) has all concern fixes applied.

     If a concern's subagent returned FAILED or produced no commits, do NOT attempt to merge its branch.
     Instead, apply the fallback strategy for that concern (see step 4d).

  4d. **Fallback for failed concern fix subagents:**

     For each concern whose fix subagent returned FAILED or produced no commits:
     - Retry the concern ONCE using a sequential fix subagent (same Task tool format as step 4 but for one
       concern, running in the ORIGINAL issue worktree `${WORKTREE_PATH}` on the main `${BRANCH}`).
     - If the sequential retry also fails, escalate to the user via AskUserQuestion:
       ```
       AskUserQuestion tool:
         question: "Concern fix subagent failed twice for concern:

         [concern_N_formatted]

         How should this be handled?"
         options:
           - "Skip this concern (add to deferred)"
           - "Abort review phase"
       ```
     - "Skip this concern": move concern N to `DEFERRED_CONCERNS`. If severity is CRITICAL or HIGH, create a
       tracking issue via `/cat:add-agent` per the severity × patience matrix.
     - "Abort review phase": STOP and return `status: "CONCERNS_FOUND"`, all unresolved FIX concerns moved to
       `deferred_concerns`.

  4e. **Clean up temporary worktrees and branches:**

     After all merges (and fallback handling) complete — whether successful or not — clean up ALL temporary
     worktrees created for this iteration:

     ```bash
     cd "${WORKTREE_PATH}"
     # For each concern N in this iteration:
     CONCERN_BRANCH="fix-concern-${AUTOFIX_ITERATION}-${N}-${BRANCH}"
     CONCERN_WORKTREE="${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/${CONCERN_BRANCH}"
     git worktree remove --force "${CONCERN_WORKTREE}" 2>/dev/null || true
     git branch -D "${CONCERN_BRANCH}" 2>/dev/null || true
     ```

     Run cleanup for all N concerns in a single chained Bash call. Cleanup failures are non-fatal (use `|| true`)
     but should be logged as warnings:
     `"WARNING: Failed to clean up worktree/branch for concern ${N}: ${CONCERN_BRANCH}"`

     Push the issue branch after all merges and cleanup:
     ```bash
     cd "${WORKTREE_PATH}" && git push origin "${BRANCH}"
     ```
     Apply the same push retry protocol as other worktree push operations (3 attempts with `git pull --rebase`
     on non-fast-forward rejection).

  4f. **Collect all new commits for `ALL_COMMITS_COMPACT` update:**

     After pushing, run:
     ```bash
     cd "${WORKTREE_PATH}" && git log --oneline "${TARGET_BRANCH}..HEAD"
     ```
     Parse the new commit hashes and append them to `ALL_COMMITS_COMPACT` (format: `hash:type`). The commit type
     for concern fix merges is `bugfix:` (or the primary implementation type if known).
  ```

  #### 4. Preserve all other auto-fix loop steps unchanged

  Steps 5 (validate implementation subagent output), 6 (persistent concern tracking), 7 (re-run stakeholder
  review), 8 (merge prior unresolved concerns), 9 (parse new review result), and 10 (continue loop) remain
  unchanged. The validation in step 5 must be adapted to check commits from each concern's branch (not a single
  subagent's commits) — use the merged commits in the issue branch after step 4e for `git diff --name-only`
  checks.

  #### 5. Update the noop/partial-noop detection for parallel subagents

  In the noop detection logic (within the `NOOP_ITERATIONS` / `PARTIAL_NOOP_ITERATIONS` tracking), "actionable
  changes" means the total number of concerns for which at least one concern fix subagent returned SUCCESS
  (not FAILED). Concerns retried sequentially that succeed count as actionable. Concerns that fail both the
  parallel and sequential attempts count as non-actionable.

  #### 6. Single-concern optimization

  When there is exactly ONE FIX-marked concern in the current iteration, skip the parallel worktree protocol
  entirely. Instead, use the existing sequential fix subagent path (spawn one implementation subagent running
  in the original issue worktree `${WORKTREE_PATH}` on the main `${BRANCH}`) — the same Task tool format used
  in the original step 4. This avoids the overhead of creating, merging, and cleaning up a worktree for a
  single concern. The planning subagent (step 3) still runs and produces a fix plan; only the implementation
  step changes. Skip steps 4a–4f and proceed directly to step 5 (validate implementation subagent output)
  using commits from the implementation subagent's return value.

  When there are TWO OR MORE FIX-marked concerns, use the full parallel worktree protocol (steps 4a–4f).

  #### 7. Scope isolation enforcement

  The delegation prompt for each concern fix subagent already includes:
  `"Fix ONLY the files listed in the fix plan for this concern. Do NOT modify any file not referenced in the fix
  plan section above."`

  Additionally, after step 4b (all subagents complete), validate scope isolation. The allowed files for concern
  N are extracted from the planning subagent's output by parsing lines matching `- Files: <paths>` within the
  `### Concern N:` section. Specifically:

  ```bash
  # For each concern N that returned SUCCESS or PARTIAL:
  CONCERN_BRANCH="fix-concern-${AUTOFIX_ITERATION}-${N}-${BRANCH}"
  # Extract "- Files: ..." line(s) from the planning subagent's Concern N section
  # (grep between "### Concern N:" and the next "### Concern" or end of plan output)
  ALLOWED_FILES=$(echo "${fix_plan_output}" | \
    awk "/^### Concern ${N}:/,/^### Concern [0-9]/" | \
    grep "^- Files:" | sed 's/- Files:[[:space:]]*//' | tr ',' '\n' | tr -d ' ')
  ACTUAL_FILES=$(cd "${WORKTREE_PATH}" && git diff --name-only HEAD "${CONCERN_BRANCH}")
  # For each file in ACTUAL_FILES, check if it appears in ALLOWED_FILES:
  while IFS= read -r actual_file; do
    if ! echo "${ALLOWED_FILES}" | grep -qF "${actual_file}"; then
      echo "WARNING: Concern ${N} fix subagent modified out-of-scope file: ${actual_file}. Allowed: ${ALLOWED_FILES}"
    fi
  done <<< "${ACTUAL_FILES}"
  ```
  Out-of-scope file warnings do NOT block the merge — they are logged for auditability.

- Run `mvn -f client/pom.xml test` after making the change to verify no existing Java tests are broken by the
  skill file update (the test suite validates skill file structure and content patterns).
  - Files: `client/pom.xml` (read-only reference for test runner command)

## Post-conditions

- [ ] Each identified stakeholder concern is assigned to its own dedicated fix subagent
- [ ] All concern fix subagents are spawned simultaneously (in a single message, not sequentially)
- [ ] After all subagents complete, their commits are collected and merged into the issue branch before the verify
  phase re-runs
- [ ] Each concern fix subagent works in an isolated worktree (separate from the main issue worktree) and commits
  changes there
- [ ] When two or more concern fix subagents modify the same file, last-write-wins (`--theirs`) is applied with a
  logged warning
- [ ] Each concern fix subagent is scoped exclusively to its assigned concern — scope violations are logged
- [ ] Temporary worktrees created for concern fix subagents are cleaned up (worktree removed, branch deleted)
  after their commits are successfully merged into the issue branch
- [ ] If a concern fix subagent fails (returns FAILED or produces no commits), it is retried sequentially once;
  if the retry also fails, the user is asked to skip or abort
- [ ] Standard: `mvn -f client/pom.xml test` passes with no regressions
- [ ] E2E: Run `/cat:work` on an issue with multiple review concerns; verify multiple parallel subagents are
  spawned simultaneously (in a single message) and their changes are integrated successfully into the issue branch

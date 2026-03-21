# Plan: Add Criteria Verification Gate to Potentially-Complete Closure Path

## Goal

Add a `verify-implementation` gate to the "Already complete" closure path in `work/first-use.md` so that
when an issue is identified as `potentially_complete`, the workflow independently verifies all PLAN.md
post-conditions before allowing closure. This prevents shallow commit analysis from incorrectly closing
issues whose implementation is incomplete.

## Type

feature

## Parent Requirements

None

## Background

M569 was caused by the `potentially_complete` handling in `work/first-use.md` using only a shallow git
commit analysis (reading diffs of `suspicious_commits`) to determine whether an issue is complete.
When the user selects "Already complete", the skill immediately closes the issue (STATE.md → closed,
merge, cleanup) without running `verify-implementation` against the PLAN.md post-conditions. This means
a partially-implemented or incorrectly-identified issue can be closed prematurely.

The `work-confirm-agent` already runs `verify-implementation` correctly during the standard implement →
confirm → review → merge flow. The gap is exclusively in the `potentially_complete` bypass path that
skips the entire confirm phase.

## Approaches

### A: Gate in `work/first-use.md` before "Already complete" closure
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Modify the "Already complete" sub-path in `work/first-use.md` to invoke
  `verify-implementation` (via the `cat:verify-implementation-agent` skill) before updating STATE.md.
  Block closure if verification returns INCOMPLETE; show which criteria failed. This is surgically
  targeted at the exact gap.

### B: Separate gate skill invoked by `work-with-issue-agent`
- **Risk:** MEDIUM
- **Scope:** 3 files (moderate)
- **Description:** Add a new pre-confirm gate in `work-with-issue-agent` that runs before the confirm
  phase and separately handles the `potentially_complete` case. Wider surface area, more refactoring.

> **Chosen approach: A.** The gap is in `work/first-use.md` (the `potentially_complete` "Already complete"
> path). Approach A adds the gate at exactly that point with minimal blast radius. The `verify-implementation`
> skill already exists and has a clean invocation contract. No new phase or skill is needed.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The verify subagent requires a worktree with the implementation already present; the
  `potentially_complete` path does have a worktree available. However, the worktree may not have the
  suspicious commits merged into it yet — they exist on `target_branch`, not in the worktree HEAD.
  The verify skill reads from `WORKTREE_PATH`, so we must ensure the suspicious commits are visible
  there (see Wave 1 notes).
- **Mitigation:** The fix includes a step to cherry-pick or reference the suspicious commits through
  the worktree's `target_branch` reference before invoking verify. The verify skill uses file paths
  not git history, so reading via the worktree works as long as the files exist there. Since the
  `potentially_complete` path creates a worktree branched from `target_branch` (which contains the
  suspicious commits), the files are present.

## Files to Modify

- `plugin/skills/work/first-use.md` — Add `verify-implementation` gate inside the "Already complete"
  sub-path, between the user selecting "Already complete" and the STATE.md update step. Block with
  user-visible failure message if verification finds unmet criteria.

## Pre-conditions

- [ ] All dependent issues are closed

## Main Agent Waves

- /cat:instruction-builder-agent plugin/skills/work/first-use.md

## Sub-Agent Waves

### Iteration 1 (Pre-existing Compilation Fix)

**PRIORITY:** This fix must be completed before the main implementation (Iteration 0) can proceed. The compilation error blocks the test suite.

- Wrap Process in try-with-resources at line 155 of GitMergeLinear.java to match the pattern at line 212.
  The checkFastForwardPossible() method creates a Process but does not properly close it. Change:
  ```java
  Process process = pb.start();
  int exitCode = process.waitFor();
  ```
  to:
  ```java
  try (Process process = pb.start())
  {
    int exitCode = process.waitFor();
  }
  ```
  This ensures the Process resource is properly closed, resolving the compilation error:
  "Process is not an interface" at line 212.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitMergeLinear.java`

- Verify the fix by running tests:
  ```bash
  cd "${WORKTREE_PATH}" && mvn -f client/pom.xml test
  ```
  All tests must pass (exit code 0) before proceeding to Iteration 0.
  - Files: `client/pom.xml`

- Commit the fix:
  ```bash
  cd "${WORKTREE_PATH}" && git add client/src/main/java/io/github/cowwoc/cat/hooks/util/GitMergeLinear.java && git commit -m "bugfix: wrap Process in try-with-resources in GitMergeLinear.checkFastForwardPossible()"
  ```

### Wave 1

- Read `plugin/skills/work/first-use.md` (lines 240–292) to understand the current "Potentially
  Complete Handling" section precisely
  - Files: `plugin/skills/work/first-use.md`

- In the "Already complete" implementation block (currently steps 1–6 at lines ~281–288), insert a
  new verification gate **before** step 1 (before "Update STATE.md"). The gate must:
  1. Output a user-visible banner: `"Verifying post-conditions before closing ${issue_id}..."`
  2. Build the commits JSON array from `suspicious_commits` (a space-separated list of hex hashes
     from the `work-prepare` output parsed in Phase 1), then invoke `cat:verify-implementation-agent`.
     Build the commits JSON array with a Bash step first:
     ```bash
     COMMITS_JSON="["
     first=true
     for hash in ${suspicious_commits}; do
       [[ "$first" == "true" ]] || COMMITS_JSON="${COMMITS_JSON},"
       COMMITS_JSON="${COMMITS_JSON}{\"hash\":\"${hash}\",\"message\":\"suspicious commit\",\"type\":\"feature\"}"
       first=false
     done
     COMMITS_JSON="${COMMITS_JSON}]"
     echo "COMMITS_JSON=${COMMITS_JSON}"
     ```
     Then invoke the skill using the JSON args format (consistent with how `work-confirm-agent` invokes
     it — `${ARGUMENTS}` is read as a JSON blob, no catAgentId prefix):
     ```
     Skill tool:
       skill: "cat:verify-implementation-agent"
       args: |
         {
           "issue_id": "${issue_id}",
           "issue_path": "${issue_path}",
           "worktree_path": "${WORKTREE_PATH}",
           "execution_result": {
             "commits": ${COMMITS_JSON},
             "files_changed": 0
           }
         }
     ```
     If `suspicious_commits` is empty, pass `"commits": []` and `"files_changed": 0`.
     Note: The Skill tool is a separate tool call — shell `cd` state does not affect it. Pass all
     paths explicitly via the JSON args as shown above.
  3. Parse the verification result. The skill outputs a structured report with an overall assessment
     of `COMPLETE`, `PARTIAL`, or `INCOMPLETE`.
  4. **If verification returns `COMPLETE`:** Output `"All post-conditions verified."` and proceed to
     existing step 1 (STATE.md update).
  5. **If verification returns `PARTIAL` or `INCOMPLETE`:** Output the full verification report
     (which criteria are Missing/Partial and why). Then STOP — do NOT update STATE.md, do NOT merge.
     Display to the user:
     ```
     BLOCKED: ${issue_id} cannot be closed — ${N} post-condition(s) are unmet.
     Review the criteria above, then either:
       - Fix the missing implementation and re-run /cat:work
       - Select "Not complete, continue" to run the full implement→confirm→review→merge workflow
     ```
     Release the lock: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"`
     Clean up worktree by invoking:
     ```
     Skill tool:
       skill: "cat:safe-rm-agent"
       args: "${CAT_AGENT_ID} ${WORKTREE_PATH}"
     ```
     Then stop — do not proceed further.
  6. **If the verify skill itself fails** (non-zero exit or unparseable output): STOP immediately
     with a clear error:
     ```
     FAIL: verify-implementation-agent failed for ${issue_id}.
     Cannot close issue without verified post-conditions.
     Error: <error message>
     ```
     Release lock via `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"`,
     then invoke `/cat:safe-rm-agent` with args `"${CAT_AGENT_ID} ${WORKTREE_PATH}"` to clean up
     worktree, then stop.
  - Files: `plugin/skills/work/first-use.md`

- Renumber the existing "Already complete" implementation steps 1–6 to steps 2–7 to accommodate the
  new verification gate as step 1.
  - Files: `plugin/skills/work/first-use.md`

- Commit the changes:
  `cd "${WORKTREE_PATH}" && git add plugin/skills/work/first-use.md && git commit -m "feature: add post-condition verification gate to potentially-complete closure path"`
  - Files: `plugin/skills/work/first-use.md`

- After the implementation commit above, update the issue `index.json` to `closed`. Set the
  `status:` field to `closed`. Then commit:
  ```bash
  cd "${WORKTREE_PATH}" && git add .cat/issues/v2/v2.1/this-issue/index.json && git commit -m "planning: close this-issue"
  ```
  This is a separate commit from the implementation commit above (different commit type).

## Post-conditions

- [ ] `plugin/skills/work/first-use.md` "Already complete" path invokes `cat:verify-implementation-agent`
  before updating STATE.md to closed
- [ ] When `verify-implementation-agent` returns PARTIAL or INCOMPLETE, the workflow blocks closure and
  outputs which post-conditions are unmet
- [ ] When `verify-implementation-agent` fails (non-zero exit or unparseable output), the workflow fails
  fast with a clear error rather than silently proceeding to closure
- [ ] When `verify-implementation-agent` returns COMPLETE, the workflow proceeds normally to the existing
  STATE.md update and merge steps
- [ ] `plugin/skills/work/first-use.md` contains an inline comment within the new verification gate
  block noting that the standard implement→confirm→review→merge path runs the same check via
  `work-confirm-agent` (Phase 3), so the gate is consistent with the existing flow
- [ ] E2E: Simulate a `potentially_complete` issue where post-conditions are not fully met — the gate
  blocks closure and outputs a user-visible list of unmet criteria; the issue is not closed
- [ ] E2E: Simulate a `potentially_complete` issue where all post-conditions are met — the gate passes
  and the existing closure flow completes normally
- [ ] All tests pass: `mvn -f client/pom.xml test`
- [ ] No regressions — the standard implement→confirm→review→merge flow is unchanged

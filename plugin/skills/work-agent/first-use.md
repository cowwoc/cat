<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work: Thin Orchestrator

Execute issues with worktree isolation, subagent orchestration, and quality gates.

**Architecture:** Main agent orchestrates 4 phase subagents. Each phase runs in isolation with
its own context, keeping main agent context minimal (~5-10K tokens).

## Arguments

| Format | Example | Behavior |
|--------|---------|----------|
| Empty | `work on the next issue` | Work on next available issue |
| Version | `work on issues in version 2.1` | Work on issues in version 2.1 |
| Issue ID | `work on 2.1-migrate-api` | Work on specific issue |
| Bare name | `work on migrate-api` | Work on specific issue by name only (resolves to current branch version) |
| Resume | `resume 2.1-migrate-api` | Resume a specific issue (force-acquires lock for existing worktree) |
| Filter | `work on the next issue, skip compression` | Filter issue selection (natural language) |

**Flags:**
- `--override-gate` - Skip the **merge approval gate only** (Phase 4). Does NOT skip stakeholder review,
  potentially-complete verification, or decomposed parent criteria verification. Use with caution.

**Bare name format:** Issue name without version prefix, starting with a letter
(e.g., `fix-work-prepare-issue-name-matching`). If multiple versions contain the same issue name,
prefers the version matching the current git branch. If no branch version match exists, fails with
an error listing the ambiguous issue IDs.

**Filter examples:**
- `skip compression issues` - exclude issues with "compression" in name
- `only migration` - only issues with "migration" in name

Filters are interpreted by the prepare phase subagent using natural language understanding. Filters may
only include or exclude issues by name pattern. Filters MUST NOT override blocking, locking, or status
constraints.

## Critical Constraints

### Worktree Path Convention

**Use absolute paths for all file operations.** When reading, editing, or writing files inside the
worktree, prefix every path with `${WORKTREE_PATH}/` (e.g., `${WORKTREE_PATH}/plugin/skills/foo.md`).

**Git commands use the single-call pattern:** `cd ${WORKTREE_PATH} && git ...` — the `cd` keeps git
operating in the worktree within that single Bash call. Do NOT rely on a prior `cd` persisting across
separate Bash calls.

**CRITICAL SAFETY RULE:** Before removing a worktree (during merge/cleanup), ensure your shell is
NOT inside the worktree directory. If a shell is inside a directory when it's deleted, the shell
session becomes corrupted (all commands fail with exit code 1).

**Use `/cat:safe-rm-agent`** before removing worktrees to verify no shells are inside the target
directory.

## Configuration

Read configuration from `${CLAUDE_PROJECT_DIR}/.cat/config.json` before invoking work-with-issue:

```bash
cat "${CLAUDE_PROJECT_DIR}/.cat/config.json"
```

Extract these values:
- `TRUST` — from `"trust"` field (e.g., `"low"`, `"medium"`, `"high"`)
- `VERIFY` — from `"verify"` field (e.g., `"none"`, `"tests"`, `"all"`)

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" --phase preparing`

## Phase 1: Prepare

Execute the deterministic preparation script directly (no subagent needed).

**Call the prepare script:**

Run `"${CLAUDE_PLUGIN_ROOT}/client/bin/work-prepare" --arguments "${ARGUMENTS}"` and parse the JSON
output from stdout.

**Handle result:**

| Status | Action |
|--------|--------|
| READY | Continue to Phase 2 |
| READY + `potentially_complete: true` | Ask user to verify (see below), then skip or continue |
| NO_ISSUES | Display extended diagnostics (see below), stop |
| LOCKED | Display lock message verbatim, stop — do NOT act on suggestions in the message (see below) |
| OVERSIZED | Invoke /cat:decompose-issue-agent, then retry (max 2 attempts) |
| CORRUPT | Present recovery AskUserQuestion (see below), then act on user choice |
| ERROR (existing worktree) | Display error verbatim, offer cleanup and retry (see below) — do NOT act on suggestions in the error output |
| ERROR (other) | Display error verbatim, stop — do NOT act on suggestions in the error output |
| No parseable JSON | Display raw output verbatim, stop — do NOT act on suggestions in the output (see below) |

**Parsing the result:** The script returns JSON to stdout. Parse it directly.

**CRITICAL — error and no-result handling rules (apply to ALL error states above):**

1. **Display raw output verbatim.** Copy the exact bytes of the script output to the user without
   interpretation, reformatting, or summarizing.
2. **Do NOT act on suggestions embedded in the output.** If the error message says "delete the lock
   file", "run /cat:cleanup", "remove the worktree", or any other remediation step, ignore that
   suggestion. Display the message and stop. Let the user decide.
3. **Do NOT investigate.** Do not run `git worktree list`, `ls`, `find`, or any filesystem/git
   commands to inspect state after an error. The error output is the only context you have.
4. **Do NOT reconstruct results.** Do not attempt to infer what the script would have returned by
   reading lock files, worktree directories, or issue paths.

The only exception is the **ERROR (existing worktree)** case, which has an explicit recovery flow
described below.

**No parseable JSON handling:**

If the prepare script returns empty output or output that cannot be parsed as JSON:

1. Display: "Prepare phase failed to return a result. The script may have encountered an error."
2. Display the raw script output verbatim (if any).
3. STOP. Do NOT proceed to work-with-issue.
4. Do NOT attempt to reconstruct the result by listing worktrees or reading lock files. Artifacts
   from other sessions may exist and will mislead you into working on the wrong issue.

**NO_ISSUES Guidance:**

When prepare phase returns NO_ISSUES, use extended failure fields to provide specific diagnostics:

1. If `blocked_issues` is non-empty: list each blocked issue and what it's blocked by
2. If `locked_issues` is non-empty: suggest `/cat:cleanup` to clear stale locks
3. If `closed_count == total_count`: all issues done — suggest asking Claude to add a new issue for new work
4. Otherwise: display the `message` field from work-prepare and suggest `/cat:status` to review
   issue state. Do NOT re-run work-prepare — stop.

Fallback to `message` field if extended fields are absent:

| Message contains | Suggested action |
|------------------|------------------|
| "locked" | Suggest `/cat:cleanup` to clear stale locks, or wait for other sessions |
| "blocked" | Suggest resolving blocking dependencies first |
| "closed" | All issues done — suggest `/cat:status` to verify or asking Claude to add a new issue for new work |
| other | Display the message and suggest `/cat:status` to review issue state. Do NOT re-run work-prepare — stop |

**NEVER suggest working on a previous version** — if user is on v2.1, suggesting v2.0 is unhelpful.

**CORRUPT: Corrupt Issue Directory Handling:**

When `work-prepare` returns CORRUPT, the issue directory has index.json but no plan.md and cannot be
executed without recovery.

1. Display the message from the CORRUPT JSON to the user.
2. Present AskUserQuestion:

   ```
   AskUserQuestion:
     header: "Corrupt Issue Detected"
     question: "<message from CORRUPT JSON>"
     options:
       - "Delete directory" (invoke /cat:safe-rm-agent on issue_path from JSON, then retry work-prepare)
       - "Create plan.md (guided)" (invoke cat:add-agent with the issue_id as context, then retry work-prepare)
       - "Skip this issue" (release lock, retry work-prepare to find next issue)
   ```

3. If user selects **"Delete directory"**:
   - Release lock: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"`
   - Invoke `cat:safe-rm-agent` to remove the corrupt issue directory at `issue_path` from the JSON
   - After removal, retry `work-prepare` immediately

4. If user selects **"Create plan.md (guided)"**:
   - Release lock: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"`
   - Invoke `cat:add-agent` with the `issue_id` from the CORRUPT JSON as context to guide plan.md creation
   - After plan.md is created, retry `work-prepare` to validate and continue

5. If user selects **"Skip this issue"**:
   - Release lock: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"`
   - Retry `work-prepare` with the same arguments to find the next available issue

**CORRUPT retry limit:** If work-prepare returns CORRUPT 3 consecutive times (across any combination
of Skip/Delete/Create choices), display "Multiple corrupt issue directories detected. Run /cat:status
to review issues, then fix or remove corrupt directories manually." and stop. Do NOT continue retrying.

**LOCKED: Issue Locked by Another Session:**

LOCKED is only returned when the user requested a specific issue by ID and that issue is locked by
another session. (When discovering the next available issue automatically, locked issues are skipped
internally by work-prepare.) Display the lock message verbatim including the locking session, then
stop. Do NOT retry work-prepare with the same arguments — it will return the same LOCKED result.
Do NOT act on any suggestions embedded in the lock message. Suggest the user wait for the other
session to finish, run `/cat:cleanup` if the lock is stale, or specify a different issue.

**ERROR: Existing Worktree Handling:**

When `work-prepare` returns ERROR and the `message` field references an existing worktree or an
existing session lock (e.g., "already holds a lock", "worktree already exists"):

1. Display the error message to the user verbatim.
2. Offer cleanup and retry using AskUserQuestion. The "Resume on existing worktree" option is only
   available when the user's original invocation explicitly contained a `resume` or `continue` keyword
   (e.g., `resume 2.1-my-issue`, `continue 2.1-my-issue`). If the user did NOT use resume/continue,
   omit this option entirely and only present "Clean up and retry" and "Abort".

   ```
   AskUserQuestion:
     header: "Existing Worktree Detected"
     question: "<error message from work-prepare>"
     options:
       - "Resume on existing worktree" (only offered when user explicitly said resume/continue — see below)
       - "Clean up and retry" (invoke cat:cleanup-agent, then immediately retry work-prepare)
       - "Abort" (stop)
   ```

   **Do NOT investigate worktree state between steps 1–2 and presenting the AskUserQuestion.**
   Do NOT run `git worktree list`, `ls`, or any filesystem/git commands to inspect existing worktree
   state. The error message from `work-prepare` is sufficient context. Go directly to the
   AskUserQuestion.

3. If user selects **"Resume on existing worktree"**:
   - Extract the `issue_id` from the `"issue_id"` field of the ERROR JSON returned by the first
     work-prepare invocation (this field is present in ERROR responses for existing worktrees).
   - **IMMEDIATELY invoke work-prepare** with the resume prefix:
     `"${CLAUDE_PLUGIN_ROOT}/client/bin/work-prepare" --arguments "resume ${issue_id}"`.
     Parse the result and resume Phase 1 error handling logic.
   - Do NOT run any filesystem, git, or investigation commands before the retry
   - Do NOT manually construct issue paths or worktree paths — work-prepare returns these in its JSON
     output when it returns READY

4. If user selects **"Clean up and retry"**:
   - Invoke `cat:cleanup-agent` (no arguments needed)
   - **IMMEDIATELY after cleanup-agent returns, call work-prepare again** using the same subprocess
     invocation from Phase 1:
     `"${CLAUDE_PLUGIN_ROOT}/client/bin/work-prepare" --arguments "${ARGUMENTS}"`. Parse the result
     and resume Phase 1 error handling logic.
   - Do NOT invoke any other skill or workflow between cleanup-agent returning and the work-prepare
     retry
   - Do NOT invoke `cat:extract-investigation-context-agent` or any investigation skill at this point
   - The ONLY permitted action between cleanup-agent returning and the retry is reading the cleanup
     result
   - **Retry limit:** Only retry work-prepare once after cleanup. If the second attempt also returns
     ERROR with an existing worktree message, display the error verbatim and stop. Do NOT loop back
     to the AskUserQuestion.

5. If user selects **"Abort"**: stop.

**CRITICAL:** After cleanup-agent returns or the user selects "Resume", the next action MUST be
retrying `work-prepare`. Any other skill invocation at this point is a control-flow error.

**Potentially Complete Handling:**

When prepare returns READY with `potentially_complete: true`, work may already exist on the target
branch with index.json not reflecting completion (e.g., stale merge overwrote status).

1. Read the full diff for all commits in `suspicious_commits` in a single Bash call (git show reads
   object history, not working tree contents, so any git directory in the repo works):
   ```bash
   # suspicious_commits is a space-separated list of commit hashes from work-prepare JSON output.
   # Validate each value is a hex hash before use — reject any value not matching [0-9a-fA-F]+.
   cd "${WORKTREE_PATH}" && valid_count=0 && for hash in ${suspicious_commits}; do
     if [[ ! "$hash" =~ ^[0-9a-fA-F]+$ ]]; then
       echo "ERROR: invalid commit hash: $hash" >&2
       continue
     fi
     valid_count=$((valid_count + 1))
     echo "=== $hash ==="
     git show "$hash"
   done && echo "VALID_HASH_COUNT=$valid_count"
   ```
   Use `git show` (full diff), not `git show --stat`. File names alone are insufficient to determine
   whether a commit implements the issue's goal — the actual code changes must be visible.
   If `suspicious_commits` is empty, skip to step 2 (no analysis needed).
   If `VALID_HASH_COUNT` is 0 but `suspicious_commits` was non-empty, treat as UNCERTAIN
   (all hashes were invalid — present AskUserQuestion to the user rather than guessing).
2. Read the issue's goal from its plan.md.
3. Analyze whether the suspicious commits implement the issue's goal:
   - **YES** (commits clearly address the goal) → ask user permission to close:
     ```
     AskUserQuestion:
       header: "${issue_id}"
       question: "Is ${issue_id} already complete?"
       options:
         - "Already complete" (close issue via worktree — see details below)
         - "Not complete, continue" (Proceed to Phase 2 normally)
     ```
   - **NO** (commits are unrelated or tangential to the goal) → log a note that the suspicious
     commits don't implement the issue, then proceed to Phase 2 automatically without asking the
     user.
   - **UNCERTAIN** → ask the user using the same AskUserQuestion as the YES case above.

   **"Already complete" implementation:**
   1. Output: `"Verifying post-conditions before closing ${issue_id}..."`

      <!-- Note: the standard implement→confirm→review→merge path runs the same check via work-confirm-agent
           (Phase 3), so this gate is consistent with the existing flow. -->

      Build the commits JSON array from `suspicious_commits`:
      ```bash
      COMMITS_JSON="["
      first=true
      for hash in ${suspicious_commits}; do
        [[ "$hash" =~ ^[0-9a-fA-F]+$ ]] || continue
        [[ "$first" == "true" ]] || COMMITS_JSON="${COMMITS_JSON},"
        COMMITS_JSON="${COMMITS_JSON}{\"hash\":\"${hash}\",\"message\":\"suspicious commit\",\"type\":\"feature\"}"
        first=false
      done
      COMMITS_JSON="${COMMITS_JSON}]"
      echo "COMMITS_JSON=${COMMITS_JSON}"
      ```
      Then invoke the skill (if `suspicious_commits` is empty, `COMMITS_JSON` will be `[]`):
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

      Parse the verification result (overall assessment field: `COMPLETE`, `PARTIAL`, or `INCOMPLETE`):

      - **If COMPLETE:** Output `"All post-conditions verified."` and proceed to step 2.

      - **If PARTIAL or INCOMPLETE:** Output the full verification report (which criteria are Missing/Partial and
        why). Then STOP — do NOT update index.json, do NOT merge. Display to the user:
        ```
        BLOCKED: ${issue_id} cannot be closed — ${N} post-condition(s) are unmet.
        Review the criteria above, then either:
          - Fix the missing implementation and ask Claude to work on the issue again
          - Select "Not complete, continue" to run the full implement→confirm→review→merge workflow
        ```
        Release the lock:
        `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"`
        Clean up worktree by invoking:
        ```
        Skill tool:
          skill: "cat:safe-rm-agent"
          args: "${CAT_AGENT_ID} ${WORKTREE_PATH}"
        ```
        **Note:** `${CAT_AGENT_ID}` is the unique agent identifier injected at the start of the work-prepare
        phase. It is available in the work orchestration context and passed to skill invocations as the first
        argument (see `plugin/rules/qualified-names.md` § "Skill Invocation: Passing Arguments" for details).
        Then stop — do not proceed further.

      - **If the verify skill itself fails** (non-zero exit or unparseable output): STOP immediately:
        ```
        FAIL: verify-implementation-agent failed for ${issue_id}.
        Cannot close issue without verified post-conditions.
        Error: <error message>
        ```
        Release the lock:
        `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"`
        Invoke `cat:safe-rm-agent` to clean up worktree, then stop.

   2. Update `${WORKTREE_PATH}/<relative_issue_path>/index.json` to set status to `closed`
   3. Commit in worktree: `cd ${WORKTREE_PATH} && git add <relative_issue_path>/index.json && git commit -m "planning: close completed issue ${issue_id}"`
   4. Merge the worktree branch into `${target_branch}` using the normal merge flow (Phase 4 merge
      procedure)
   5. Release lock: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"`
   6. Clean up worktree using `/cat:safe-rm-agent`
   7. Select next issue by invoking `cat:work-agent` with the same version scope

Do NOT ask the user when the commits are clearly unrelated — that interruption is unnecessary
friction.

**Decomposed Parent Closure Verification:**

When prepare returns READY for an issue that is a decomposed parent (its index.json contains
a `## Decomposed Into` section listing sub-issues), verify acceptance criteria BEFORE offering
closure to the user.

**Decomposed parent detection:**

```bash
ISSUE_STATE="${issue_path}/index.json"
IS_DECOMPOSED=$(grep -q "^## Decomposed Into" "$ISSUE_STATE" && echo "true" || echo "false")
```

**Required flow when IS_DECOMPOSED is true:**

1. Read plan.md acceptance criteria: `cat "${issue_path}/plan.md"`
2. Verify each acceptance criterion is satisfied (spawn an Explore subagent if needed)
3. Only after all criteria are verified, use AskUserQuestion to offer closure:
   ```
   AskUserQuestion:
     header: "${issue_id}"
     question: "All sub-issues are closed. Close parent issue ${issue_id}?"
     options:
       - "Close parent issue" (Update index.json in worktree, commit, merge, release lock, clean up worktree)
       - "Keep open" (Release lock, clean up worktree, stop)
   ```

4. If user selects **"Close parent issue"**:
   - Update `${WORKTREE_PATH}/<relative_issue_path>/index.json` to set status to `closed`
   - Commit in worktree: `cd ${WORKTREE_PATH} && git add <relative_issue_path>/index.json && git commit -m "planning: close parent issue ${issue_id}"`
   - Merge the worktree branch into `${target_branch}` using the normal merge flow (Phase 4 merge
     procedure)
   - Release lock: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"`
   - Clean up worktree using `/cat:safe-rm-agent`

5. If user selects **"Keep open"**:
   - Release lock: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"`
   - Clean up worktree using `/cat:safe-rm-agent`
   - Stop.

**CRITICAL: Do NOT offer to close before verifying acceptance criteria.**
The sequence is always: children closed → criteria verified → offer closure.
Offering closure without criteria verification is a protocol violation.

**Store phase 1 results:**
- `issue_id`, `issue_path`, `worktree_path`, `issue_branch`, `target_branch`
- `estimated_tokens`

## Phase 2-4: Delegate to work-with-issue

After Phase 1 returns READY, delegate remaining phases to the work-with-issue skill.

This skill receives the issue ID and metadata, allowing its exclamation-backtick preprocessing to
render progress banners automatically for all 4 phases.

**Invoke the work-with-issue skill:**

Use the Skill tool to invoke `/cat:work-with-issue-agent` with positional space-separated arguments:

```
Skill tool:
  skill: "cat:work-with-issue-agent"
  args: "${CLAUDE_SESSION_ID} ${issue_id} ${issue_path} ${worktree_path} ${issue_branch} ${target_branch} ${estimated_tokens} ${TRUST} ${VERIFY}"
```

The skill will:
1. Render progress banners via exclamation-backtick preprocessing (now has issue_id available)
2. Execute Phase 2 (implementation subagent)
3. Execute Phase 3 (stakeholder review) if verify != none
4. Execute Phase 4 (merge and cleanup)
5. Return execution summary

**Expected return format:**

```json
{
  "status": "SUCCESS|FAILED",
  "issue_id": "2.1-issue-name",
  "commits": [...],
  "files_changed": 5,
  "tokens_used": 65000,
  "merged": true
}
```

**Return validation:** If work-with-issue returns no parseable JSON, partial JSON, or JSON missing
the required `status` field, treat as a phase failure. Release the lock using the Error Handling
procedure below and display the raw return value to the user. Do NOT proceed to the Next Issue phase
with undefined or missing result fields.

**Store final results:**
- `commits`, `files_changed`, `tokens_used`, `merged`

## Next Issue

After successful merge, invoke `/cat:work-complete-agent` with positional arguments:

```
/cat:work-complete-agent ${CLAUDE_SESSION_ID} ${issue_id} ${target_branch}
```

Output the skill result verbatim.

**Parse the result to determine next issue status:**
- If result contains "**Next:**" followed by an issue ID → next issue found
- If result contains "Scope Complete" → no next issue

**Route based on trust level** (use the `TRUST` value read during the Configuration step above; do
NOT re-read `config.json`):

| Condition | Action |
|-----------|--------|
| No next issue | Scope complete — stop |
| Next issue + trust == "low" | Display box, stop for user |
| Next issue + trust >= "medium" | Display box, auto-continue to `cat:work-agent ${next_issue_id}` |

**Low-trust stop message:**

If trust == "low" and next issue found, display after the box:

```
Ready to continue to next issue. Ask Claude to work on the next issue to continue, or use /cat:status to review remaining issues.
```

**Auto-continue (trust >= medium):**

Invoke the Skill tool again with `cat:work-agent` and args `"${CLAUDE_SESSION_ID} ${next_issue_id}"`
to continue to the next issue. No delay needed — the work skill handles its own orchestration.

## Error Handling

If any phase subagent fails unexpectedly:

1. Capture error message
2. Run `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${issue_id}" "${CLAUDE_SESSION_ID}"` to release the lock.
3. Display error to user
4. Offer: Retry, Abort, or Manual cleanup

## Success Criteria

- [ ] Phase subagent spawned for each phase
- [ ] Results collected and parsed as JSON
- [ ] User approval gate respected (skipped only when `--override-gate` flag is passed)
- [ ] Lock released on completion or error
- [ ] Progress banners displayed at phase transitions

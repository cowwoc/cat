# Plan

## Goal

Prevent the approval gate from being presented to the user until all background stakeholder reviewer
subagents have fully completed. Currently, reviewer agents may still be running when the approval gate
is shown, resulting in stale or missing review output. If any reviewer subagent fails, the gate must
be blocked until the failure is resolved — no partial review results are acceptable.

## Pre-conditions

- [ ] All dependent issues are closed

## Post-conditions

- [ ] The approval gate is only shown after all spawned reviewer subagents have returned a result
- [ ] If any reviewer subagent fails (error or no output), the gate is blocked and the user is informed
- [ ] The orchestrating agent waits for reviewer completion before transitioning to the merge phase
- [ ] The fix is verified end-to-end: spawn reviewers, confirm gate does not appear until all return
- [ ] All existing tests pass

## Research Findings

After deep analysis of the review/merge flow in the plugin:

1. `plugin/skills/stakeholder-review-agent/first-use.md` Step 3 says "Task/Agent call per reviewer" — the word "Agent"
   implies reviewers may be spawned with the Agent tool, which supports `run_in_background: true`. If used, background
   reviewers complete asynchronously. The current instructions do not prohibit background execution.

2. `plugin/skills/work-review-agent/first-use.md` invokes `cat:stakeholder-review-agent` via Skill tool (synchronous),
   but if that skill internally uses background Agent calls for reviewers, those results arrive asynchronously after
   the skill "completes" and before Step 4 parses results. There is no count verification: Step 4 says "parse Task tool
   output as JSON" but never checks that exactly N results arrived for N selected stakeholders.

3. `plugin/skills/work-merge-agent/first-use.md` Step 12 has "Pre-Gate Background Task Completion (MANDATORY)" but:
   - MANDATORY STEPS header says "Steps 9-11" — which does NOT cover reviewer subagents from the review phase
   - Does not explicitly reference reviewer subagents by name
   - Has no check that the review result file was written by `work-review-agent` with a valid status
   - No mechanical gate blocks the approval gate when reviewer completion is unconfirmed

4. The fix must:
   - Prohibit `run_in_background: true` for reviewer subagents in `stakeholder-review-agent`
   - Add a reviewer count check in `stakeholder-review-agent` Step 4 before parsing results
   - Add a pre-return reviewer-completion gate in `work-review-agent`
   - Strengthen the "Pre-Gate Background Task Completion" in `work-merge-agent` to explicitly cover
     reviewer subagents and check the review result file

## Commit Type

`bugfix:`

## Jobs

### Job 1

- In `plugin/skills/stakeholder-review-agent/first-use.md` Step 3 ("Spawn Reviewers"), change the spawning
  instruction to explicitly prohibit `run_in_background: true`:
  - Replace "Issue ALL Task calls in one message: Task(prompt, model=optional)." with:
    "Issue ALL Task calls in one message. Use ONLY the Task tool — NEVER the Agent tool. Do NOT set
    `run_in_background: true`. Reviewer subagents MUST complete as foreground tasks so their results
    are received before Step 4 begins."
  - Also add to the ANTI-FABRICATION GUARD block a count check:
    "Before writing any verdict, additionally verify that the number of Task tool results received equals
    the count of selected stakeholders. If fewer results arrived than expected, treat the missing reviewers
    as FAILED with verdict REJECTED and a `parse_error` note: 'Reviewer did not return a Task result.'"

- In `plugin/skills/stakeholder-review-agent/first-use.md` Step 4 ("Collect Reviews"), add reviewer count
  verification at the start:
  - Add before "Parse Task tool output as JSON":
    "**Reviewer count check (MANDATORY):** Before parsing any result, count the number of Task tool
    responses received. The expected count is `SELECTED_COUNT` — the integer count of stakeholders selected
    in Step 1 (read from the Step 1 selection output; it is the length of the selected-stakeholders list).
    If received count < `SELECTED_COUNT`: for each missing reviewer, add a synthetic REJECTED result with
    concerns: `[{severity: 'CRITICAL', location: 'N/A', explanation: 'Reviewer subagent did not return a
    result.', recommendation: 'Retry /cat:work or check for background task failures.'}]`"
  - After processing all reviewer results and before writing the final JSON output, add:
    "Include a `reviewer_count` field in the top-level result JSON, set to the actual count of Task tool
    responses received (before synthetic results are added). Example: `\"reviewer_count\": 3`"

- In `plugin/skills/work-review-agent/first-use.md`, after "Invoke Stakeholder Review" section and before
  "Handle Review Result" section, add a reviewer completion gate:
  - Add a new subsection: "**Reviewer Completion Gate (MANDATORY)**"
  - Content: "Before parsing the stakeholder-review result, verify that the returned JSON contains
    `reviewer_count` equal to or greater than the count of selected stakeholders passed to the skill.
    `reviewer_count` is a top-level integer field in the JSON output from `stakeholder-review-agent`
    (added in Step 4 of that skill). The count of selected stakeholders is the number of stakeholder IDs
    passed to `cat:stakeholder-review-agent` in the current invocation (track this count before invoking
    the skill).
    If `reviewer_count` is absent from the result (older version of the skill), proceed.
    If `reviewer_count` is present AND less than the number of selected stakeholders, STOP immediately:
    do NOT return to work-with-issue; return FAILED status:
    ```json
    {\"status\": \"FAILED\", \"message\": \"Reviewer completion gate failed: only N of M reviewers returned results.\"}
    ```
    Release the lock before returning."

- In `plugin/skills/work-merge-agent/first-use.md`, update the MANDATORY STEPS header entry for
  "Background Task Completion":
  - Change: "ALL background tasks launched in Steps 9-11 must have returned via `<task-notification>`
    before invoking AskUserQuestion."
  - To: "ALL background tasks — including any reviewer subagents spawned during the review phase —
    must have returned via `<task-notification>` before invoking AskUserQuestion."

- In `plugin/skills/work-merge-agent/first-use.md`, strengthen the "Pre-Gate Background Task Completion"
  subsection in Step 12. After the existing instruction about background tasks, add:
  ```
  **Reviewer completion check (MANDATORY):**

  `ISSUE_ID` is the issue identifier passed as a parameter to `work-merge-agent` (the same `issue_id`
  used throughout the merge skill for lock files and state). `CAUTION` is read from the effective config
  using `get-config-output effective` and extracting the `caution` field with grep/sed (the same pattern
  used elsewhere in the merge skill to read config values; `caution` defaults to `"medium"` if absent).

  ```bash
  REVIEW_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/review/${CLAUDE_SESSION_ID}"
  REVIEW_RESULT_FILE="${REVIEW_DIR}/${ISSUE_ID}-result.json"
  ```

  If `REVIEW_RESULT_FILE` exists:
  - Read the file and extract `status` field.
  - If `status` is absent or empty: treat as review phase failure — STOP with error:
    ```
    ERROR: Review result file at ${REVIEW_RESULT_FILE} exists but contains no valid status.
    All reviewer subagents must complete before the approval gate can be presented.
    Re-run /cat:work to retry the review phase.
    ```
  - If `status == "FAILED"`: STOP with error:
    ```
    ERROR: Review phase reported FAILED status. One or more reviewer subagents did not return a result.
    All reviewer subagents must complete before the approval gate can be presented.
    Re-run /cat:work to retry the review phase.
    ```

  If `REVIEW_RESULT_FILE` does not exist AND `CAUTION != "low"` (i.e., review was not skipped):
  - STOP with error:
    ```
    ERROR: Review result file not found at ${REVIEW_RESULT_FILE}.
    The review phase must complete successfully before the approval gate is shown.
    Ensure the review phase ran and all reviewer subagents returned results.
    Re-run /cat:work to retry.
    ```
  ```

- Update index.json in the SAME commit: set `status` to `closed`, `progress` to `100`

## Success Criteria

- `stakeholder-review-agent` Step 3 prohibits `run_in_background: true` for reviewer subagents
- `stakeholder-review-agent` Step 4 verifies reviewer count before parsing
- `work-review-agent` has a reviewer completion gate before returning
- `work-merge-agent` Step 12 explicitly covers reviewer subagents in the background task check
- `work-merge-agent` Step 12 reads and validates the review result file before showing the gate
- All existing tests pass

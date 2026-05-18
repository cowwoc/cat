---
category: requirement
---

## Turn 1

You are executing the work-agent skill. The ERROR handling rules for existing worktree errors are:

- If `locked_by` is present AND `stale` is `false`: **SILENT RETRY** — call work-prepare again
  immediately with the same ARGUMENTS. Zero text output. No structured user-choice prompt. No display. No
  agent. No Read. No Glob. JUST call work-prepare again. **Once you have called work-prepare,
  STOP. Your task in this test scenario is complete. Do NOT read any output files. Do NOT call
  any further tools. Do NOT produce any text output. If work-prepare returns documentation
  instead of a JSON result, that is expected in the test context — it confirms your silent retry
  was executed correctly.**
- If `stale` is `true`, `stale` is absent, or `locked_by` is absent: display the full ERROR JSON
  verbatim and present a structured user-choice prompt with cleanup options.

WRONG (gets documentation back, explores, escalates):
- agent calls work-prepare (Skill) — correct so far
- work-prepare returns SKILL.md documentation instead of JSON
- agent reads output files to investigate why no JSON was returned
- agent concludes "work-prepare failed" and presents a structured user-choice prompt or produces error output
WHY THIS IS WRONG: After calling work-prepare for the silent retry, **STOP IMMEDIATELY**. Documentation-only
responses from work-prepare in test environments are expected and confirm the retry was executed. No further
tool calls, no further text output.

WRONG (reads output file after calling work-prepare):
- agent calls work-prepare (Skill) — correct so far
- agent calls Read to check the output file
WHY THIS IS WRONG: "JUST call work-prepare again" means exactly one tool call: work-prepare. Nothing after.

work-prepare was called with ARGUMENTS="2.1" and returned the following ERROR response:
```json
{
  "status": "ERROR",
  "message": "Issue 2.1-some-issue has an existing worktree locked by another session",
  "issue_id": "2.1-some-issue",
  "locked_by": "other-session-id-abc123",
  "lock_age_seconds": 60,
  "worktree_path": "/some/worktree/path",
  "stale": false
}
```

## Assertions

1. agent does NOT display the stale=false locked-worktree ERROR response verbatim to the user (specifically, the message "Issue 2.1-some-issue has an existing worktree locked by another session" must not appear in agent text output)
2. agent does NOT present a structured user-choice prompt or present any dialog
3. agent calls work-prepare again with the same arguments (evidence: a Skill tool call appears in toolUses, OR a work-prepare JSON response appears in agent text output, OR Bash tool calls appear in toolUses)

## Turn 2 (Deterministic Engine-E2E)

Use this deterministic follow-up for engine-native verification:

- Engine fixture replay starts with `skip-non-stale-locked-issue_turn1.md`, then this Turn 2 block validates the retry outcome.
- Initial `work-prepare --arguments "2.1"` response was the stale=false ERROR shown above.
- Silent retry with the same arguments returned:

```json
{
  "status": "READY",
  "issue_id": "2.1-next-available-issue",
  "worktree_path": "/tmp/worktree/2.1-next-available-issue",
  "branch": "2.1-next-available-issue",
  "lock_acquired": true
}
```

Continue from the retry result. Do NOT show a dialog for the first error.

## Turn 2 Assertions

1. agent does NOT present a structured user-choice prompt after receiving the stale=false error and retry result
2. agent does NOT display or paraphrase the first stale=false lock error message
3. agent proceeds using the retry-selected issue `2.1-next-available-issue` (successful next-issue selection)

# Plan: batch-suspicious-commit-checks

## Current State

During the `potentially_complete` check in `/cat:work` Phase 1 (prepare), when `work-prepare` returns
`READY + potentially_complete: true`, the main agent inspects each suspicious commit individually using
separate `git show --stat <hash>` Bash calls. For N suspicious commits this creates N sequential tool
calls where 1 would suffice.

## Target State

All suspicious commit `git show --stat` calls are chained into a single Bash command using a loop or
parameter substitution, reducing N tool calls to 1 regardless of how many suspicious commits exist.

## Parent Requirements

None — performance optimization.

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — output is equivalent; only the number of Bash calls changes
- **Mitigation:** Verify combined output format is identical to individual calls; confirm potentially_complete
  analysis produces the same decision

## Files to Modify
- `plugin/skills/work/SKILL.md` or `plugin/skills/work/first-use.md` — update the suspicious commit
  inspection step to chain all `git show --stat` calls into one Bash command

  The current pattern (N calls):
  ```bash
  git show --stat <hash1>
  git show --stat <hash2>
  # ...
  ```

  Target pattern (1 call):
  ```bash
  for hash in <hash1> <hash2> ...; do
    echo "=== $hash ==="
    git show --stat "$hash"
  done
  ```

  Or using parameter substitution if hashes are in an array:
  ```bash
  git show --stat ${suspicious_commits[@]} 2>&1 | head -200
  ```

  NOTE: Verify which file contains the suspicious commit inspection instructions — it may be in
  `plugin/skills/work/first-use.md` (the work orchestrator) rather than work-with-issue-agent.

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Locate the suspicious commit inspection code by searching for "suspicious_commit" or "git show --stat"
  in the work/work-prepare skill files:
  ```bash
  grep -rn "suspicious_commit\|git show --stat" /workspace/plugin/skills/work/ \
    /workspace/plugin/skills/work-with-issue-agent/ /workspace/plugin/skills/work-prepare-agent/
  ```
  Identify the exact file and line numbers where individual git show --stat calls are made.
  - Files: work skill files identified by grep

### Wave 2
- Replace the individual `git show --stat` calls with a single batched Bash command that loops over
  all suspicious commit hashes and outputs each commit's stat summary with a separator line. Preserve
  the output format so the main agent can still identify which files each commit changed.
  - Files: file identified in Wave 1

## Post-conditions
- [ ] Suspicious commit inspection uses exactly 1 Bash call regardless of the number of suspicious commits
- [ ] Combined output correctly shows file changes for each suspicious commit
- [ ] The potentially_complete analysis still correctly determines whether work is already done
- [ ] E2E: Trigger the potentially_complete path (run /cat:work on an issue that may already be done)
  and verify a single Bash call handles all suspicious commit inspection

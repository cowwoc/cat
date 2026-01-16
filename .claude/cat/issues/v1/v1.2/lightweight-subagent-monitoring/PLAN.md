# Plan: lightweight-subagent-monitoring

## Objective
add lightweight subagent monitoring via completion markers

## Details
- Add .completion.json marker file written by subagent on completion
- Add .session_id file in worktree for monitoring script to find session
- Add scripts/monitor-subagents.sh for efficient status polling
- Update collect-results to prefer reading completion marker over session parsing
- Update monitor-subagents skill to use the new script
- Update spawn-subagent to write .session_id and require completion marker

The completion marker approach is far more efficient than parsing
potentially large session JSONL files (~200 bytes vs megabytes).

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working

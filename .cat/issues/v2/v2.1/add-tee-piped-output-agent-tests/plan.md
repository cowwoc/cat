# Plan

## Goal

Add agent-compliance tests for plugin/rules/tee-piped-output.md covering the tee rule requirements, exceptions, and cleanup behavior.

## Pre-conditions

(none)

## Post-conditions

- [ ] Agent-compliance tests exist in `plugin/tests/rules/tee-piped-output/` covering the core tee requirement (agent uses mktemp + tee when running a piped Bash command)
- [ ] Tests verify agent captures stderr alongside stdout using the `2>&1 | tee "$LOG_FILE"` pattern
- [ ] Tests verify agent stores the log file path in a `LOG_FILE` variable and uses it consistently in both `tee` invocation and `rm -f` cleanup
- [ ] Tests verify agent cleans up the log file with `rm -f "$LOG_FILE"` after use
- [ ] Tests verify that `run_in_background=true` commands are exempt from the tee rule
- [ ] Tests verify agent uses tee even for simple/short piped commands (mandatory, no exemption for brevity)
- [ ] All existing tests continue to pass (no regressions)

---
category: REQUIREMENT
---
## Turn 1

Verify that SPRT state initialization correctly configures model_id for use by downstream grading.

Step 1 — Create the output directory and initialize SPRT state with two test cases:
```bash
mkdir -p .cat/work
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" init-sprt \
  ".cat/work/sprt-state.json" '["tc1","tc2"]' "/dev/null" "claude-sonnet-4-5-20250929"
```

Step 2 — Record a mixed batch: PASS for tc1, FAIL for tc2:
```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" update-sprt \
  ".cat/work/sprt-state.json" "tc1" "true"
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" update-sprt \
  ".cat/work/sprt-state.json" "tc2" "false"
```

Step 3 — Read the state file and report the model_id, and the log_ratio for both tc1 and tc2 in your response:
```bash
cat .cat/work/sprt-state.json
```

## Assertions

1. The file `.cat/work/sprt-state.json` exists in the runner worktree
2. The agent's text response includes the value `claude-sonnet-4-5-20250929` for `model_id`
3. The agent's text response shows the `log_ratio` for `tc1` is approximately `0.1112` (positive, one PASS)
4. The agent's text response shows the `log_ratio` for `tc2` is approximately `-1.0986` (negative, one FAIL)
5. Both tc1 and tc2 have decision `INCONCLUSIVE` (neither boundary crossed yet)

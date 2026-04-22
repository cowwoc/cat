---
category: REQUIREMENT
---
## Turn 1

Initialize a SPRT state file and record a trial result using the instruction-test-runner binary.

Step 1 — Create the output directory and initialize SPRT state:
```bash
mkdir -p .cat/work
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" init-sprt \
  ".cat/work/sprt-state.json" '["tc1"]' "/dev/null" "claude-haiku-4-5"
```

Step 2 — Record a PASS result for tc1:
```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" update-sprt \
  ".cat/work/sprt-state.json" "tc1" "true"
```

Step 3 — Read `.cat/work/sprt-state.json` and confirm in your response the value of `model_id` and the `passes` and `fails` counts for `tc1`:
```bash
cat .cat/work/sprt-state.json
```

## Assertions

1. The file `.cat/work/sprt-state.json` exists in the runner worktree
2. The agent's text response includes the value `claude-haiku-4-5` for `model_id`
3. The agent's text response shows `passes` equal to 1 and `fails` equal to 0 for `tc1`

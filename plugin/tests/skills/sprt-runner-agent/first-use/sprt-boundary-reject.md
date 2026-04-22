---
category: REQUIREMENT
---
## Turn 1

Test the SPRT reject boundary by recording enough FAIL observations to trigger the REJECT decision.

Step 1 — Create the output directory and initialize SPRT state for tc1:
```bash
mkdir -p .cat/work
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" init-sprt \
  ".cat/work/sprt-state.json" '["tc1"]' "/dev/null" "claude-haiku-4-5"
```

Step 2 — Record 3 FAIL results for tc1 (log_ratio accumulates ~-1.0986 per FAIL; 3 * -1.0986 ≈ -3.296, crossing the REJECT boundary of -2.944):
```bash
for i in $(seq 1 3); do
  "${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" update-sprt \
    ".cat/work/sprt-state.json" "tc1" "false"
done
```

Step 3 — Read the state file and confirm the decision for tc1 in your response:
```bash
cat .cat/work/sprt-state.json
```

## Assertions

1. The file `.cat/work/sprt-state.json` exists in the runner worktree
2. The agent's text response shows the `decision` field for `tc1` is `REJECT`
3. The agent's text response shows `passes` equal to 0 and `fails` equal to 3 for `tc1`

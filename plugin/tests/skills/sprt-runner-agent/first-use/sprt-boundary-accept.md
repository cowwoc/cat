---
category: REQUIREMENT
---
## Turn 1

Test the SPRT accept boundary by recording enough PASS observations to trigger the ACCEPT decision.

Step 1 — Create the output directory and initialize SPRT state for tc1:
```bash
mkdir -p .cat/work
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" init-sprt \
  ".cat/work/sprt-state.json" '["tc1"]' "/dev/null" "claude-haiku-4-5-20251001"
```

Step 2 — Record 27 PASS results for tc1 (log_ratio accumulates ~0.1112 per PASS; 27 * 0.1112 ≈ 3.002, crossing the ACCEPT boundary of 2.944):
```bash
for i in $(seq 1 27); do
  "${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" update-sprt \
    ".cat/work/sprt-state.json" "tc1" "true"
done
```

Step 3 — Read the state file and confirm the decision for tc1 in your response:
```bash
cat .cat/work/sprt-state.json
```

## Assertions

1. The file `.cat/work/sprt-state.json` exists in the runner worktree
2. The agent's text response shows the `decision` field for `tc1` is `ACCEPT`
3. The agent's text response shows `passes` equal to 27 and `fails` equal to 0 for `tc1`

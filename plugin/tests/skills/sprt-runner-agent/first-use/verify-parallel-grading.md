---
category: REQUIREMENT
---
## Turn 1

Grade each of the three completed test runs using instruction-grader-agent.

This prompt's preamble contains a `[CWD: /absolute/path]` header. That path is the runner
worktree root. Use it to form absolute output paths for each grade file.

Run alpha:
- Transcript (runner output JSON): `plugin/tests/skills/sprt-runner-agent/first-use/fixtures/run_alpha.json`
- Scenario: `plugin/tests/skills/sprt-runner-agent/first-use/fixtures/scenario_alpha.txt`
- Run ID: `fixture_alpha`
- Output path: absolute path — take the value from `[CWD: ...]` and append `/.cat/work/grade_alpha.json`

Run beta:
- Transcript (runner output JSON): `plugin/tests/skills/sprt-runner-agent/first-use/fixtures/run_beta.json`
- Scenario: `plugin/tests/skills/sprt-runner-agent/first-use/fixtures/scenario_beta.txt`
- Run ID: `fixture_beta`
- Output path: absolute path — take the value from `[CWD: ...]` and append `/.cat/work/grade_beta.json`

Run gamma:
- Transcript (runner output JSON): `plugin/tests/skills/sprt-runner-agent/first-use/fixtures/run_gamma.json`
- Scenario: `plugin/tests/skills/sprt-runner-agent/first-use/fixtures/scenario_gamma.txt`
- Run ID: `fixture_gamma`
- Output path: absolute path — take the value from `[CWD: ...]` and append `/.cat/work/grade_gamma.json`

**Invocation requirements (MANDATORY):**
1. Use the `Agent` tool with `subagent_type: instruction-grader-agent` — do NOT use the `Skill` tool.
2. Spawn all three agents in a **single message** (parallel invocation).
3. After all three complete, verify each output file exists on disk using Bash:
   ```bash
   ls <CWD>/.cat/work/grade_alpha.json <CWD>/.cat/work/grade_beta.json <CWD>/.cat/work/grade_gamma.json
   ```
4. If any file is missing, re-invoke that specific grader before reporting completion.
5. Read each grade file and report the verdict for each run:
   ```bash
   cat <CWD>/.cat/work/grade_alpha.json
   cat <CWD>/.cat/work/grade_beta.json
   cat <CWD>/.cat/work/grade_gamma.json
   ```
   Your response must include the grading outcome for each run (either the raw JSON or an explicit per-assertion PASS/FAIL summary derived from reading the files).

## Assertions

1. The agent's response contains evidence that `grade_alpha.json` has only PASS verdicts — either by showing the JSON with `"verdict":"PASS"` entries, or by explicitly confirming that all assertions for fixture_alpha passed.
2. The agent's response contains evidence that `grade_beta.json` has only PASS verdicts — either by showing the JSON with `"verdict":"PASS"` entries, or by explicitly confirming that all assertions for fixture_beta passed.
3. The agent's response contains evidence that `grade_gamma.json` has only PASS verdicts — either by showing the JSON with `"verdict":"PASS"` entries, or by explicitly confirming that all assertions for fixture_gamma passed.

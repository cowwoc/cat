# Plan

## Goal

Add `plugin/skills/grep-and-read-agent/benchmark/test-cases.json` with agent-orchestrated empirical
test cases that verify `cat:grep-and-read-agent` is invoked when a task requires searching for files
AND reading their contents. No Bats test file — tests are run manually by Claude when the skill is
modified.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/skills/grep-and-read-agent/benchmark/test-cases.json` exists with at least 3 test
  cases (at least 1 positive, at least 2 negative)
- [ ] Positive test case(s) use `must_use_tools: ["Skill"]` with `severity: "HIGH"`
- [ ] Negative test case(s) use `must_not_use_tools: ["Skill"]`
- [ ] `system_reminders` contains the current `cat:grep-and-read-agent` SKILL.md description in
  `"Available skill: cat:grep-and-read-agent — <description>"` format
- [ ] Running `empirical-test-runner` on the benchmark achieves ≥80% on each positive test case
  (verified manually before committing)

## Files Modified

- `plugin/skills/grep-and-read-agent/benchmark/test-cases.json` — new file

## Execution Steps

### Step 1 — Read grep-and-read-agent skill

Read `plugin/skills/grep-and-read-agent/SKILL.md` and `plugin/skills/grep-and-read-agent/first-use.md`
to understand the skill's scope and trigger conditions.

### Step 2 — Design test cases

Design test cases following the pattern in the test-cases.json format below. Positive cases must use
prompts where the agent genuinely needs to search for files at unknown paths AND read their contents.
Negative cases must be clearly out of scope (search-only, or explicit known paths, or single file).

### Step 3 — Write test-cases.json

Create `plugin/skills/grep-and-read-agent/benchmark/` directory and write `test-cases.json`.

Required format:
```json
{
  "target_description": "Subagent uses cat:grep-and-read-agent when a task requires searching for files AND reading their contents",
  "system_reminders": [
    "Available skill: cat:grep-and-read-agent — PREFER when searching pattern AND reading matches - single operation (50-70% faster than sequential)"
  ],
  "configs": {
    "TC1": {
      "messages": [
        {
          "prompt": "<prompt that requires search+read>",
          "success_criteria": {
            "must_use_tools": ["Skill"],
            "_metadata": {
              "uses_tool:Skill": {
                "description": "<what the agent should do>",
                "reason": "<why this triggers the skill>",
                "severity": "HIGH"
              }
            }
          }
        }
      ]
    }
  }
}
```

### Step 4 — Validate empirically

Run empirical-test-runner on the benchmark:
```bash
RUNNER="/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/empirical-test-runner"
"$RUNNER" \
  --config plugin/skills/grep-and-read-agent/benchmark/test-cases.json \
  --trials 3 \
  --model haiku \
  --cwd .
```

All positive test cases must achieve ≥80% pass rate. If any fail, revise the test prompts or
system_reminders and re-run.

### Step 5 — Commit

```bash
git add plugin/skills/grep-and-read-agent/benchmark/test-cases.json
git commit -m "test: add grep-and-read-agent benchmark test cases"
```

## Success Criteria

- `plugin/skills/grep-and-read-agent/benchmark/test-cases.json` exists
- At least 1 positive test case (`must_use_tools: ["Skill"]`)
- At least 2 negative test cases (`must_not_use_tools: ["Skill"]`)
- Empirical validation: positive cases ≥80% pass rate

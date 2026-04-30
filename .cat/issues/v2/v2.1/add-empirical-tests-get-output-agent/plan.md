# Plan: add-empirical-tests-get-output-agent

## Goal
Run `cat:instruction-builder-agent` on the `cat:get-output-agent` skill to improve its instructions, then validate
compliance using `cat:empirical-test-agent` with two critical scenarios: (1) cold start (first invocation in a new
session with no prior `<output>` tags), and (2) multiple invocations in a session where each call must echo the
LATEST `<output>` tag content — not an earlier cached version — even when unrelated prompts occur in between.

## Parent Requirements
None

## Approaches

### A: Instruction-builder then empirical-test (chosen)
- **Risk:** LOW
- **Scope:** 1-2 files (`plugin/skills/get-output-agent/SKILL.md`, optionally `first-use.md`)
- **Description:** Run instruction-builder with an explicit goal targeting both compliance scenarios. After it
  completes, write the empirical test config and validate with empirical-test-runner. The instruction-builder
  receives the two scenario requirements as part of its goal description, so it targets them directly.

### B: Define empirical test cases first, then run instruction-builder (TDD)
- **Risk:** MEDIUM
- **Scope:** Same as A
- **Description:** Write test config before instruction-builder, then pass test config path as additional context.
  Rejected because the `## Main Agent Waves` section executes BEFORE `## Sub-Agent Waves` in the CAT workflow —
  writing the config in a Sub-Agent Wave cannot feed it to the Main Agent Wave. Approach A achieves the same goal
  by embedding the scenario requirements in the instruction-builder goal description.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** The `cat:empirical-test-agent` must simulate multi-turn context for scenario 2;
  `priming_messages` must inject a prior invocation with distinct output content. If instruction-builder
  determines the skill needs no changes, empirical-test still runs to confirm the existing instructions pass.
- **Mitigation:** Use explicit `priming_messages` in the test config. The `success_criteria` for scenario 2
  must match ONLY the latest output text and explicitly exclude the earlier content string.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetOutput.java` — update Step 1 and Verification to reference the LAST `<output>` tag (prevents staleness when the skill is invoked multiple times)
- `plugin/skills/get-output-agent/SKILL.md` — remove appended instructions (staleness guidance now lives in GetOutput.java)
- `plugin/skills/get-output-agent/first-use.md` — Supplementary Rules removed; type-matching and error-content rules moved into GetOutput.java

## Pre-conditions
- [ ] All dependent issues are closed

## Main Agent Waves

- `/cat:instruction-builder-agent plugin/skills/get-output-agent/SKILL.md` — goal: Improve instructions so
  the skill reliably echoes the verbatim content of the CURRENT `<output>` tag injected by the preprocessor,
  not any earlier `<output>` tags seen in the session. Must pass two scenarios: (1) cold start — works on
  first invocation when no prior output tags exist in the session; (2) latest-output — when called multiple
  times with different `<output>` tag content and unrelated prompts in between, echoes the LATEST tag
  content, not a stale earlier version.

  Invocation (Skill tool): `skill: "cat:instruction-builder-agent", args: "${CAT_AGENT_ID} plugin/skills/get-output-agent/SKILL.md"`

  **Edge case:** If instruction-builder determines no changes are needed (skill already correct), proceed to
  Sub-Agent Waves without modification. Empirical-test still runs to confirm compliance.

## Sub-Agent Waves

### Wave 1 — Write empirical test config

- Write the exact JSON below to `/tmp/get-output-agent-compliance-test.json`.
  This wave runs after the Main Agent Wave (instruction-builder) completes.
  - Files: `/tmp/get-output-agent-compliance-test.json` (create, verbatim content below)

  ```json
  {
    "target_description": "cat:get-output-agent compliance: cold start and latest-output-echoing",
    "configs": {
      "scenario_1_cold_start": {
        "messages": [
          {
            "prompt": "Use the cat:get-output-agent skill with the following output:\n\n<output type=\"test\">\nHello from cold start scenario\n</output>",
            "success_criteria": {
              "must_contain": ["Hello from cold start scenario"],
              "_metadata": {
                "contains:Hello from cold start scenario": {
                  "description": "Skill echoes the output tag content verbatim on first invocation",
                  "reason": "Cold start: no prior output tags in session; skill must use the provided content",
                  "severity": "HIGH"
                }
              }
            }
          }
        ]
      },
      "scenario_2_latest_output": {
        "priming_messages": [
          {
            "role": "user",
            "content": "Use the cat:get-output-agent skill with the following output:\n\n<output type=\"test\">\nEARLIER OUTPUT CONTENT - should NOT appear\n</output>"
          },
          {
            "role": "assistant",
            "content": "EARLIER OUTPUT CONTENT - should NOT appear"
          },
          {
            "role": "user",
            "content": "Now do something unrelated: what is 2 + 2?"
          },
          {
            "role": "assistant",
            "content": "2 + 2 = 4"
          }
        ],
        "messages": [
          {
            "prompt": "Use the cat:get-output-agent skill with the following output:\n\n<output type=\"test\">\nLATEST OUTPUT CONTENT - this should appear\n</output>",
            "success_criteria": {
              "must_contain": ["LATEST OUTPUT CONTENT - this should appear"],
              "must_not_contain": ["EARLIER OUTPUT CONTENT - should NOT appear"],
              "_metadata": {
                "contains:LATEST OUTPUT CONTENT - this should appear": {
                  "description": "Skill echoes the latest output tag content after unrelated prompts",
                  "reason": "Multiple invocations: skill must use the CURRENT output tag, not a prior session tag",
                  "severity": "HIGH"
                },
                "not_contains:EARLIER OUTPUT CONTENT - should NOT appear": {
                  "description": "Skill does not echo stale output from an earlier invocation",
                  "reason": "Prevents staleness bug where earlier output tag is echoed instead of latest",
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

### Wave 2 — Run empirical-test against updated skill

- Invoke `cat:empirical-test-agent` via Skill tool. The skill loads its internal methodology and then runs
  `empirical-test-runner` CLI against the test config. Invoking the skill causes it to execute the CLI tool
  as part of its workflow; the subagent does NOT need to call `empirical-test-runner` directly.

  Invocation (Skill tool): `skill: "cat:empirical-test-agent", args: "${CAT_AGENT_ID}"`

  When the skill's methodology prompts for the test configuration, provide:
  - Config path: `/tmp/get-output-agent-compliance-test.json`
  - Trials: `10`
  - Model: `sonnet`
  - Working directory: `/workspace` (enables production-equivalent system prompt and plugin hooks from
    `.claude/settings.json`)

  The empirical-test-runner binary is at:
  `${CLAUDE_PLUGIN_DATA}/client/bin/empirical-test-runner`

  Both `scenario_1_cold_start` and `scenario_2_latest_output` must reach PASS verdict (not FAIL or
  INSUFFICIENT_DATA) before the post-conditions are met.

  - Files touched: none (read-only test execution)

## Post-conditions
- [ ] `first-use.md` contains only the preprocessor directive and description (Supplementary Rules section removed)
- [ ] `plugin/skills/get-output-agent/SKILL.md` was processed by instruction-builder (verify with
  `git log --oneline plugin/skills/get-output-agent/SKILL.md`; new commit present OR instruction-builder
  confirmed no changes needed)
- [ ] All existing tests pass with no regressions (`mvn -f client/pom.xml test` exits 0)
- [ ] Existing uses of `cat:get-output-agent` continue to work correctly
- [ ] E2E scenario 1: `empirical-test-runner` reports `scenario_1_cold_start` as PASS (pass rate meets
  SPRT threshold, not INSUFFICIENT_DATA or FAIL)
- [ ] E2E scenario 2: `empirical-test-runner` reports `scenario_2_latest_output` as PASS (pass rate meets
  SPRT threshold), confirming the skill echoes the LATEST `<output>` tag, not an earlier cached one

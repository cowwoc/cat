# Plan: add-empirical-compliance-testing-skill

## Goal
Create a `/cat:empirical-test` skill for systematically troubleshooting agent compliance issues where documentation is
correct but agents don't follow instructions. Uses controlled experiments with statistical trials to isolate root causes
and validate fixes.

## Satisfies
None (infrastructure/tooling improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Claude CLI availability, test execution time, model cost per trial
- **Mitigation:** Default to haiku for cost efficiency, configurable trial count, timeout handling

## Approach

The skill orchestrates the empirical testing methodology proven in M507/fix-verbatim-echo-framing:

1. **Define**: User describes the compliance failure (what should happen vs what does happen)
2. **Hypothesize**: Generate test configurations that isolate variables (one change at a time)
3. **Test**: Run N trials per configuration using claude CLI with stream-json format
4. **Analyze**: Collect pass/fail statistics, identify which variable causes the failure
5. **Fix**: Test candidate fixes with the same methodology before committing
6. **Report**: Present findings with data tables showing success rates per configuration

## Files to Create
- `plugin/skills/empirical-test/first-use.md` - Skill definition
- `plugin/skills/empirical-test/metadata.json` - Skill metadata
- `plugin/scripts/empirical-test-runner.py` - Test execution engine

## Acceptance Criteria
- [ ] `/cat:empirical-test` skill accepts a compliance failure description
- [ ] Skill generates isolated test configurations varying one element at a time
- [ ] Test runner executes N trials per config using claude CLI stream-json
- [ ] Results show per-config success rates in a formatted table
- [ ] Supports model selection (haiku/sonnet) and configurable trial count
- [ ] Supports optional tool-use priming context (messages before the test prompt)
- [ ] Test runner handles timeouts and errors gracefully

## Execution Steps
1. **Step 1:** Create the test runner script (`plugin/scripts/empirical-test-runner.py`)
   - Accept test configs as JSON input (each config has a name, prompt content, and success criteria)
   - Execute each config N times using claude CLI with stream-json
   - Parse output events to extract assistant text and tool use
   - Evaluate success criteria against output
   - Return JSON results with per-config statistics
   - Files: `plugin/scripts/empirical-test-runner.py`
2. **Step 2:** Create the skill definition (`plugin/skills/empirical-test/first-use.md`)
   - Guide the agent through the empirical testing methodology
   - Step 1: Gather failure description from user
   - Step 2: Generate hypothesis and test configurations
   - Step 3: Invoke test runner with configs
   - Step 4: Analyze results and identify root cause
   - Step 5: Generate and test candidate fixes
   - Step 6: Present findings report
   - Files: `plugin/skills/empirical-test/first-use.md`, `plugin/skills/empirical-test/metadata.json`
3. **Step 3:** Verify skill loads and test runner executes correctly

## Success Criteria
- [ ] Skill correctly orchestrates the full empirical testing workflow
- [ ] Test runner reliably executes trials and collects statistics
- [ ] Results are actionable (clear identification of which variable causes failure)

# Plan: evaluate-skill-activation

## Goal
Measure whether Claude Code correctly invokes CAT skills when given relevant prompts, using sandboxed evals. If
activation score is less than 100%, update skill definitions to improve activation (better descriptions, trigger
phrases, naming conventions).

## Satisfies
None (quality assurance / infrastructure)

## Approaches

### A: Daytona Sandbox Eval Harness
- **Risk:** MEDIUM
- **Scope:** 5+ files (moderate)
- **Description:** Build a TypeScript/Python eval harness using Daytona sandboxes (per Scott Spence's approach).
  Each test case runs `claude -p` with a prompt, captures JSONL stream output, checks if the correct skill was
  activated. Requires Daytona account and API key.

### B: Local Docker Sandbox Eval
- **Risk:** LOW
- **Scope:** 5+ files (moderate)
- **Description:** Same eval approach but using local Docker containers instead of Daytona. Each container gets a
  fresh Claude Code install with CAT plugin. Avoids external service dependency but requires Docker.

### C: Dry-Run Skill Matching (No Sandbox)
- **Risk:** LOW
- **Scope:** 3-4 files (minimal)
- **Description:** Test skill activation by analyzing skill definitions against test prompts without actually
  running Claude Code. Uses heuristic matching (keyword overlap, description similarity) as a proxy. Faster and
  cheaper but doesn't test actual Claude behavior.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Requires Claude API credits for each eval run; sandbox setup complexity; results may vary between
  model versions
- **Mitigation:** Use `--max-turns 1` to minimize token usage per test; cache sandbox images; document model
  version in results

## Files to Modify
- `tests/eval/` - New directory for eval harness code
- `tests/eval/test_cases.json` - Test prompts mapped to expected skill activations
- `tests/eval/run_evals.py` - Eval orchestrator script
- `tests/eval/results/` - Directory for eval result artifacts
- `plugin/skills/*/SKILL.md` - Update skill descriptions if activation < 100%

## Acceptance Criteria
- [ ] Eval harness can run test prompts through Claude Code and detect skill activation
- [ ] Test suite covers all user-invocable CAT skills with at least 2 prompts each
- [ ] Activation results are reported with per-skill pass/fail breakdown
- [ ] If activation < 100%, skill definitions are updated and re-evaluated
- [ ] Eval can be re-run to measure improvement

## Execution Steps
1. **Inventory user-invocable skills:** List all CAT skills that users can trigger, extract their descriptions
   and trigger patterns
   - Files: `plugin/skills/*/SKILL.md`
2. **Create test case definitions:** Write 2+ test prompts per skill, plus negative cases (prompts that should
   NOT trigger any skill)
   - Files: `tests/eval/test_cases.json`
3. **Build eval harness:** Script that runs each prompt through `claude -p --output-format stream-json
   --max-turns 1`, parses JSONL for `tool_use` events containing skill names
   - Files: `tests/eval/run_evals.py`
4. **Run baseline evaluation:** Execute eval suite, collect per-skill activation rates
   - Files: `tests/eval/results/`
5. **Analyze failures:** For skills with < 100% activation, identify patterns in missed prompts
6. **Update skill definitions:** Improve descriptions, add trigger phrases, adjust naming
   - Files: `plugin/skills/*/SKILL.md`
7. **Re-run evaluation:** Verify improvement, iterate until 100% activation
8. **Implement forced-eval hook:** If skill definition changes alone don't achieve 100%, implement a
   `UserPromptSubmit` hook that forces Claude to evaluate each available skill with explicit YES/NO decisions
   before acting. This three-step commitment mechanism (evaluate → activate → implement) achieves 100%
   activation with zero false positives. Reference:
   https://scottspence.com/posts/measuring-claude-code-skill-activation-with-sandboxed-evals
   - Files: `.claude/settings.json` (hook registration), `plugin/hooks/` (hook handler)

## Success Criteria
- [ ] All user-invocable skills achieve 100% activation on standard test prompts
- [ ] Zero false positives on negative test prompts
- [ ] Eval harness is reproducible (documented setup, deterministic test cases)

## Reference
- [Measuring Claude Code Skill Activation with Sandboxed Evals](https://scottspence.com/posts/measuring-claude-code-skill-activation-with-sandboxed-evals)
  - Baseline without intervention: ~55% activation
  - Forced-eval hook (UserPromptSubmit): 100% activation, 100% true negative rate
  - LLM-eval hook (Haiku pre-classifier): 100% activation but 80% false positive rate on negatives
  - **Forced-eval is the recommended approach** for eliminating both false negatives and false positives

# CAT Skill Activation Eval Suite

Automated evaluation harness to measure Claude Code's skill activation accuracy for CAT skills.

## Overview

This eval suite tests whether Claude correctly invokes CAT skills when given relevant user prompts. It covers all 15 user-invocable CAT skills with multiple test prompts per skill, plus negative test cases.

## Components

- `inventory_skills.py` - Scans plugin directory to identify user-invocable skills
- `test_cases.json` - Test prompts mapped to expected skill activations (36 test cases total)
- `run_evals.py` - Eval orchestrator that runs prompts through Claude CLI and checks activations
- `results/` - Output directory for eval results and summaries

## Test Case Categories

- **Explicit**: Prompts that directly mention the skill action (e.g., "Show me the project status")
- **Implicit**: Prompts that describe intent without explicit skill names (e.g., "What's the current progress?")
- **Negative**: Prompts that should NOT trigger any CAT skill (e.g., "What's the weather?")

## Usage

### Run Smoke Test (Recommended First)

Run a quick 3-case smoke test to verify the harness works before running the full suite:

```bash
cd /workspace/.claude/cat/worktrees/2.1-evaluate-skill-activation
python3 tests/eval/smoke_test.py
```

This runs one explicit positive, one implicit positive, and one negative test case.

### Run Full Eval Suite

```bash
cd /workspace/.claude/cat/worktrees/2.1-evaluate-skill-activation
python3 tests/eval/run_evals.py
```

This will:
1. Load all 36 test cases from `test_cases.json`
2. Run each prompt through `claude -p --output-format stream-json --max-turns 1`
3. Parse JSONL output to detect skill activation
4. Generate pass/fail results with per-skill activation rates
5. Save results to `tests/eval/results/`

### Run Skill Inventory

```bash
python3 tests/eval/inventory_skills.py
```

Generates `skill_inventory.json` with all user-invocable skills and their descriptions.

## Output Format

### results.json

Detailed results for each test case:

```json
[
  {
    "test_id": "status-1",
    "expected_skill": "status",
    "actual_skill": "status",
    "passed": true,
    "prompt": "Show me the project status",
    "category": "explicit"
  }
]
```

### summary.json

Aggregate statistics:

```json
{
  "total_tests": 36,
  "passed": 32,
  "failed": 4,
  "pass_rate": 88.9,
  "skill_stats": {
    "status": {
      "total": 3,
      "passed": 3,
      "failed": 0,
      "activation_rate": 100.0
    }
  }
}
```

## Acceptance Criteria

- All skills should achieve 100% activation rate on standard test prompts
- Zero false positives on negative test cases
- Total pass rate should be 100%

## Improving Skill Activation

If a skill has < 100% activation rate:

1. Review failed test cases in `results/results.json`
2. Identify patterns in missed prompts
3. Update the skill's `SKILL.md` frontmatter:
   - Improve the `description` field with trigger phrases
   - Add common user intent keywords
4. Re-run eval to verify improvement

Example improvement:

```yaml
# Before
description: Display project progress

# After
description: Display project progress - versions, issues, and completion status
```

## Cost Considerations

Each eval run makes API calls through `claude -p`:
- 36 test cases
- ~1 turn per test (limited by --max-turns 1)
- Estimated cost: minimal, but accumulates over multiple runs

Use `--max-turns 1` to minimize token usage per test.

## Limitations

- Tests skill activation only, not skill execution correctness
- Requires active Claude API key
- Results may vary between model versions
- Does not test multi-turn conversations or context handling

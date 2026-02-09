# Skill Activation Evaluation Harness

Reference documentation for the CAT skill activation evaluation system.

## Design Decisions

### Execution Environment

The harness uses local `claude -p` CLI invocation for test execution:
- Simplicity: No external dependencies or container setup required
- Cost: Same API costs regardless of execution environment
- Speed: Direct CLI invocation is fastest
- Maintenance: No container images or Daytona accounts to manage

### Single-Turn Testing

Tests use `--max-turns 1` to:
- Minimize token usage per test case
- Ensure skill activation happens in first turn
- Prevent multi-turn conversations that would inflate costs
- Make tests deterministic (single response per prompt)

### Test Case Structure

Test cases are divided into positive and negative categories:
- **Positive cases** verify skills activate when they should
- **Negative cases** verify no false positives on unrelated prompts
- Both are required for confidence in activation quality

Each skill receives 2-3 prompts covering:
- Direct command-style requests ("Show me X")
- Question-style requests ("What's the status?")
- Task-oriented requests ("I need to do X")

This covers the range of natural user phrasing patterns.

### CLI Discovery

The Claude CLI (`claude` v2.1.37+) provides:
- `--output-format stream-json` requires `--verbose` flag
- No `--project-path` option; use `cwd` parameter in subprocess instead
- Output format is JSONL with structure: `{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Skill","input":{"skill":"cat:status"}}]}}`
- Skills are invoked as "cat:status", "cat:work", etc. (prefixed with "cat:")

## Known Limitations

1. **API Cost**: Full eval requires API calls proportional to test case count
2. **Model Variance**: Results may differ between Claude model versions
3. **Context Sensitivity**: Tests run in isolation without conversation history
4. **Permission Mode**: Tests use default permission mode, not "plan" or "delegate"
5. **No Multi-Turn**: Only tests first-turn activation, not conversational skill invocation

## File Structure

```
tests/eval/
├── README.md                    # User-facing documentation
├── IMPLEMENTATION_NOTES.md      # This file
├── inventory_skills.py          # Skill discovery script
├── test_cases.json              # Test cases (positive and negative)
├── run_evals.py                 # Main eval orchestrator
├── smoke_test.py                # Quick sanity check
├── skill_inventory.json         # Generated skill list
└── results/                     # Output directory (created on first run)
    ├── results.json            # Detailed per-case results
    └── summary.json            # Aggregate statistics
```

## Components

### Skill Inventory (`inventory_skills.py`)

Automatically scans `plugin/skills/*/SKILL.md` files to:
- Identify user-invocable skills (those without `user-invocable: false`)
- Extract skill descriptions and argument hints
- Output `skill_inventory.json`

### Test Cases (`test_cases.json`)

Contains positive test cases covering all user-invocable skills and negative test cases that should NOT trigger any
skill. Test cases include prompts testing different phrasings (explicit direct mentions and implicit intent-based).

### Eval Harness (`run_evals.py`)

Main orchestrator that:
- Runs each prompt through `claude -p --output-format stream-json --max-turns 1 --verbose`
- Parses JSONL stream output to detect Skill tool invocations
- Extracts skill name from tool_use events
- Generates pass/fail results with per-skill breakdown
- Outputs `results/results.json` and `results/summary.json`

### Smoke Test (`smoke_test.py`)

Quick sanity check that runs 3 representative test cases before running full eval to verify harness works correctly
with minimal API cost.

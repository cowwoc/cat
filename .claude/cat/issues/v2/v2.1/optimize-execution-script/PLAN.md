# Plan: optimize-execution-script

## Current State
The optimize-execution skill contains inline jq queries across 5 analysis steps. These queries
fail when executed via the Bash tool due to shell escaping of `!=` and `//` operators (M431).
The agent must manually write a script file first, wasting turns and tokens.

## Target State
A single Python script (`plugin/scripts/analyze-session.py`) handles all mechanical data
extraction (Steps 1-3). The skill is simplified to: run the script, then interpret the JSON
output for Steps 4-5 (which require LLM judgment).

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - skill output format unchanged
- **Mitigation:** Test script against current session JSONL file

## Files to Modify
- `plugin/scripts/analyze-session.py` - New Python script for mechanical data extraction
- `plugin/skills/optimize-execution/SKILL.md` - Simplify to run script + interpret results

## Execution Steps
1. **Create `plugin/scripts/analyze-session.py`:**
   - Accept session file path as argument
   - Implement all Step 1-3 analysis: tool frequency, token usage, output sizes,
     cache candidates, batch candidates, parallel candidates
   - Output results as single JSON object to stdout
   - Handle JSONL format (one JSON object per line)

2. **Rewrite `plugin/skills/optimize-execution/SKILL.md`:**
   - Remove all inline jq code blocks from Steps 1-3
   - Replace with single instruction to run the Python script
   - Keep Steps 4-5 (UX categorization, recommendations) as LLM-interpreted guidance
   - Keep output format and example sections unchanged

## Success Criteria
- [ ] Python script runs successfully against a real session JSONL file
- [ ] Script output contains all metrics previously extracted by inline jq
- [ ] Skill document no longer contains inline jq commands
- [ ] All tests pass (`python3 /workspace/run_tests.py`)

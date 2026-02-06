# Plan: optimize-execution-subagents

## Goal
Enhance `analyze-session.py` to discover subagent JSONL files from the parent session and analyze them alongside
the main agent, providing combined optimization recommendations across the full execution tree.

## Satisfies
None (infrastructure enhancement to existing optimization tooling)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Subagent JSONL storage path may vary; agentId extraction relies on tool_result format
- **Mitigation:** Use patterns documented in get-history skill; add graceful handling for missing subagent files

## Files to Modify
- `plugin/scripts/analyze-session.py` - Add subagent discovery and combined analysis
- `plugin/skills/optimize-execution/SKILL.md` - Document subagent analysis in output format

## Acceptance Criteria
- [ ] Functionality works: script discovers and analyzes subagent JSONL files
- [ ] Tests passing: new tests cover subagent discovery and combined analysis
- [ ] No regressions: existing single-session analysis still works identically

## Execution Steps
1. **Add subagent discovery function to `analyze-session.py`:**
   - Parse parent session JSONL for Task tool_result entries containing `"agentId":"..."`
   - Extract all agentId values
   - Resolve subagent file paths: `{parent_session_dir}/subagents/agent-{agentId}.jsonl`
   - Return list of discovered subagent file paths (skip any that don't exist on disk)

2. **Add per-subagent analysis:**
   - For each discovered subagent JSONL, run the existing analysis functions (tool_frequency,
     token_usage, output_sizes, cache_candidates, batch_candidates, parallel_candidates)
   - Store results keyed by agentId

3. **Add combined aggregation:**
   - Merge tool_frequency counts across main + all subagents
   - Merge cache_candidates (same operation repeated across agents, not just within one)
   - Sum token_usage across all agents
   - Keep batch_candidates and parallel_candidates per-agent (they're sequential within each agent)

4. **Update `analyze_session()` return structure:**
   - Add `subagents` key: dict of agentId -> per-subagent analysis
   - Add `combined` key: aggregated metrics across all agents
   - Keep existing top-level keys unchanged for backward compatibility (they represent main agent only)

5. **Update SKILL.md output format documentation:**
   - Add `subagents` section to the JSON output format example
   - Add `combined` section showing aggregated metrics
   - Document the subagent discovery mechanism

6. **Add tests for subagent discovery and combined analysis:**
   - Test: parent session with no subagents returns empty subagents dict
   - Test: parent session with subagents discovers and analyzes them
   - Test: combined metrics correctly aggregate across agents
   - Test: missing subagent files are skipped gracefully

## Success Criteria
- [ ] Running `analyze-session.py` on a session with subagents produces per-subagent and combined analysis
- [ ] Running on a session without subagents produces identical output to current behavior (backward compat)
- [ ] All existing tests continue to pass
- [ ] New tests cover subagent discovery, per-agent analysis, and combined aggregation

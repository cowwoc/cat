# Plan: improve-skill-benchmarking

## Goal
Redesign the instruction-builder-agent benchmarking workflow for targeted/incremental re-testing, correct model
selection, benchmark artifact persistence, and token tracking.

## Satisfies
None (infrastructure/tooling improvement)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Breaking changes to benchmark workflow, test artifact management, token tracking implementation
- **Mitigation:** Maintain backward compatibility where possible, thorough testing of incremental re-test logic

## Approach

### Current State
- instruction-builder uses Haiku for all benchmark execution regardless of the skill's target model
- Runs full SPRT from scratch on every benchmark, even for minor changes
- Does not persist test artifacts to the repository
- Does not track token costs across benchmark runs

### Redesigned Workflow
1. **Targeted/incremental benchmarking**: When a skill is edited, diff the old and new skill text to identify which
   semantic units changed. Re-run SPRT only for test cases linked to changed units. Carry forward passing results for
   unchanged units.
2. **Prior-informed re-testing**: Use a change-detection approach instead of full compliance testing from scratch.
   Options include starting the SPRT log_ratio at a positive value reflecting prior compliance, or running a fast
   smoke test (3-5 runs on changed units) before escalating to full SPRT.
3. **Correct model selection**: Read the skill's `model:` frontmatter field and use that model for benchmarking
   instead of hardcoding Haiku.
4. **Persistent benchmark artifacts**: Record and commit benchmark data including skill/test case file paths, content
   hashes, and test case files themselves (stored in `benchmark/` subdirectory under the skill directory).
5. **Token tracking**: Record tokens used by each benchmark invocation and aggregate across the benchmark run.

## Files to Create/Modify
- `plugin/skills/instruction-builder-agent/SKILL.md` - Update benchmarking logic
- `plugin/skills/instruction-builder-agent/benchmark/benchmark-runner.sh` - Script for incremental benchmarking
- `plugin/skills/instruction-builder-agent/benchmark/` - Directory for benchmark artifacts
- Update any related hook files that invoke instruction-builder

## Post-conditions
- [ ] Skill model field is read and used for benchmark execution (no hardcoded Haiku)
- [ ] Diff-based change detection identifies modified semantic units
- [ ] SPRT is re-run only for test cases linked to changed units
- [ ] Prior compliance history is used to optimize re-test iterations (smoke test or positive log_ratio)
- [ ] Benchmark artifacts (skill hash, test case hashes, test files) are persisted to repo
- [ ] Token counts are tracked and reported per invocation and in aggregate
- [ ] Test case files are stored in `benchmark/` subdirectory under skill directory
- [ ] Benchmark data is committed to the repository with each run
- [ ] Backward compatibility is maintained where possible

## Sub-Agent Waves

### Wave 1: Change Detection and Semantic Unit Mapping
1. **Step 1:** Implement diff logic to identify changed semantic units
   - Parse skill frontmatter and body
   - Compare old vs new versions
   - Identify which semantic units changed (using existing test case `semantic_unit_id` and `location` fields)
   - Files: `plugin/skills/instruction-builder-agent/benchmark/benchmark-runner.sh`
2. **Step 2:** Create test case mapping to semantic units
   - Extend test case structure to link test cases to semantic units
   - Create mapping from semantic units to affected test cases
   - Files: Updates to test case format and mapping logic

### Wave 2: Targeted Re-testing
1. **Step 3:** Implement selective SPRT re-run
   - Preserve passing results for unchanged units
   - Run SPRT only for test cases linked to changed units
   - Files: `plugin/skills/instruction-builder-agent/benchmark/benchmark-runner.sh`
2. **Step 4:** Implement prior-informed re-testing
   - Option A: Start SPRT log_ratio at positive value for prior-compliant skills
   - Option B: Run 3-5 smoke tests before escalating to full SPRT
   - Files: `plugin/skills/instruction-builder-agent/benchmark/benchmark-runner.sh`

### Wave 3: Model Selection and Artifact Persistence
1. **Step 5:** Update to read skill `model:` field
   - Parse frontmatter to extract model directive
   - Use that model for benchmark subagent instead of hardcoded Haiku
   - Files: `plugin/skills/instruction-builder-agent/SKILL.md`, `plugin/skills/instruction-builder-agent/benchmark/benchmark-runner.sh`
2. **Step 6:** Implement benchmark artifact persistence
   - Record skill file path and content hash
   - Record test case file paths and hashes
   - Store test case files in `benchmark/` subdirectory
   - Create benchmark.json with metadata
   - Commit artifacts to repository
   - Files: `plugin/skills/instruction-builder-agent/benchmark/benchmark-runner.sh`

### Wave 4: Token Tracking and Reporting
1. **Step 7:** Implement token tracking in benchmark subagents
   - Collect token usage from each benchmark invocation
   - Store counts in benchmark artifacts
   - Files: `plugin/skills/instruction-builder-agent/benchmark/benchmark-runner.sh`, hook files
2. **Step 8:** Implement token reporting
   - Aggregate token counts across entire benchmark run
   - Display summary to user at completion
   - Files: `plugin/skills/instruction-builder-agent/SKILL.md`, `plugin/skills/instruction-builder-agent/benchmark/benchmark-runner.sh`


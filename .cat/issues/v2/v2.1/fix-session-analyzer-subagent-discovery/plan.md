# Plan: fix-session-analyzer-subagent-discovery

## Goal
Fix `SessionAnalyzer.discoverSubagents()` so it finds all subagent JSONL files, not just those
referenced in Task tool_result entries of the main session JSONL. After context compaction, earlier
Task tool_result entries are replaced by a summary, so their `agentId` values are lost — leaving
subagents invisible to `session-analyzer analyze`.

## Satisfies
None (infrastructure bugfix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Scanning the subagents directory will include ALL subagent files, including those
  from other sessions if any overlap exists. Since subagent directories are session-scoped, this
  should not be an issue.
- **Mitigation:** Retain the existing agentId-parsing approach as a fallback; add filesystem scan
  as the primary discovery path. Exclude `agent-acompact-*.jsonl` files (compaction artifacts, not
  subagent sessions).

## Root Cause
`discoverSubagents()` builds a list of agentIds by scanning `"agentId":"..."` patterns in Task
tool_result entries of the main JSONL, then constructs `subagents/agent-{agentId}.jsonl` paths.

When context is compacted, earlier conversation turns (including Task tool_results containing
`agentId`) are replaced by a `type: "summary"` entry. The agentIds are gone from the main JSONL
but the subagent JSONL files remain on disk under `subagents/`.

**Evidence:** Session `1250c456-4e71-4e70-8513-aa07207a5109` had 13 subagent files under
`subagents/` but `session-analyzer analyze` returned an empty `subagents` map. The main JSONL
only contained one `agentId` reference (the most recent Agent call after compaction).

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java` — update
  `discoverSubagents()` to also scan the `subagents/` directory directly

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update `discoverSubagents()` to perform two-phase discovery:
  1. Filesystem scan: collect all `agent-*.jsonl` files from `subagentDir`, excluding
     `agent-acompact-*.jsonl` files
  2. AgentId parse: existing logic (finds refs in non-compacted sessions)
  3. Union both sets, deduplicate by path
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`
- Update existing tests and add a test: `discoverSubagents()` finds subagent files even when no
  `"agentId"` references appear in the main JSONL entries (simulating post-compaction state)
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionAnalyzerTest.java`
- Run all tests: `mvn -f client/pom.xml test`

## Post-conditions
- [ ] `session-analyzer analyze <session-id>` populates `subagents` map with all subagent JSONL
  files found under `subagents/agent-*.jsonl` (excluding `agent-acompact-*`)
- [ ] Post-compaction sessions (where main JSONL has no `agentId` references) still discover
  all subagent files
- [ ] `agent-acompact-*.jsonl` files are excluded from subagent analysis
- [ ] All tests pass

## Commit Type
bugfix

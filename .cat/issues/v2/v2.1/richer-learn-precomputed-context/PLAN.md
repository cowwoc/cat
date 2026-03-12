# Plan: richer-learn-precomputed-context

## Goal

Enhance the extract-investigation-context preprocessing to extract richer data from the JSONL
transcript, so the Phase 1 (Investigate) subagent can work primarily from pre-computed data instead
of performing expensive JSONL searches at runtime. Target: reduce Phase 1 time by 40-60%.

## Satisfies

- None (optimization)

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Extracting too much context could overwhelm the subagent's initial prompt. Need to
  balance completeness with token budget.
- **Mitigation:** Measure pre-computed context size; cap at ~8K tokens to stay within subagent
  baseline budget

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java` — enhance search to
  return richer context (tool call sequences around matches, not just line matches)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionAnalyzerTest.java` — tests for
  enhanced extraction
- `plugin/skills/extract-investigation-context/SKILL.md` — update to request richer extraction
  and format the pre-computed context for Phase 1 consumption
- `plugin/skills/learn/phase-investigate.md` — update to use pre-computed context as primary
  evidence source, with JSONL search as fallback for verification only

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1: Enhance SessionAnalyzer

- Add method to extract tool call sequences around keyword matches (N tool calls before/after each
  match, including tool_use and tool_result pairs)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`
- Add method to extract the mistake-relevant timeline: the sequence of assistant turns and tool
  calls between the last user message and the error/mistake point
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`
- Write tests for enhanced extraction methods
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionAnalyzerTest.java`

### Wave 2: Update skill instructions

- Update extract-investigation-context to call the enhanced extraction and format results as
  structured evidence blocks
  - Files: `plugin/skills/extract-investigation-context/SKILL.md`
- Update phase-investigate to use pre-computed evidence as primary source, reducing JSONL search
  to verification-only
  - Files: `plugin/skills/learn/phase-investigate.md`

## Post-conditions

- [ ] SessionAnalyzer provides tool-call-sequence extraction around keyword matches
- [ ] Pre-computed context includes structured evidence blocks (not just a navigation index)
- [ ] Phase 1 subagent uses pre-computed context as primary evidence source
- [ ] Pre-computed context stays under 8K tokens to avoid overwhelming subagent baseline
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)

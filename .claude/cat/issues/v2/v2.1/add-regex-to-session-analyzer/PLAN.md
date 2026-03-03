# Plan: add-regex-to-session-analyzer

## Goal

Add regex pattern support to SessionAnalyzer's search method, allowing multi-keyword searches in a
single file scan. This reduces extract-investigation-context time from N scans to 1 scan, directly
benefiting Phase 1 (Investigate) of the learn skill.

## Satisfies

- None (optimization)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Regex compilation errors from user-supplied patterns; performance impact of complex
  regex on large JSONL files
- **Mitigation:** Validate regex at entry point; use Pattern.compile() with timeout-safe flags;
  fall back to literal match on invalid regex

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java` — change `search()`
  to accept regex pattern instead of plain text keyword; update CLI to document regex support
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionAnalyzerTest.java` — tests for
  regex search (multi-keyword alternation, case sensitivity, invalid regex handling)
- `plugin/skills/extract-investigation-context/SKILL.md` — update to combine keywords into single
  regex alternation pattern (`keyword1|keyword2|keyword3`) instead of N sequential calls

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1: Implement regex search

- Update `search()` method to compile pattern as regex; keep case-sensitive by default
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`
- Add `--regex` flag to CLI (default: literal match for backwards compatibility)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`
- Write tests for: regex alternation, invalid regex fallback, case-insensitive flag
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionAnalyzerTest.java`

### Wave 2: Update skill to use regex

- Update extract-investigation-context to combine all keywords into a single regex alternation
  pattern and make one search call instead of N
  - Files: `plugin/skills/extract-investigation-context/SKILL.md`

## Post-conditions

- [ ] `SessionAnalyzer.search()` accepts regex patterns when `--regex` flag is used
- [ ] CLI remains backwards-compatible (literal match without flag)
- [ ] Invalid regex patterns produce a clear error message (not a stack trace)
- [ ] extract-investigation-context uses single regex call instead of N sequential calls
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)

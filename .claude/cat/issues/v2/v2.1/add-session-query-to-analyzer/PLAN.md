# Plan: Add Structured Search Capabilities to SessionAnalyzer

## Goal

Extend SessionAnalyzer with structured query methods to replace raw grep-based session history searches,
reducing investigation tool calls from ~140 to 1-3.

## Satisfies

None (infrastructure/optimization)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** API surface growth on SessionAnalyzer
- **Mitigation:** Add subcommand dispatch to existing main() method

## Files to Modify

- client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java - Add query methods and CLI subcommands
- plugin/skills/get-history-first-use/SKILL.md - Update to reference Java tool instead of raw grep

## Acceptance Criteria

- [ ] `session-analyzer search <file> <keyword> [--context N]` extracts matching content with surrounding context
- [ ] `session-analyzer errors <file>` extracts all error outputs (generalizes DetectFailures pattern)
- [ ] `session-analyzer file-history <file> <path-pattern>` traces all reads/writes/edits of matching files
- [ ] Existing `session-analyzer <file>` (no subcommand) continues to work as before
- [ ] get-history skill updated to reference the Java tool

## Execution Steps

1. **Add subcommand dispatch to main():** Parse first argument as subcommand (analyze, search, errors, file-history).
   Default to analyze for backward compatibility.
   - Files: SessionAnalyzer.java
2. **Add search method:** Extract JSONL entries matching keyword, return with N lines of surrounding context from the
   same message. Handle mega-line JSONL by extracting relevant text blocks, not full lines.
   - Files: SessionAnalyzer.java
3. **Add errors method:** Scan tool_result entries for error patterns (reuse DetectFailures.FAILURE_PATTERN or similar).
   Return structured JSON with command, exit code, error output, and timestamp.
   - Files: SessionAnalyzer.java
4. **Add file-history method:** Trace Read/Write/Edit/Bash tool uses that reference a file path pattern. Return
   chronological list of operations on that file.
   - Files: SessionAnalyzer.java
5. **Update get-history skill:** Replace grep-based examples with Java tool invocations.
   - Files: plugin/skills/get-history-first-use/SKILL.md
6. **Add tests:** Test each query method with sample JSONL input.
   - Files: SessionAnalyzerTest.java

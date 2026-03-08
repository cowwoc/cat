# Plan: add-time-usage-to-optimize-report

## Goal

Update the optimize-execution skill (`first-use.md`) to always include a **Time Usage** section in its
report, placed immediately before the Recommendations section. The section shows a phase-level time summary
and a per-tool breakdown, derived from JSONL timestamps via a new `session-analyzer timing` subcommand.

## Parent Requirements

None

## Approaches

### A: Documentation-only (manual JSONL timing)
- **Risk:** LOW
- **Scope:** 1 file (first-use.md)
- **Description:** Add a new step and report section to `first-use.md`; instruct the LLM to compute timing
  from raw JSONL timestamps. No Java changes.
- **Rejected because:** LLM-computed timing arithmetic from raw JSONL is error-prone and inconsistent.
  Timestamp field variations and format differences across JSONL versions make this fragile. The existing
  pattern for all other metrics is to use `session-analyzer` as the authoritative data source.

### B: session-analyzer enhancement + documentation (chosen)
- **Risk:** MEDIUM
- **Scope:** 3 files (SessionAnalyzer.java, test, first-use.md)
- **Description:** Add a `timing` field to `session-analyzer` output containing per-phase and per-tool
  elapsed times derived from JSONL entry timestamps. Update `first-use.md` Step 1 to document the new
  field and add a Time Usage step + report section.
- **Chosen because:** Consistent with existing architecture (all other metrics come from session-analyzer),
  deterministic and tested, no fragile LLM arithmetic.

### C: New dedicated timing-analyzer CLI
- **Risk:** MEDIUM
- **Scope:** 2-3 files
- **Description:** Create a separate `timing-analyzer` binary; update first-use.md to call it.
- **Rejected because:** Fragments the tool surface area. session-analyzer already reads JSONL; adding a
  second tool for the same file is wasteful duplication.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** SessionAnalyzer.java changes require matching test coverage; JSONL entries may lack
  timestamps in edge cases (old sessions, truncated files)
- **Mitigation:** Graceful fallback when timing data is unavailable; new TestNG tests for timing extraction;
  keep timing extraction in a separate method for easy isolation

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java` — Add timing extraction:
  parse ISO 8601 timestamps from JSONL `timestamp` fields, group by phase (identified by
  `work-prepare`/`work-implement`/`work-review`/`work-merge` skill invocations), compute elapsed time per
  tool and per phase. Add `timing` field to the JSON output returned by the tool.
- `client/src/test/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzerTest.java` — Add tests for
  timing extraction with and without timestamps, with and without phase markers, graceful fallback when
  timestamps are absent.
- `plugin/skills/optimize-execution/first-use.md` — (1) Update Step 1 to document the new `timing` field
  in `session-analyzer` output. (2) Add a new Step N "Extract Time Usage" that reads `timing` from the
  session-analyzer result. (3) Add `Time Usage` section to the Step 6 report structure template,
  immediately before Recommendations. (4) Update the Example Output to include a sample Time Usage section.

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Add timing extraction to `SessionAnalyzer.java` and write tests
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzerTest.java`

### Wave 2
- Update `first-use.md` to document Time Usage step and report section, using the new `timing` field
  - Files: `plugin/skills/optimize-execution/first-use.md`
- Run `mvn -f client/pom.xml test` and confirm all tests pass
  - Files: (no new files)

### Wave 3 (iteration 1 fixes)
- Fix field name mismatch: align `first-use.md` to the actual `session-analyzer` JSON output. Update all
  references in `first-use.md` so that:
  - `timing.total_elapsed_seconds` → `timing.session_elapsed_seconds` (session-level total; lines 63, 309,
    463)
  - per-tool `tool.total_elapsed_seconds` → `tool.elapsed_seconds` (per-tool elapsed; lines 62, 311, 475)
  - Files: `plugin/skills/optimize-execution/first-use.md`
- Fix `tools` vs `tools_elapsed` container mismatch: `first-use.md` line 311 references `tools` within each
  phase; the implementation uses `tools_elapsed` at the top level and `tools` within each phase element.
  Verify whether the phase objects contain a `tools` array (check `SessionAnalyzer.java` lines ~960-970) and
  update the documentation to use the correct path (`phase.tools[*].tool`, `phase.tools[*].call_count`,
  `phase.tools[*].elapsed_seconds`)
  - Files: `plugin/skills/optimize-execution/first-use.md`
- Fix session-level fallback: `first-use.md` line 57 documents a `"session"` phase when no CAT phase markers
  are detected, but `SessionAnalyzer.java` returns an empty `phases` array in that case. Update `first-use.md`
  to match implementation: when `timing.phases` is absent or empty, display a single session-level row using
  `timing.session_elapsed_seconds` for total time and render the tool breakdown from `timing.tools_elapsed`
  instead of phase-level `tools`. Remove the `"session"` phase name fallback entirely.
  - Files: `plugin/skills/optimize-execution/first-use.md`
- Build jlink binary and verify E2E: run `mvn -f client/pom.xml package -Pjlink` in the worktree to produce
  a compiled `session-analyzer` binary at `client/target/jlink/bin/session-analyzer`, then invoke
  `/cat:optimize-execution` against a real CAT session JSONL file to confirm the report includes the Time
  Usage section with phase timings and per-tool breakdown. Confirm `timing.session_elapsed_seconds` is
  present in the JSON output.
  - Files: (no new files; build artifact only)

## Post-conditions

- [ ] `session-analyzer` JSON output includes a `timing` field with per-phase and per-tool elapsed times
- [ ] `first-use.md` Step 6 report structure template includes a `Time Usage` section immediately before
  `Recommendations`
- [ ] `Time Usage` section shows phase-level summary (time per CAT phase, or session-level total if no
  phases detected)
- [ ] `Time Usage` section shows per-tool breakdown (tool name, call count, total elapsed time) within
  each phase
- [ ] Documentation specifies that timing data comes from the `timing` field in `session-analyzer` output
- [ ] Documentation specifies the section is omitted gracefully when `timing` is absent or empty
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: Run `/cat:optimize-execution` after a CAT work session and confirm the report includes the
  Time Usage section with phase timings and per-tool breakdown

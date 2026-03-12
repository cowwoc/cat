# Plan: detect-corrupt-issue-directories

## Goal
Detect issue directories that contain STATE.md but no PLAN.md, report them as corrupt to the end-user across
/cat:work, /cat:status, and /cat:cleanup, and offer recovery options (delete or guided PLAN.md creation) in /cat:work.

## Parent Requirements
None

## Approaches

### A: Centralized detection in IssueDiscovery.java (chosen)
- **Risk:** LOW
- **Scope:** 6 files (moderate)
- **Description:** Add PLAN.md existence check in `findIssueInDir()` alongside the existing STATE.md check (line 716).
  Extend the `Found` record with `boolean isCorrupt`. work-prepare returns a new `CORRUPT` JSON status. Skills consume
  the status via AskUserQuestion. Follows the established pattern — STATE.md detection already uses this exact path.

### B: Detection only in skill layer
- **Risk:** MEDIUM
- **Scope:** 3 skill files (minimal)
- **Description:** Skills scan issue directories directly before invoking binaries.
- **Rejected:** Duplicates file-system logic across 3 independent skills; no Java test coverage; inconsistent
  criteria risk if one skill diverges.

### C: New IssueValidator class
- **Risk:** LOW
- **Scope:** 7 files (comprehensive)
- **Description:** Extract all issue-directory validation into a dedicated class called by all callers.
- **Rejected:** Premature abstraction — a single boolean condition does not justify a new class. Approach A
  adds the check in the natural location (adjacent to the existing STATE.md check) with no structural overhead.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** The `Found` record signature change affects all callers; the `CORRUPT` JSON status must be handled
  gracefully in work-prepare's caller (the work skill) to avoid breaking the existing ERROR-handling path.
- **Mitigation:** Add `isCorrupt` as the last field in `Found` so existing construction sites need only append
  `false`. Test all three commands (work-prepare, status, cleanup) in Java tests.

## Research Findings
`IssueDiscovery.java` is at
`client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`. The `Found` record (lines 227-242)
already includes `boolean createStateMd`. Lines 716-717 detect missing STATE.md with
`boolean createStateMd = !Files.isRegularFile(statePath)`. No PLAN.md check exists. work-prepare currently
returns JSON statuses: READY, NO_ISSUES, LOCKED, OVERSIZED, ERROR. The `/cat:cleanup` skill uses structured JSON
output via `get-cleanup-output`; its JSON contract must gain a `corruptIssues` array field. `WorkPrepareTest` and
`IssueDiscoveryTest` exist with established patterns. All related issues are CLOSED.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` — add PLAN.md check in
  `findIssueInDir()`, extend `Found` record with `boolean isCorrupt`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — handle `isCorrupt` flag; return
  `{"status": "CORRUPT", "issue_id": "...", "issue_path": "...", "message": "..."}` JSON
- `plugin/skills/work-agent/SKILL.md` — handle CORRUPT status (similar to existing ERROR handling): show warning,
  offer AskUserQuestion with delete/wizard/skip options
- `plugin/skills/status-agent/SKILL.md` — display corrupt issue directories with `⚠ CORRUPT` indicator in issue list
- `plugin/skills/cleanup-agent/SKILL.md` — include corrupt issue directories in cleanup confirmation flow
- `client/src/main/java/io/github/cowwoc/cat/hooks/GetCleanupOutput.java` (or equivalent) — add `corruptIssues`
  array to cleanup JSON output
- `client/src/test/java/.../IssueDiscoveryTest.java` — add tests for corrupt directory detection
- `client/src/test/java/.../WorkPrepareTest.java` — add tests for CORRUPT status output

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- In `IssueDiscovery.java`: add PLAN.md existence check in `findIssueInDir()`. After the existing
  `boolean createStateMd = !Files.isRegularFile(statePath)` line, add:
  `boolean isCorrupt = Files.isRegularFile(statePath) && !Files.isRegularFile(planPath);`
  where `planPath` is constructed the same way as `statePath` but pointing to `PLAN.md`. Extend the `Found` record
  to include `boolean isCorrupt` as the last field. Update all `Found(...)` construction sites to pass `false` for
  `isCorrupt` except the corrupt case.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
- In `WorkPrepare.java`: after the LOCKED/OVERSIZED/ERROR checks, add a check: if `found.isCorrupt()`, return JSON
  `{"status": "CORRUPT", "issue_id": "<id>", "issue_path": "<path>", "message": "Issue directory is corrupt:
  STATE.md exists but PLAN.md is missing at <path>"}`.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
- In `GetCleanupOutput.java` (or the cleanup output handler): add a `corruptIssues` array to the cleanup JSON
  output. Scan all issue directories under `.claude/cat/issues/` for the corrupt condition (STATE.md present,
  PLAN.md absent). Each entry: `{"issue_id": "...", "issue_path": "..."}`.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/GetCleanupOutput.java` (or equivalent)
- Add `IssueDiscoveryTest` test: create temp dir with STATE.md but no PLAN.md, confirm `isCorrupt = true`.
  Add `WorkPrepareTest` test: simulate corrupt directory, confirm CORRUPT JSON status returned.
  - Files: `client/src/test/java/.../IssueDiscoveryTest.java`,
    `client/src/test/java/.../WorkPrepareTest.java`

### Wave 2
- In `plugin/skills/work-agent/SKILL.md`: add CORRUPT case to the Phase 1 status handling table (alongside
  READY, NO_ISSUES, LOCKED, OVERSIZED, ERROR). When status is CORRUPT, display error message from JSON and
  present AskUserQuestion:
  ```
  header: "Corrupt Issue Detected"
  question: "<message from CORRUPT JSON>"
  options:
    - "Delete directory" — run safe-rm on issue_path, release lock, pick next issue
    - "Create PLAN.md (guided)" — invoke /cat:add with issue_path context, then retry work-prepare
    - "Skip this issue" — release lock, pick next issue
  ```
  - Files: `plugin/skills/work-agent/SKILL.md`
- In `plugin/skills/status-agent/SKILL.md`: after listing in-progress and open issues, add a "Corrupt Issues"
  section. For each entry in `corruptIssues` from the get-status-output JSON (or equivalent), display:
  `⚠ CORRUPT  <issue_id>  — STATE.md present but PLAN.md missing at <issue_path>`. If no corrupt issues exist,
  omit the section.
  - Files: `plugin/skills/status-agent/SKILL.md`
- In `plugin/skills/cleanup-agent/SKILL.md`: add corrupt issue directories to the cleanup survey. If
  `corruptIssues` array from get-cleanup-output is non-empty, include them in the AskUserQuestion confirmation
  step alongside worktrees and locks. Label each entry as "Corrupt issue directory: <issue_id>". Offer
  "Delete all corrupt issue directories" as a cleanup action.
  - Files: `plugin/skills/cleanup-agent/SKILL.md`
- Run full test suite: `mvn -f client/pom.xml test`
  - Files: all modified files above

## Post-conditions
- [ ] When work-prepare encounters an issue directory with STATE.md but no PLAN.md, it returns
  `{"status": "CORRUPT", ...}` JSON with a descriptive message
- [ ] The /cat:work skill presents AskUserQuestion with three recovery options (delete/wizard/skip) when CORRUPT
  status is received
- [ ] /cat:status output includes a "Corrupt Issues" section listing all corrupt directories with ⚠ indicator
  (section is omitted when no corrupt issues exist)
- [ ] /cat:cleanup output contract includes `corruptIssues` array; corrupt directories appear in the cleanup
  confirmation flow
- [ ] `IssueDiscoveryTest` and `WorkPrepareTest` cover corrupt directory detection scenarios
- [ ] `mvn -f client/pom.xml test` passes with zero failures
- [ ] E2E: Given an issue directory containing only STATE.md (no PLAN.md), /cat:work shows corruption warning
  with three recovery options rather than silently skipping or crashing
- [ ] Corrupt-directory detection logic is centralized in `IssueDiscovery.java` (not duplicated in skills)
- [ ] The guided PLAN.md wizard (option b) invokes /cat:add and produces a PLAN.md that passes work-prepare
  validation on next invocation

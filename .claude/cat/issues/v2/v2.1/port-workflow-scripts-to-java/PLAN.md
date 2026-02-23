# Plan: port-workflow-scripts-to-java

## Current State
Four bash scripts handle issue workflow operations: `issue-lock.sh` (acquire/release/check/heartbeat locking),
`get-available-issues.sh` (find next executable issue), `check-existing-work.sh` (detect existing commits on issue
branches), and `lib/version-utils.sh` (version parsing and issue directory resolution, used by `get-available-issues.sh`).

## Target State
All four scripts rewritten as Java tools in the jlink bundle. `issue-lock.sh` is the most critical — it's referenced by
6+ skills.

## Satisfies
None (infrastructure/tech debt)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** `issue-lock.sh` is heavily used across skills; breaking it disrupts all `/cat:work` flows
- **Mitigation:** TDD approach, extensive testing of all subcommands (acquire, release, check, force-release, update,
  heartbeat, list)

## Files to Modify
- `plugin/scripts/issue-lock.sh` — remove after port
- `plugin/scripts/get-available-issues.sh` — remove after port
- `plugin/scripts/check-existing-work.sh` — remove after port
- `plugin/scripts/lib/version-utils.sh` — remove after port
- `client/src/main/java/...` — new Java implementations
- `client/src/test/java/...` — new tests
- `plugin/skills/work-prepare/first-use.md` — update invocations
- `plugin/skills/work/first-use.md` — update invocations
- `plugin/skills/work-merge/first-use.md` — update invocations
- `plugin/skills/cleanup/first-use.md` — update invocations
- `plugin/skills/merge-subagent/first-use.md` — update invocations
- `plugin/skills/work-with-issue/first-use.md` — update invocations

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Read `issue-lock.sh` and document all subcommands, flags, and exit codes
   - Files: `plugin/scripts/issue-lock.sh`
2. **Step 2:** Write Java implementation for issue-lock with all subcommands
   - Files: `client/src/main/java/...`
3. **Step 3:** Write tests for issue-lock Java implementation (all subcommands)
   - Files: `client/src/test/java/...`
4. **Step 4:** Read `lib/version-utils.sh` and document all functions
   - Files: `plugin/scripts/lib/version-utils.sh`
5. **Step 5:** Write Java implementation for version-utils
   - Files: `client/src/main/java/...`
6. **Step 6:** Read `get-available-issues.sh` and document behavior
   - Files: `plugin/scripts/get-available-issues.sh`
7. **Step 7:** Write Java implementation for get-available-issues (using version-utils Java)
   - Files: `client/src/main/java/...`
8. **Step 8:** Read `check-existing-work.sh` and document behavior
   - Files: `plugin/scripts/check-existing-work.sh`
9. **Step 9:** Write Java implementation for check-existing-work
   - Files: `client/src/main/java/...`
10. **Step 10:** Write tests for all Java implementations
    - Files: `client/src/test/java/...`
11. **Step 11:** Update all skill first-use.md files to invoke Java tools
    - Files: all affected skills
12. **Step 12:** Remove the original bash scripts
13. **Step 13:** Run full test suite to verify no regressions

## Post-conditions
- [ ] User-visible behavior unchanged
- [ ] All tests passing
- [ ] Code quality improved
- [ ] E2E: Run `/cat:work` and confirm issue discovery and locking work end-to-end

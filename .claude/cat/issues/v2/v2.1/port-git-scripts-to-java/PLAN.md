# Plan: port-git-scripts-to-java

## Current State
Two bash scripts handle git safety wrappers: `git-amend-safe.sh` (pre-checks before amend) and `git-rebase-safe.sh`
(backup and conflict recovery for rebase). These are invoked by the `cat:git-amend` and `cat:git-rebase` skills.

## Target State
Both scripts rewritten as Java tools in the jlink bundle, invoked from the same skill entry points.

## Satisfies
None (infrastructure/tech debt)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Git operations are safety-critical; incorrect port could cause data loss
- **Mitigation:** TDD approach, test with real git repos in temp dirs

## Files to Modify
- `plugin/scripts/git-amend-safe.sh` — remove after port
- `plugin/scripts/git-rebase-safe.sh` — remove after port
- `client/src/main/java/...` — new Java implementations
- `client/src/test/java/...` — new tests
- `plugin/skills/git-amend/first-use.md` — update invocation
- `plugin/skills/git-rebase/first-use.md` — update invocation

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Read `git-amend-safe.sh` and document all behaviors, flags, and exit codes
   - Files: `plugin/scripts/git-amend-safe.sh`
2. **Step 2:** Write Java implementation for git-amend-safe with equivalent behavior
   - Files: `client/src/main/java/...`
3. **Step 3:** Write tests for git-amend-safe Java implementation
   - Files: `client/src/test/java/...`
4. **Step 4:** Read `git-rebase-safe.sh` and document all behaviors, flags, and exit codes
   - Files: `plugin/scripts/git-rebase-safe.sh`
5. **Step 5:** Write Java implementation for git-rebase-safe with equivalent behavior
   - Files: `client/src/main/java/...`
6. **Step 6:** Write tests for git-rebase-safe Java implementation
   - Files: `client/src/test/java/...`
7. **Step 7:** Update skill first-use.md files to invoke Java tools instead of bash scripts
   - Files: `plugin/skills/git-amend/first-use.md`, `plugin/skills/git-rebase/first-use.md`
8. **Step 8:** Remove the original bash scripts
   - Files: `plugin/scripts/git-amend-safe.sh`, `plugin/scripts/git-rebase-safe.sh`
9. **Step 9:** Run full test suite to verify no regressions

## Post-conditions
- [ ] User-visible behavior unchanged (same output, same exit codes)
- [ ] All tests passing
- [ ] Code quality improved (type-safe Java vs bash)
- [ ] E2E: Invoke `cat:git-amend` and `cat:git-rebase` skills and confirm they work end-to-end

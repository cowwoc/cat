# Plan: port-issue-discovery

## Current State
`get-available-issues.sh` (751 lines) finds the next executable issue with dependency checking,
lock integration, and priority ordering. Also depends on `lib/version-utils.sh` (105 lines) for
version schema detection.

## Target State
Java equivalent in the hooks module that produces identical JSON output.

## Satisfies
Parent: 2.1-port-workflow-scripts (sub-issue 3 of 4)

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** Issue discovery drives task selection - wrong ordering or filtering breaks workflow
- **Mitigation:** Test with various issue configurations; verify dependency resolution and lock integration

## Scripts to Port
- `plugin/scripts/get-available-issues.sh` (751 lines) - Issue discovery
  - Scans issue directories for open/in-progress tasks
  - Resolves dependencies between issues
  - Integrates with issue-lock.sh for lock checking/acquisition
  - Handles blocked tasks, locked tasks, oversized tasks
  - Returns JSON with issue details, lock status, diagnostics
- `plugin/scripts/lib/version-utils.sh` (105 lines) - Version schema utilities
  - Detects version depth (major, minor, patch)
  - Parses version strings
  - Note: VersionUtils.java may already exist - check and extend

## Files to Create
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
- Test files

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/VersionUtils.java` - extend if needed
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` - if new exports needed

## Execution Steps
1. Read `get-available-issues.sh` thoroughly - understand issue scanning, dependency resolution, lock integration
2. Read `lib/version-utils.sh` - understand version schema detection
3. Check existing `VersionUtils.java` for reusable version parsing
4. Implement `IssueDiscovery.java` with full issue scanning and filtering
5. Ensure integration with `IssueLock.java` from port-lock-and-worktree sub-issue
6. Write tests verifying JSON output contracts and dependency resolution
7. Run `mvn -f hooks/pom.xml verify`
8. Update STATE.md (status: closed, progress: 100%)

## Success Criteria
- [ ] IssueDiscovery produces identical JSON output
- [ ] Dependency resolution matches bash implementation
- [ ] Lock integration works with IssueLock.java
- [ ] Blocked/locked/oversized task detection preserved
- [ ] All tests pass (`mvn -f hooks/pom.xml verify`)

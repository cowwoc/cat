# Plan: optimize-work-prepare-performance

## Goal

Reduce work-prepare wall-clock time from ~40s to ~8-12s by eliminating redundant file I/O, filtering
closed issues before paying the git subprocess cost, and parallelizing independent I/O operations using
virtual threads.

## Parent Requirements

None

## Research Findings

Profiling of a 743-issue project (689 closed, 54 open) revealed four distinct performance problems:

**Problem 1 — Filter-after-sort in `listIssueDirsByAge()`:**
`listIssueDirsByAge()` invokes `getIssueCreationTime()` for every directory in a version dir to sort
them by git creation time. `getIssueCreationTime()` spawns a `git log` subprocess per directory.
Status filtering happens after sorting, so git subprocesses are wasted on closed issues that will be
immediately discarded. With hundreds of closed issues in v2.1, this dominates execution time.

**Problem 2 — Double-scan:**
`IssueDiscovery.findNextIssue()` reads STATE.md files while scanning. When no issue is found,
`WorkPrepare.handleNonFoundResult()` calls `gatherDiagnosticInfo()` which calls `buildIssueIndex()`,
which does `Files.walk()` + `Files.readString()` for all 743 STATE.md files independently.
The NO_ISSUES code path reads every STATE.md file twice.

**Problem 3 — Sequential git subprocesses:**
`getIssueCreationTime()` spawns `git log` calls sequentially. These are independent per-issue calls
with no data dependencies between them.

**Problem 4 — Sequential `Files.readString()` in `buildIssueIndex()`:**
743 sequential file reads, each with separate I/O overhead.

**Approach A (chosen): Targeted fixes at each problem site**
- Fix filter-before-sort in `listIssueDirsByAge()`
- Eliminate double-scan by building the index once and reusing it
- Add virtual thread parallelism to git calls in `listIssueDirsByAge()`
- Add virtual thread parallelism to `Files.readString()` in `buildIssueIndex()`
- **Risk:** LOW — each fix is localized; thread safety addressed by using `ConcurrentHashMap`
- **Scope:** 2 files (IssueDiscovery.java, WorkPrepare.java)

**Approach B (rejected): Disk-based git timestamp cache**
Persist git creation times to a JSON file so subsequent runs skip git entirely.
- **Rejected because:** Cache invalidation complexity (issues can be created/deleted), extra file to
  maintain, and the user explicitly requested this approach not be taken.

**Approach C (rejected): Pre-built closed-issue index**
Maintain a separate closed-issues list to avoid rescanning closed issues across runs.
- **Rejected because:** Requires writes on every issue status change, coupling that doesn't exist today,
  and the filter-before-sort fix achieves the same result more simply.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Virtual thread parallelism requires thread-safe collections; filter-before-sort changes
  the code path for `listIssueDirsByAge()` callers (they must handle the new filtering contract);
  double-scan elimination restructures the `findNextIssue()` + `gatherDiagnosticInfo()` relationship.
- **Mitigation:** Use `ConcurrentHashMap` for `creationTimeCache` and `sortedDirCache`; verify all
  callers of `listIssueDirsByAge()` accept filtered (open/in-progress only) results; run full test
  suite after each change.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` — four changes:
  1. **Filter-before-sort:** In `listIssueDirsByAge()`, read STATE.md for each dir and skip closed
     issues before calling `getIssueCreationTime()`. Only open/in-progress issues enter the sort.
  2. **Parallel git calls:** In `listIssueDirsByAge()`, use `Executors.newVirtualThreadPerTaskExecutor()`
     to submit one `getIssueCreationTime()` call per open/in-progress directory concurrently, then
     collect futures before sorting.
  3. **Thread-safe caches:** Change `creationTimeCache` from `HashMap` to `ConcurrentHashMap` and
     `sortedDirCache` from `HashMap` to `ConcurrentHashMap` to support concurrent writes from virtual
     threads.
  4. **Parallel `Files.readString()`:** In `buildIssueIndex()`, use
     `Executors.newVirtualThreadPerTaskExecutor()` to submit one `Files.readString()` call per STATE.md
     file concurrently. Collect results into a thread-safe accumulator before populating `issueIndex`
     and `bareNameIndex`. Use a `ConcurrentLinkedQueue` or synchronized block for accumulation, then
     populate the `LinkedHashMap`s sequentially afterward to preserve insertion order.
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — one change:
  5. **Eliminate double-scan:** In `execute()`, build the diagnostic index (`buildIssueIndex`) once
     before calling `findNextIssue()`. Pass the pre-built index into both `findNextIssue()` (via a new
     overload or a field on `IssueDiscovery`) and `gatherDiagnosticInfo()`. Remove the redundant
     `Files.walk()` + `Files.readString()` inside `gatherDiagnosticInfo()` when an index is supplied.
     Only build the index lazily (current behavior) when a specific issue is requested by ID (the fast
     path that never hits `gatherDiagnosticInfo()`).

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- In `IssueDiscovery.java`: change `creationTimeCache` and `sortedDirCache` fields from `HashMap` to
  `ConcurrentHashMap`. Update imports accordingly.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`

### Wave 2
- In `IssueDiscovery.java`: refactor `listIssueDirsByAge()` to (a) read STATE.md and filter out closed
  issues before calling `getIssueCreationTime()`, and (b) parallelize the remaining `getIssueCreationTime()`
  calls using `Executors.newVirtualThreadPerTaskExecutor()`. Collect `Future<Long>` results before sorting.
  Add `ExecutorService`, `Future`, `ExecutionException` imports.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`

### Wave 3
- In `IssueDiscovery.java`: refactor `buildIssueIndex()` to submit `Files.readString()` calls as virtual
  thread tasks, collect results (list of `(path, content)` pairs), then populate `issueIndex` and
  `bareNameIndex` sequentially from the collected results.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`

### Wave 4
- In `WorkPrepare.java` + `IssueDiscovery.java`: eliminate double-scan. Add an overload or parameter to
  allow passing a pre-built issue index into `findNextIssue()`. In `WorkPrepare.execute()` for the `ALL`
  scope, build the index once upfront and pass it to both the selection path and `gatherDiagnosticInfo()`.
  Ensure the specific-issue path (ISSUE or BARE_NAME scope) retains its fast path without building the
  index unnecessarily.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

### Wave 5
- Run `mvn -f client/pom.xml verify` and fix any compilation errors, test failures, checkstyle, or PMD
  violations introduced by the changes. Update STATE.md to closed.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`,
    `.claude/cat/issues/v2/v2.1/optimize-work-prepare-performance/STATE.md`

## Post-conditions

- [ ] `listIssueDirsByAge()` reads STATE.md first and filters out closed issues before invoking `git log`
- [ ] `findNextIssue()` and `gatherDiagnosticInfo()` do not both independently scan all STATE.md files
- [ ] `listIssueDirsByAge()` parallelizes `git log` subprocess calls using virtual threads
- [ ] `buildIssueIndex()` parallelizes `Files.readString()` calls using virtual threads
- [ ] Thread-safe collections (`ConcurrentHashMap`) used for `creationTimeCache` and `sortedDirCache`
- [ ] `mvn -f client/pom.xml verify` passes with no errors
- [ ] No behavioral regressions: same issues discovered in same priority order, same locking behavior
- [ ] E2E: run `work-prepare` against the live repository and confirm it completes in under 15s

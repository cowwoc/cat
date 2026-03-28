# Plan

## Goal

Fix lock file extension filter in `IssueLock` to use `path.getFileName().toString().endsWith(".lock")`
instead of `path.toString().endsWith(".lock")` in both `list()` and `scanForConflictingLock()`, so
that only the filename component is checked (not ancestor directory names). Also emit a warning in
`scanForConflictingLock()` when `parseLockFile()` returns null, matching the existing warning
behavior already present in `list()`.

## Pre-conditions

- [ ] All dependent issues are closed

## Post-conditions

- [ ] `IssueLock.list()` uses `path.getFileName().toString().endsWith(".lock")`
- [ ] `IssueLock.scanForConflictingLock()` uses `path.getFileName().toString().endsWith(".lock")`
- [ ] `scanForConflictingLock()` emits a warning via `this.warnings` when `parseLockFile()` returns null
- [ ] Regression tests verify both the getFileName() filtering and the null warning
- [ ] All existing tests pass

## Research Findings

### Bug 1 & 2: endsWith(".lock") on full path

`path.toString()` returns the full absolute path, e.g. `/workspace/.cat/work/locks/my-issue.lock`.
If an ancestor directory name contained `.lock` (e.g. `/home/user/.lock.dir/locks/my-issue.lock`),
the filter would incorrectly match non-lock files in that directory. The correct fix is
`path.getFileName().toString()` which checks only the last path component.

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java`

**Bug in `list()` (stream filter in the method body):**
```java
// Current (buggy):
lockFiles = stream.filter(path -> path.toString().endsWith(".lock")).toList();
// Fixed:
lockFiles = stream.filter(path -> path.getFileName().toString().endsWith(".lock")).toList();
```

**Bug in `scanForConflictingLock()` (stream filter in the method body):**
```java
// Current (buggy):
lockFiles = stream.
  filter(path -> path.toString().endsWith(".lock")).
  toList();
// Fixed:
lockFiles = stream.
  filter(path -> path.getFileName().toString().endsWith(".lock")).
  toList();
```

### Bug 3: Missing null warning in scanForConflictingLock()

`list()` emits a warning on malformed files via `warnings.println(...)` in its catch block.
`scanForConflictingLock()` calls `parseLockFile()` which swallows exceptions and returns null,
but does not emit a warning when null is returned.

**Bug in `scanForConflictingLock()` (null check block):**
```java
// Current (buggy — silently skips):
JsonNode node = parseLockFile(lockFile);
if (node == null)
  continue;

// Fixed — emits warning before skipping:
JsonNode node = parseLockFile(lockFile);
if (node == null)
{
  warnings.println("WARNING: Skipping malformed lock file " + lockFile + ": unable to parse JSON");
  continue;
}
```

## Jobs

### Job 1

- Apply the three bugfixes to
  `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java`:
  1. In `list()`: change the stream filter from `path.toString().endsWith(".lock")` to
     `path.getFileName().toString().endsWith(".lock")`
  2. In `scanForConflictingLock()`: change the stream filter from `path.toString().endsWith(".lock")`
     to `path.getFileName().toString().endsWith(".lock")`
  3. In `scanForConflictingLock()`: when `parseLockFile()` returns null, add
     `warnings.println("WARNING: Skipping malformed lock file " + lockFile + ": unable to parse JSON");`
     before the `continue` statement (use braces around the if block per style conventions)
- Add regression tests to
  `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueLockTest.java`:
  - `listFiltersOnFilenameNotFullPath`: creates a temp directory whose **path** contains `.lock`
    (e.g. `Files.createTempDirectory("my.lock-")`) as the lock directory parent, places one valid
    lock file inside the `locks/` sub-directory, and verifies `list()` returns exactly 1 entry.
    This ensures the filter checks only the filename component, not the ancestor path.
  - `scanForConflictingLockWarnsOnMalformedFile`: creates a malformed (non-JSON) `.lock` file in
    the lock directory, calls `acquire()` for a different issue (to trigger `scanForConflictingLock()`
    internally), and verifies that the `warnings` stream received a "WARNING: Skipping malformed
    lock file" message. Use a `ByteArrayOutputStream` wrapped in `PrintStream` as the warnings sink.
- Run `mvn -f client/pom.xml verify -e` and fix all compilation/lint errors before committing
- Update `index.json` in the WORKTREE issue directory: set `"status"` to `"closed"` and
  `"progress"` to `100`
- Commit all changes with type `bugfix:` per CLAUDE.md conventions

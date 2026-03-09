# Plan: fix-retrospective-path-traversal

## Problem

`GetRetrospectiveOutput.java` resolves filenames from `index.json` (`files.mistakes` array) against `retroDir`
without boundary checking. A crafted `index.json` containing `"../etc/passwd"` as a filename would resolve to a path
outside `retroDir`, allowing the tool to read arbitrary files. Specifically, line 323:

```java
Path mistakeFile = retroDir.resolve(fileName);  // fileName from index.json — no boundary check
```

No other `resolve()` calls in the file use user-supplied values: `files.retrospectives` entries are only counted
(never resolved to paths), and all other `resolve()` calls use compile-time constants.

## Parent Requirements

None

## Reproduction Code

```java
// index.json with malicious path in files.mistakes:
// { "files": { "mistakes": ["../../../etc/passwd"] } }
// Results in: retroDir.resolve("../../../etc/passwd") → outside retroDir
```

## Expected vs Actual

- **Expected:** Resolving `"../../../etc/passwd"` throws `IOException("Invalid path in index.json: ...")`
- **Actual:** File is resolved outside `retroDir` and read, potentially leaking arbitrary files

## Root Cause

`loadMistakesSince()` calls `retroDir.resolve(fileName)` where `fileName` comes from the untrusted
`files.mistakes` array in `index.json` without normalizing or boundary-checking the resulting path.

## Approaches

### A: normalize() + startsWith() — chosen

After `retroDir.resolve(fileName)`, call `.normalize()` and check that the result starts with
`retroDir.normalize()`. Works regardless of whether the file exists. Handles all `../` sequences. Fast (no I/O
beyond what's already needed). Does not follow symlinks, but in this context (dev tool reading its own files),
symlink attacks are not a realistic threat model.

### B: toRealPath() on both paths

Resolve `retroDir.toRealPath()` once, then call `resolved.toRealPath()` on each file. Fully handles symlinks.
**Rejected**: requires the file to exist before validation (throws `NoSuchFileException` if absent), which changes
error behavior and requires reordering the existing `Files.exists(mistakeFile)` check.

### C: Reject filenames containing path separators

Reject any `fileName` containing `/` or `..`. Simple and explicit. **Rejected**: overly restrictive — valid
filenames with nested subdirectories under `retroDir` would be incorrectly rejected.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Valid paths always normalize to something under `retroDir`, so legitimate use is unaffected
- **Mitigation:** Regression test in Wave 2 verifies legitimate paths still work; new test verifies boundary violation
  throws IOException

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java`
  - Modify `loadMistakesSince()`: add boundary check after `retroDir.resolve(fileName)`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetRetrospectiveOutputTest.java`
  - Add test `pathTraversalInMistakeFileThrows`

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- **In `loadMistakesSince()` (approx line 323), replace:**

  ```java
  String fileName = fileNode.asString();
  Path mistakeFile = retroDir.resolve(fileName);
  if (!Files.exists(mistakeFile))
  {
    throw new IOException("Mistakes file listed in index.json not found: " + mistakeFile);
  }
  ```

  **With:**

  ```java
  String fileName = fileNode.asString();
  Path retroDirNormalized = retroDir.normalize();
  Path mistakeFile = retroDirNormalized.resolve(fileName).normalize();
  if (!mistakeFile.startsWith(retroDirNormalized))
  {
    throw new IOException("""
      Invalid path in index.json files.mistakes: '%s'. \
      Path must stay within the retrospectives directory: %s\
      """.formatted(fileName, retroDir));
  }
  if (!Files.exists(mistakeFile))
  {
    throw new IOException("Mistakes file listed in index.json not found: " + mistakeFile);
  }
  ```

  Note: `retroDirNormalized` is computed per-iteration to keep the change minimal; no method-level field is needed.
  If performance is a concern it can be hoisted to the method scope (before the loop), but correctness is identical.

- Add `pathTraversalInMistakeFileThrows` test to `GetRetrospectiveOutputTest.java`:

  ```java
  /**
   * Verifies that a path traversal attempt in index.json files.mistakes throws IOException.
   */
  @Test
  public void pathTraversalInMistakeFileThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    try
    {
      TestJvmScope scope = new TestJvmScope(tempDir, tempDir);
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      String index = """
        {
          "last_retrospective": "",
          "mistake_count_since_last": 15,
          "trigger_interval_days": 7,
          "mistake_count_threshold": 10,
          "files": {
            "mistakes": ["../../../etc/passwd"],
            "retrospectives": []
          },
          "patterns": [],
          "action_items": []
        }
        """;
      Files.writeString(retroDir.resolve("index.json"), index);
      GetRetrospectiveOutput skill = new GetRetrospectiveOutput(scope);
      try
      {
        skill.getOutput(new String[0]);
        throw new AssertionError("Expected IOException for path traversal");
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "e.getMessage()").
          contains("Invalid path in index.json files.mistakes");
      }
    }
    finally
    {
      // clean up tempDir
      try (var stream = Files.walk(tempDir))
      {
        stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
    }
  }
  ```

  Note: Existing tests use a `finally` block with recursive deletion; follow that same cleanup pattern.

- Run all tests to verify: `mvn -f client/pom.xml test`
- Update STATE.md: `status: closed`, `resolution: implemented`, `progress: 100%`, in the same commit as the
  implementation

## Post-conditions

- [ ] `loadMistakesSince()` validates that each resolved mistake file path starts with `retroDir.normalize()`
  before reading
- [ ] Validation uses `normalize()` on both the directory and the resolved path to handle `../` sequences
- [ ] An `IOException` with a descriptive message is thrown when a path traversal is detected
- [ ] Test `pathTraversalInMistakeFileThrows` verifies that `"../../../etc/passwd"` as a mistake filename triggers
  the IOException
- [ ] No regressions — existing tests still pass
- [ ] E2E: running `get-output retrospective` with a valid index.json produces correct output; running with
  `"../../../etc/passwd"` in `files.mistakes` produces an IOException

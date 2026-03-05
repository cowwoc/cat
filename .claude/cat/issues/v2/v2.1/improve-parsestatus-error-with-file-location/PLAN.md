# Plan: improve-parsestatus-error-with-file-location

## Goal

Improve the `GetStatusOutput.parseStatusFromContent()` error message to include the file path and
line number of the invalid status value, so users can quickly locate the problematic line in their
STATE.md files.

## Satisfies

None

## Background

The current error message is:

```
Error loading skill: Unknown status 'complete'. Valid values: blocked, closed, in-progress, open.
If migrating from older versions, run: plugin/migrations/2.1.sh
```

The file path and line number are missing. The method `parseStatusFromContent(String content)` only
receives the file content but not the path, so it cannot include that information in the error.
Callers that know the file path pass only the content:

- Line 183: `majorStateFile` → `parseStatusFromContent(content)` (no path)
- Line 335: `stateFile` → `parseStatusFromContent(content)` (no path)
- Line 923: `filePath` (String, relative) → `parseStatusFromContent(content.toString())` (no path)

`IssueDiscovery.java` already sets the correct example by including the path in its error messages.

## Approaches

### A: Add `sourcePath` parameter to `parseStatusFromContent`

Add a `String sourcePath` parameter and compute the line number from the matcher position. All three
callers pass their known path (as a display string). The error message becomes:

```
Unknown status 'complete' in .claude/cat/issues/v2/v2.1/my-issue/STATE.md:5.
Valid values: blocked, closed, in-progress, open.
If migrating from older versions, run: plugin/migrations/2.1.sh
```

- **Risk:** LOW
- **Scope:** `GetStatusOutput.java` + its test file

> Approach A is recommended.

## Risk Assessment

- **Risk Level:** LOW

## Implementation Steps

1. Add `String sourcePath` parameter to `parseStatusFromContent(String content, String sourcePath)`.
2. Compute line number from `matcher.start()` by counting newlines in `content.substring(0, matcher.start())`.
3. Also update the "Missing Status field" message to include `sourcePath`.
4. Update the three call sites to pass the file path as a string:
   - Line 183: pass `majorStateFile.toString()`
   - Line 335: pass `stateFile.toString()`
   - Line 923: pass `branch + ":" + filePath`
5. Update the `GetStatusOutputTest` tests for `parseStatusFromContent` to pass a path argument.
6. Run `mvn -f client/pom.xml verify` — all tests must pass.

## Post-conditions

- `parseStatusFromContent` error messages include the file path and line number.
- All existing tests pass.
- The method signature is `parseStatusFromContent(String content, String sourcePath)`.

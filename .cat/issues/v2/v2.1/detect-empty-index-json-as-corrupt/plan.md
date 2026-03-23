# Plan

## Goal

Update `/cat:cleanup` to detect issue directories with empty or non-JSON `index.json` files as corrupt, so they appear
in the "Corrupt Issue Directories" section of the cleanup survey. Currently, `work-prepare` fails with
"index.json does not contain a JSON object" when it encounters such files, but the cleanup survey does not surface
them, leaving users with no automated recovery path.

## Pre-conditions

(none)

## Post-conditions

- [ ] The cleanup survey detects issue directories whose `index.json` is empty (0 bytes) or contains invalid JSON
  as corrupt, and lists them in the "Corrupt Issue Directories" section
- [ ] `work-prepare` no longer silently skips past corrupt directories — cleanup provides the recovery path instead
- [ ] The detection is implemented in `GetCleanupOutput.scanForCorruptIssues()` in
  `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java`
- [ ] Existing corrupt detection (missing plan.md) continues to work correctly — no regression
- [ ] E2E verification: run `/cat:cleanup` and confirm that directories with empty `index.json` appear under
  "⚠ Corrupt Issue Directories" with an appropriate description

## Research Findings

**Corrupt detection location:** `GetCleanupOutput.scanForCorruptIssues()` at
`client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java` (lines 303–332).

**Current corrupt condition:** An issue directory is corrupt when `plan.md` is absent (regardless of whether
`index.json` is present). The method recursively walks `.cat/issues/` directories, skipping version dirs
(matching `^v\d+(\.\d+){0,2}$`).

**Missing condition:** When `index.json` is present but empty (0 bytes) or contains invalid JSON, the method
currently does NOT flag it as corrupt. This causes `work-prepare` to fail later with
`"index.json does not contain a JSON object: <path>"`.

**`CorruptIssue` record:** Defined at line 176 with fields `issueId` (String) and `issuePath` (String). The
display in `getSurveyOutput` formats each entry as `issueId + ": " + issuePath`. A `reason` field should be
added to show why the directory is corrupt (e.g., "plan.md is missing", "index.json is empty",
"index.json does not contain a JSON object").

**Jackson integration:** `GetCleanupOutput` already imports `JsonMapper` and `JsonNode`. It has access to
`scope.getJsonMapper()` for JSON parsing.

**Commit type:** `bugfix:` — this fixes a bug where corrupt directories are not surfaced during cleanup.

## Sub-Agent Waves

### Wave 1

- Update `CorruptIssue` record in `GetCleanupOutput.java` to add a `reason` field, update its Javadoc and
  compact constructor, and update `getSurveyOutput` to display the reason.
- Update `scanForCorruptIssues` in `GetCleanupOutput.java` to also detect:
  - `index.json` is present but empty (0 bytes after stripping whitespace) → reason: "index.json is empty"
  - `index.json` is present but does not parse as a JSON object → reason: "index.json does not contain a
    JSON object"
  - Existing condition: `plan.md` is absent → reason: "plan.md is missing"
  When adding the `reason` argument, also update all existing `new CorruptIssue(...)` call sites to pass the
  appropriate reason string.
- Add new test cases to
  `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetCleanupOutputTest.java`:
  - `gatherCorruptIssuesDetectsEmptyIndexJson`: creates an issue dir with an empty `index.json` (and a valid
    `plan.md`), expects 1 corrupt entry with reason containing "empty"
  - `gatherCorruptIssuesDetectsInvalidJsonInIndexJson`: creates an issue dir with `index.json` containing
    `"not-an-object"` (a JSON string, not an object), expects 1 corrupt entry with reason containing
    "JSON object"
  - Update existing test `surveyOutputIncludesCorruptIssueDirectories` in
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetCleanupOutputTest.java` to pass a `reason`
    to `CorruptIssue` constructor (use "plan.md is missing" or any non-blank string)
- Run `mvn -f client/pom.xml test` to verify all tests pass.
- Update `index.json` in the issue directory to set `status: closed, progress: 100%`.
- Commit all changes with message: `bugfix: detect empty or invalid index.json as corrupt in cleanup survey`

**Implementation details for `scanForCorruptIssues`:**

The updated logic should be:

```
For each issue directory entry:
  1. Check if plan.md is absent:
     → If absent: add CorruptIssue(dirName, path, "plan.md is missing"), continue to next entry
  2. Check if index.json exists:
     → If absent: no further corruption check (work-prepare will create it), continue to next entry
  3. Read index.json content:
     a. If content is blank (empty or whitespace-only):
        → add CorruptIssue(dirName, path, "index.json is empty")
     b. Else try to parse as JSON. If parse fails OR parsed node is not an object:
        → add CorruptIssue(dirName, path, "index.json does not contain a JSON object")
```

Use `scope.getJsonMapper()` to get the `JsonMapper`. Use `mapper.readTree(content)` to parse. Catch
`IOException` from `readTree` as the invalid JSON case. Use `Files.readString(indexJson)` to read content.
Wrap this block in a try/catch for IOException to handle read errors; on IOException, add a CorruptIssue
with reason `"index.json could not be read: " + e.getMessage()`.

**Updated `CorruptIssue` record:**

```java
/**
 * Represents a corrupt issue directory entry for display.
 *
 * @param issueId the issue ID
 * @param issuePath the absolute path to the issue directory
 * @param reason human-readable description of why the directory is corrupt
 */
public record CorruptIssue(String issueId, String issuePath, String reason)
{
  /**
   * Creates a corrupt issue entry.
   *
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if {@code issueId}, {@code issuePath}, or {@code reason} is blank
   */
  public CorruptIssue
  {
    requireThat(issueId, "issueId").isNotBlank();
    requireThat(issuePath, "issuePath").isNotBlank();
    requireThat(reason, "reason").isNotBlank();
  }
}
```

**Updated display in `getSurveyOutput`:**

Change the display line from:
```java
corruptItems.add(corrupt.issueId() + ": " + corrupt.issuePath());
```
to:
```java
corruptItems.add(corrupt.issueId() + ": " + corrupt.issuePath() + " (" + corrupt.reason() + ")");
```

**index.json format for closing the issue:**

The index.json to update (at the issue path, NOT the corrupt test directories) should be set to:
```json
{
  "status": "closed",
  "progress": 100
}
```

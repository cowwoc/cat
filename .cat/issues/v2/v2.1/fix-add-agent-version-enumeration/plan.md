# Plan

## Goal

Fix GetAddOutput handler to read version status from index.json instead of STATE.md — readVersionData() still looks
for STATE.md which was replaced by index.json in a prior migration, causing all versions to appear closed and the
add-agent to return an empty version list.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: GetAddOutput.readVersionData() reads index.json for status, not STATE.md
- [ ] Regression test added: test covers readVersionData() with index.json-based version directories
- [ ] No new issues introduced
- [ ] E2E verification: running the get-add-output CLI returns a non-empty versions array when non-closed versions exist
- [ ] All existing tests in GetAddOutputPlanningDataTest are updated to use index.json format (not STATE.md)
- [ ] Missing index.json behavior is defined and tested (e.g., version treated as closed when index.json is absent)
- [ ] readVersionData() reads plan.md (lowercase) for goal summary, not PLAN.md
- [ ] parseStatus() method is removed or replaced with JSON parsing of index.json

## Research Findings

The index.json format for version directories uses a simple JSON object:
```json
{"status": "in-progress"}
```
Valid status values are: `open`, `in-progress`, `closed`.

The version directory structure is:
- `.cat/issues/v{major}/v{major}.{minor}/index.json` — version status (replaces STATE.md)
- `.cat/issues/v{major}/v{major}.{minor}/plan.md` — version goal/summary (replaces PLAN.md)
- `.cat/issues/v{major}/v{major}.{minor}/{issue-name}/` — issue subdirectories

The existing `GetAddOutputPlanningDataTest` creates `STATE.md` files in all test setups; all must be
changed to `index.json` with the appropriate JSON content `{"status":"..."}`.

Similarly, all `PLAN.md` references must be changed to `plan.md`.

## Sub-Agent Waves

### Wave 1

- Fix `GetAddOutput.java`: update `readVersionData()` to read `index.json` instead of `STATE.md`;
  parse status from JSON; read `plan.md` instead of `PLAN.md`; remove `parseStatus()` method and
  `STATUS_PATTERN` field; update defensive issue-name filter.
- Update all tests in `GetAddOutputPlanningDataTest.java`: replace every `STATE.md` write with
  `index.json`, replace every `PLAN.md` write with `plan.md`, rename the
  `missingStateMdTreatedAsClosed` test to `missingIndexJsonTreatedAsClosed`.
- Update `index.json` in issue directory to set status `closed` and progress `100%`.
- Run `mvn -f client/pom.xml verify` and verify all tests pass.
- Commit all changes with message: `bugfix: fix GetAddOutput to read status from index.json instead of STATE.md`

## Execution Details

### GetAddOutput.java changes

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java`

Remove the `STATUS_PATTERN` field entirely:
```java
// REMOVE this line:
private static final Pattern STATUS_PATTERN = Pattern.compile("^- \\*\\*Status:\\*\\*\\s*(.+)$",
  Pattern.MULTILINE);
```

Also remove the import for `Pattern` if it becomes unused after removing `STATUS_PATTERN`.
Also remove the import for `java.util.regex.Pattern` (used only by `STATUS_PATTERN`).
Also remove `import java.util.regex.Pattern;` and keep the `import java.util.regex.Matcher` if needed — but
`Matcher` is also only used in `parseStatus()`, so remove its import too.

Actually: both `Pattern` and `Matcher` are used only by `STATUS_PATTERN` and `parseStatus()`. Remove both imports.

Replace `readVersionData()` method body:

OLD:
```java
private VersionData readVersionData(Path versionDir) throws IOException
{
  String version = versionDir.getFileName().toString().substring(1);

  Path stateMd = versionDir.resolve("STATE.md");
  if (!Files.isRegularFile(stateMd))
    return new VersionData(version, "closed", "", List.of());

  String stateContent = Files.readString(stateMd);
  String status = parseStatus(stateContent);

  Path planMd = versionDir.resolve("PLAN.md");
  String summary = "";
  if (Files.isRegularFile(planMd))
  {
    String planContent = Files.readString(planMd);
    summary = parseGoalSummary(planContent);
  }

  try (Stream<Path> entries = Files.list(versionDir))
  {
    List<String> existingIssues = entries.
      filter(Files::isDirectory).
      map(p -> p.getFileName().toString()).
      filter(name -> !name.equals("STATE.md") && !name.equals("PLAN.md") &&
        !name.equals("CHANGELOG.md")).
      sorted().
      toList();
    return new VersionData(version, status, summary, existingIssues);
  }
}
```

NEW (replace the entire method):
```java
private VersionData readVersionData(Path versionDir) throws IOException
{
  String version = versionDir.getFileName().toString().substring(1);

  Path indexJson = versionDir.resolve("index.json");
  if (!Files.isRegularFile(indexJson))
    return new VersionData(version, "closed", "", List.of());

  String indexContent = Files.readString(indexJson);
  JsonNode indexNode = scope.getJsonMapper().readTree(indexContent);
  JsonNode statusNode = indexNode.get("status");
  String status;
  if (statusNode == null || !statusNode.isString())
    status = "open";
  else
    status = statusNode.asString();

  Path planMd = versionDir.resolve("plan.md");
  String summary = "";
  if (Files.isRegularFile(planMd))
  {
    String planContent = Files.readString(planMd);
    summary = parseGoalSummary(planContent);
  }

  try (Stream<Path> entries = Files.list(versionDir))
  {
    List<String> existingIssues = entries.
      filter(Files::isDirectory).
      map(p -> p.getFileName().toString()).
      sorted().
      toList();
    return new VersionData(version, status, summary, existingIssues);
  }
}
```

Also update the Javadoc for `readVersionData()`:

OLD:
```java
/**
 * Reads version data from a version directory.
 *
 * @param versionDir the version directory (e.g., {@code .cat/issues/v2/v2.1})
 * @return the version data; returns a version with status {@code "closed"} if STATE.md is missing
 * @throws IOException if an I/O error occurs
 */
```

NEW:
```java
/**
 * Reads version data from a version directory.
 *
 * @param versionDir the version directory (e.g., {@code .cat/issues/v2/v2.1})
 * @return the version data; returns a version with status {@code "closed"} if index.json is missing
 * @throws IOException if an I/O error occurs
 */
```

Remove the `parseStatus()` method entirely:
```java
// REMOVE this entire method:
private String parseStatus(String content)
{
  java.util.regex.Matcher matcher = STATUS_PATTERN.matcher(content);
  if (matcher.find())
    return matcher.group(1).strip();
  return "open";
}
```

Add the import for `JsonNode` if not already imported:
```java
import tools.jackson.databind.JsonNode;
```

Note: `tools.jackson.databind.node.ArrayNode` and `tools.jackson.databind.node.ObjectNode` are already imported.
The `JsonNode` import uses the base package path: `tools.jackson.databind.JsonNode`.

### GetAddOutputPlanningDataTest.java changes

**File:** `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetAddOutputPlanningDataTest.java`

For every test that currently writes `STATE.md` with content like:
```java
Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** in-progress\n");
```

Replace with `index.json`:
```java
Files.writeString(versionDir.resolve("index.json"), "{\"status\":\"in-progress\"}");
```

For every test that currently writes `PLAN.md` with content like:
```java
Files.writeString(versionDir.resolve("PLAN.md"), "# Plan\n\n## Goal\n\nTest version goal summary.\n");
```

Replace with `plan.md`:
```java
Files.writeString(versionDir.resolve("plan.md"), "# Plan\n\n## Goal\n\nTest version goal summary.\n");
```

Specific replacements per test:

1. `inProgressVersionIsIncluded`:
   - Replace `STATE.md` with `index.json` → `"{\"status\":\"in-progress\"}"`
   - Replace `PLAN.md` with `plan.md`

2. `closedVersionIsExcluded`:
   - Replace `STATE.md` with `index.json` → `"{\"status\":\"closed\"}"`

3. `missingStateMdTreatedAsClosed` → rename to `missingIndexJsonTreatedAsClosed`:
   - Update the Javadoc to reference `index.json` instead of `STATE.md`
   - No `index.json` is written (that's the point of the test)

4. `mixedVersionStatusesHandledCorrectly`:
   - Replace all 3 `STATE.md` writes with `index.json` writes using appropriate status JSON

5. `versionsSortedLexicographically`:
   - Replace both `STATE.md` writes with `index.json` writes → `"{\"status\":\"open\"}"`

6. `emptyIssueListWhenNoIssueDirs`:
   - Replace `STATE.md` write with `index.json` → `"{\"status\":\"open\"}"`

7. `regularFilesNotListedAsIssues`:
   - Replace `STATE.md` write with `index.json` → `"{\"status\":\"open\"}"`
   - Replace `PLAN.md` write with `plan.md`
   - The test verifies only directories are listed; `index.json` is a file so it won't appear as an issue

8. `issueNamesWithSpecialCharactersListed`:
   - Replace `STATE.md` write with `index.json` → `"{\"status\":\"open\"}"`

9. `issueCountEqualsExistingIssuesSize`:
   - Replace `STATE.md` write with `index.json` → `"{\"status\":\"open\"}"`

10. Any other tests in the file that reference `STATE.md` or `PLAN.MD`:
    - Apply the same replacement pattern

Also update the Javadoc for `missingIndexJsonTreatedAsClosed`:
OLD:
```java
/**
 * Verifies that a version with a missing STATE.md is treated as closed and excluded.
 *
 * @throws IOException if an I/O error occurs
 */
```

NEW:
```java
/**
 * Verifies that a version with a missing index.json is treated as closed and excluded.
 *
 * @throws IOException if an I/O error occurs
 */
```

### index.json update

Update the issue's own `index.json` file in the worktree:
**File:** `.cat/issues/v2/v2.1/fix-add-agent-version-enumeration/index.json`

Set status to `closed` and progress to `100`. The current content is whatever it is; replace
the `status` field with `"closed"` and add/update `progress` to `100`.

### Commit

After making all changes and verifying tests pass, commit with:
```
bugfix: fix GetAddOutput to read status from index.json instead of STATE.md
```

Include all changed files:
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetAddOutputPlanningDataTest.java`
- `.cat/issues/v2/v2.1/fix-add-agent-version-enumeration/index.json`

# Plan

## Goal

Fix `create-issue` CLI to: (1) accept a file path argument instead of inline JSON (matching the
`plan_file` pattern already used for plan.md), and (2) always pretty-print `index.json` by parsing
and re-serializing `index_content` through the shared JsonMapper before writing.

Currently `IssueCreator.java` writes `index_content` directly via `Files.writeString()` without
formatting, so the output format depends entirely on how the caller constructed the string. Inline
JSON arguments are also fragile (escaping issues, length limits, multiline restrictions). The fix
moves JSON passing to a temp-file pattern and enforces pretty-printing server-side.

## Pre-conditions

(none)

## Post-conditions

- [ ] `create-issue` CLI accepts an `index_file` field (path to a JSON file) instead of
  `index_content` (inline JSON string) in its input — matching how `plan_file` already works
- [ ] `IssueCreator.java` reads index content from the file, parses it through JsonMapper, and
  re-serializes with `INDENT_OUTPUT` before writing `index.json` — so output is always
  pretty-printed regardless of input formatting
- [ ] The `add-agent` skill instructions (`plugin/skills/add-agent/first-use.md`) are updated
  to write index JSON to a temp file and pass `index_file` instead of `index_content`
- [ ] `IssueCreatorTest.java` is updated to pass a temp file path and verify `index.json` is
  pretty-printed (indented) in the output
- [ ] Java test suite passes with no regressions (`mvn -f client/pom.xml test`)
- [ ] E2E: run `/cat:add` end-to-end and confirm the created `index.json` is pretty-printed

## Jobs

### Job 1

- In `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueCreator.java`:
  1. Change the `required` array from `{"major", "minor", "issue_name", "index_content"}` to
     `{"major", "minor", "issue_name"}`. This removes `index_content` from required-field validation
     since it is replaced by `index_file`.
  2. Immediately after the `required` fields for-loop (the one that throws `IOException` for missing
     fields), add an explicit check for `index_file`:
     ```java
     if (!data.has("index_file"))
       throw new IOException("Missing required field: index_file");
     ```
  3. Replace the line `String indexContent = data.get("index_content").asString();` with:
     ```java
     Path indexSourceFile = Path.of(data.get("index_file").asString());
     String indexContent = Files.readString(indexSourceFile, StandardCharsets.UTF_8);
     ```
  4. Replace the line `Files.writeString(indexFile, indexContent, StandardCharsets.UTF_8);` with:
     ```java
     JsonNode parsedIndex = this.mapper.readTree(indexContent);
     Files.writeString(indexFile, this.mapper.writeValueAsString(parsedIndex), StandardCharsets.UTF_8);
     ```
     `this.mapper` is the existing `JsonMapper` instance field initialized in the constructor from
     `scope.getJsonMapper()`. It already has `SerializationFeature.INDENT_OUTPUT` enabled, so the
     output is always pretty-printed.

- In `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueCreatorTest.java`:
  1. Update all test methods that currently pass `"index_content": "..."` inline:
     - Write the index JSON to a temp file (e.g., `Files.createTempFile("index-", ".json")`)
     - Pass `"index_file": "<tempFilePath>"` in the JSON input
     - In `finally` blocks, also delete the temp file
  2. In `executeCreatesIssueStructure()`: after verifying the issue path exists, also verify that
     the content of `index.json` contains a newline (i.e., is pretty-printed / indented), for
     example: `requireThat(indexContent, "indexContent").contains("\n")`.
  3. Update the validation test `executeRejectsMissingIndexContent()` to now test for
     `index_file` missing instead of `index_content` missing. Rename the test method to
     `executeRejectsMissingIndexFile()` and update the `expectedExceptionsMessageRegExp` to
     `".*Missing required field: index_file.*"` and the JSON to omit `index_file`.
  4. Remove the old `executeRejectsMissingIndexContent` test entirely (the renamed version
     in step 3 replaces it).

- In `plugin/skills/add-agent/first-use.md`:
  1. Before the `create-issue` call, add a step to write the index JSON to a temp file:
     ```bash
     index_temp_file=$(mktemp /tmp/cat-index-XXXXXX.json)
     cat > "${index_temp_file}" << 'INDEXEOF'
     {full index.json content}
     INDEXEOF
     ```
  2. Update the `create-issue` JSON argument to replace `"index_content": "{full index.json content}"`
     with `"index_file": "'"${index_temp_file}"'"`.
  3. Add cleanup of `index_temp_file` in both the error and success paths (alongside the existing
     `plan_temp_file` cleanup).
  4. Update the **JSON escaping** note just above the `create-issue` call to remove the mention of
     `{full index.json content}` needing to be JSON-escaped (since it's now written to a file, not
     embedded inline). Replace with a note that the content is written to a temp file instead.
  - Run `mvn -f client/pom.xml verify -e` to verify the build passes with no linter errors.
  - Update `index.json` in the issue directory to set status to `closed` and progress to `100%`.

<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Type

bugfix

## Goal

Fix two bugs in `GetStatusOutput` status-parsing methods:

1. **C1 — blank check after `readTree()`**: In `parseStatusFromIndexJson`, `readTree(content)` is called before
   the `content.isBlank()` guard. A blank index.json should return `"open"` but instead hits Jackson parsing,
   causing a misleading `IOException`. Move the blank check before `readTree()`.

2. **C2 — duplication between `parseStatusFromIndexJson` and `parseStatusFromJson`**: Both methods share the
   same "extract status from a parsed JSON node" logic but differ in whitespace handling (only
   `parseStatusFromIndexJson` calls `.strip()`) and error message style. Extract a private
   `parseStatusFromJsonNode(JsonNode root, String sourcePath)` helper that both methods delegate to, ensuring
   consistent `.strip()` and uniform error messages.

## Post-conditions

- `parseStatusFromIndexJson` checks `content.isBlank()` before calling `readTree(content)`.
- A private `parseStatusFromJsonNode(JsonNode, String)` helper exists; both `parseStatusFromIndexJson` and
  `parseStatusFromJson` delegate to it after parsing their root node.
- `rawStatus` is stripped consistently in both paths.
- Error messages use the same format: `"Missing 'status' field in <sourcePath>"`.
- All existing tests pass (`mvn -f client/pom.xml test`).
- New unit tests cover: blank content → returns "open", non-blank valid JSON, unknown status throws, missing
  status field throws.

## Execution Steps

1. In `GetStatusOutput.parseStatusFromIndexJson` (in
   `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`):
   - Move the `if (content.isBlank()) return "open";` guard to be the FIRST statement, before the try/catch
     that calls `readTree(content)`.

2. Extract a new private method `parseStatusFromJsonNode(JsonNode root, String sourcePath)`:
   - Takes the already-parsed root node and the source path string.
   - Gets `root.get("status")`, checks `statusNode == null || !statusNode.isString()`, throws
     `IOException("Missing 'status' field in " + sourcePath)`.
   - Gets `rawStatus = statusNode.asString().strip()`.
   - Looks up `IssueStatus.fromString(rawStatus)`, returns `status.toString()` if found.
   - Otherwise throws `IOException("Unknown status '" + rawStatus + "' in " + sourcePath + ".\n" + ...)`.

3. Update `parseStatusFromIndexJson` to call `parseStatusFromJsonNode(root, sourcePath)` instead of repeating
   the node-extraction logic.

4. Update `parseStatusFromJson` to call `parseStatusFromJsonNode(root, sourcePath)` instead of repeating the
   node-extraction logic.

5. Add or update unit tests in the test module to cover the four scenarios listed in Post-conditions.

6. Run `mvn -f client/pom.xml verify` and confirm all tests pass.

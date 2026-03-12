<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: implement-add-skill-handler-data

## Goal

Implement HANDLER_DATA generation for the `/cat:add` skill. Currently, `add/first-use.md` references
`HANDLER_DATA` 14 times (planning_valid, versions, branch_strategy, branch_pattern) but no Java code
generates it, causing Claude to silently improvise by scanning the filesystem manually. This issue
creates an `AddSkillData` SkillOutput class and wires it into `first-use.md` via an `<output>` section
with a preprocessor directive, so HANDLER_DATA is injected automatically on every invocation.

## Parent Requirements

None

## Research Findings

**Root cause analysis:**

1. `add-agent/SKILL.md` calls `skill-loader add-agent "$ARGUMENTS"` → `SkillLoader.main()` is invoked.
2. `SkillLoader.loadRawContent("add-agent")` looks for `skills/add-agent/first-use.md`, which does not
   exist, then falls back to `skills/add/first-use.md`.
3. `first-use.md` has zero `!` backtick preprocessor directives. `processPreprocessorDirectives()` is
   a no-op. The content passes through unchanged.
4. `first-use.md` references `HANDLER_DATA` 14 times but no Java code anywhere generates it.
5. No guard in `first-use.md` handles the case where HANDLER_DATA is entirely absent (only a guard
   for `planning_valid == false` exists, which assumes HANDLER_DATA is present).

**Correct fix pattern (confirmed via SkillLoader source):**

`SkillLoader.processContent()` calls `processPreprocessorDirectives()` first (expanding `!` directives),
then `parseContent()` splits on the `<output>` tag. Adding an `<output>` section to the end of
`first-use.md` with a `!` backtick directive causes:
- On first skill use: instructions wrapped in `<instructions skill="add">` tags, output body wrapped
  in `<output skill="add">` tags — both present.
- On subsequent uses: only `<output skill="add">` regenerated (fresh data each time). Instructions
  retrieved from the cached earlier message.

**Launcher registration:**

New launchers are registered in `client/build-jlink.sh` in the `HANDLERS` array (lines ~54–85) as
`"launcher-name:skills.ClassName"`. The build script generates the shell wrapper automatically.

**Existing pattern (reference implementation):** `GetAddOutput.java` in `io.github.cowwoc.cat.hooks.skills`
package — same constructor signature, same `getOutput(String[] args)` method, registered as
`"get-add-output:skills.GetAddOutput"` in `build-jlink.sh`.

**HANDLER_DATA JSON schema** (derived from all 14 references in first-use.md):
```json
{
  "planning_valid": true,
  "error_message": "",
  "branch_strategy": "per-issue",
  "branch_pattern": "v{version}/{issue-name}",
  "versions": [
    {
      "version": "2.1",
      "status": "in-progress",
      "summary": "First paragraph under ## Goal in version PLAN.md",
      "existing_issues": ["issue-bare-name-1", "issue-bare-name-2"],
      "issue_count": 42
    }
  ]
}
```

`branch_strategy` and `branch_pattern` are project conventions not stored in `cat-config.json`; hardcode
them as constants in `AddSkillData`.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Parsing STATE.md and PLAN.md for version data could fail if files are missing or
  malformed; `<output>` tag position in `first-use.md` must be at the very end to avoid truncating
  instructions.
- **Mitigation:** Use defensive reads (skip versions with missing STATE.md/PLAN.md rather than
  crashing); validate with a real `/cat:add` invocation in E2E post-condition.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/AddSkillData.java` — CREATE: new SkillOutput
  class that scans planning structure and returns HANDLER_DATA JSON
- `client/build-jlink.sh` — MODIFY: register `"add-skill-data:skills.AddSkillData"` in HANDLERS array
- `plugin/skills/add/first-use.md` — MODIFY: append `<output>` section with `!` backtick directive

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Create `client/src/main/java/io/github/cowwoc/cat/hooks/skills/AddSkillData.java`.

  **Class details:**
  - Package: `io.github.cowwoc.cat.hooks.skills`
  - License header (Java block comment style) at top
  - Implements `io.github.cowwoc.cat.hooks.util.SkillOutput`
  - Constructor: `public AddSkillData(JvmScope scope)` — validate `scope` not null via `requireThat`,
    store as field
  - Constants:
    - `private static final String BRANCH_STRATEGY = "per-issue";`
    - `private static final String BRANCH_PATTERN = "v{version}/{issue-name}";`
  - `getOutput(String[] args)` — validate `args` not null; ignore all args (no args expected); call
    `buildHandlerData()` and return result
  - `buildHandlerData()` — private method returning `String`, throws `IOException`:
    1. `Path issuesDir = scope.getClaudeProjectDir().resolve(".cat/issues");`
    2. If `issuesDir` does not exist: return JSON with `planning_valid: false`,
       `error_message: "Planning structure not found: " + issuesDir + ". Run /cat:init to initialize."`,
       empty `versions` array, empty `branch_strategy`/`branch_pattern`.
    3. Walk version directories: use `Files.walk(issuesDir, 2)` filtered to directories whose path
       relative to `issuesDir` matches pattern `v{digits}/v{digits}.{digits}` (e.g., `v2/v2.1`).
       Sort results to produce stable ordering.
    4. For each version directory: call `readVersionData(versionDir)` to get a `VersionData` record.
       Skip versions with status `"closed"`. Collect non-closed into a list.
    5. Build JSON using `JsonMapper` from `scope.getJsonMapper()`. Construct an `ObjectNode` with:
       - `planning_valid`: `true`
       - `error_message`: `""`
       - `branch_strategy`: `BRANCH_STRATEGY`
       - `branch_pattern`: `BRANCH_PATTERN`
       - `versions`: array of version objects
    6. Return `scope.getJsonMapper().writeValueAsString(root)`.
  - `readVersionData(Path versionDir)` — private method returning `VersionData` record, throws
    `IOException`:
    1. Extract version string from directory name: `versionDir.getFileName().toString().substring(1)`
       (strips leading `v` from `v2.1` → `"2.1"`).
    2. Read `versionDir/STATE.md`. If missing, return `VersionData` with status `"closed"` to skip.
    3. Parse status: scan STATE.md for a line matching `^status:\s*(.+)$` (case-insensitive). Extract
       the value. Trim whitespace. If not found, use `"open"`.
    4. Read `versionDir/PLAN.md`. If missing, use summary `""`.
    5. Parse summary: find the `## Goal` heading line. Take the first non-blank line after it as the
       summary. Truncate to 120 characters if longer.
    6. List issue directories: `Files.list(versionDir)` filtered to directories, excluding entries whose
       name matches `STATE.md`, `PLAN.md`, or `CHANGELOG.md`. Collect bare names (just `getFileName()`
       as string). Sort alphabetically. Count them as `issue_count`.
    7. Return `new VersionData(version, status, summary, existingIssues, issueCount)`.
  - `VersionData` — private record with fields: `String version`, `String status`, `String summary`,
    `List<String> existingIssues`, `int issueCount`. Add compact constructor validating non-null strings
    and non-null list.
  - Imports needed: `JvmScope`, `MainJvmScope`, `SkillOutput`, `com.fasterxml.jackson.databind.node.ObjectNode`,
    `com.fasterxml.jackson.databind.node.ArrayNode`, `Files`, `Path`, `List`, `ArrayList`,
    `requireThat`, standard Java.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/AddSkillData.java`

### Wave 2

- Register the launcher in `client/build-jlink.sh`.

  In the `HANDLERS` array (around line 85), add:
  ```
  "add-skill-data:skills.AddSkillData"
  ```
  Place it adjacent to the `"get-add-output:skills.GetAddOutput"` entry.
  - Files: `client/build-jlink.sh`

- Append `<output>` section to `plugin/skills/add/first-use.md`.

  Add the following block at the very end of the file (after the last existing line):
  ```markdown

  <output>
  !`"${CLAUDE_PLUGIN_ROOT}/client/bin/add-skill-data"`
  </output>
  ```
  The `<output>` tag must be at the end of the file so `parseContent()` can find it as the last
  `<output>` tag. No args are passed because `AddSkillData` reads project dir from `JvmScope`.
  - Files: `plugin/skills/add/first-use.md`

### Wave 3

- Run `mvn -f client/pom.xml verify` and fix any compilation errors, test failures, checkstyle, or PMD
  violations introduced by the changes. Then update `STATE.md` to closed.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/AddSkillData.java`,
    `.cat/issues/v2/v2.1/implement-add-skill-handler-data/STATE.md`

## Post-conditions

- [ ] `AddSkillData` class compiles and implements `SkillOutput` correctly
- [ ] `add-skill-data` launcher registered in `client/build-jlink.sh`
- [ ] `add/first-use.md` ends with an `<output>` section containing the `!` backtick directive
- [ ] `mvn -f client/pom.xml verify` passes with no errors
- [ ] E2E: invoke `/cat:add` and confirm the skill output contains `HANDLER_DATA` JSON (no unresolved
  `HANDLER_DATA` references remain in the execution context)

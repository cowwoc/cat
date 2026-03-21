# Plan: fix-add-skill-index-json-format

## Problem

The add skill (`plugin/skills/add/first-use.md` — served from cache at
`/home/node/.config/claude/plugins/cache/cat/cat/2.1/skills/add/first-use.md`) embeds Markdown templates instead
of JSON when generating `index.json` files. This causes Jackson to throw `"Unexpected character (#)"` when parsing
any `index.json` that was created using this skill. Three distinct problems exist:

1. **Line 22:** Reference to `${CLAUDE_PLUGIN_ROOT}/templates/issue-state.md` — file does not exist. The correct
   filename is `issue-index.json`.
2. **Lines 935–946 (`issue_create` step):** The embedded template uses Markdown format (`# State`, `- **Status:**`)
   instead of the JSON format that matches `plugin/templates/issue-index.json`.
3. **`version_create` step:** Major, minor, and patch `index.json` heredocs also use Markdown format instead of the
   JSON format defined in `major-state.md`, `minor-state.md` (which themselves use Markdown, but the actual
   version index.json files used in production are JSON — see `/workspace/.cat/issues/v2/index.json` for reference).
4. **Corrupted file:** `/workspace/.cat/issues/v2/v2.1/add-personality-questionnaire/index.json` contains Markdown
   content but must contain valid JSON: `{"status":"open","dependencies":["2.1-rename-config-options"],"blocks":[]}`.

## Parent Requirements

None

## Reproduction Code

```bash
# After running /cat:add, the generated index.json has content like:
cat .cat/issues/v2/v2.1/some-issue/index.json
# Output:
# # State
# - **Status:** open
# - **Progress:** 0%
# - **Dependencies:** []
# - **Blocks:** []
#
# Then /cat:status or any Java tool invoking Jackson fails with:
# Unexpected character ('#' (code 35)): was expecting valid start value
```

## Expected vs Actual

- **Expected:** `index.json` files contain valid JSON matching `plugin/templates/issue-index.json`:
  `{"status": "open", "dependencies": [], "blocks": []}`
- **Actual:** `index.json` files contain Markdown text starting with `# State`, causing
  `com.fasterxml.jackson.core.JsonParseException: Unexpected character ('#' (code 35))`

## Root Cause

The `issue_create` step in `first-use.md` (line 939) contains a fenced code block labeled `markdown` with
Markdown content instead of JSON. The `version_create` step has the same problem for major/minor/patch
`index.json` heredocs. Additionally, the reference file path on line 22 points to a non-existent file
`issue-state.md` (should be `issue-index.json`).

## Risk Assessment

- **Risk Level:** MEDIUM
- **Regression Risk:** The skill is used every time a new issue or version is created; wrong templates
  corrupt planning data silently.
- **Mitigation:** Add a regression test. Repair the existing corrupted file. Verify Jackson can parse the
  repaired and new files.

## Files to Modify

- `plugin/skills/add/first-use.md` — Fix line 22 (template filename) and the `issue_create` step
  (lines 935–946) and the `version_create` step heredocs for major/minor/patch index.json.
- `.cat/issues/v2/v2.1/add-personality-questionnaire/index.json` — Repair corrupted file to valid JSON.
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IndexJsonParsingTest.java` — Add regression test
  verifying index.json parsing.

## Test Cases

- [ ] Original bug scenario — `add-personality-questionnaire/index.json` is now valid JSON parseable by Jackson.
- [ ] New issues created via `/cat:add` produce index.json files that Jackson can parse without error.
- [ ] `/cat:status` runs without "Failed to parse index.json" errors.
- [ ] Regression test added: verifies that index.json files with JSON content parse correctly and files with
  Markdown content produce the expected Jackson error (negative test).

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Fix `plugin/skills/add/first-use.md` (source file; the cache copy at
  `/home/node/.config/claude/plugins/cache/cat/cat/2.1/skills/add/first-use.md` is read-only and must NOT be
  edited):
  - **Fix 1 — Line 22:** Change `${CLAUDE_PLUGIN_ROOT}/templates/issue-state.md` to
    `${CLAUDE_PLUGIN_ROOT}/templates/issue-index.json`.
  - **Fix 2 — `issue_create` step (lines 935–946 in cache copy):** The step instructs the agent to generate
    index.json using a Markdown template. Replace the instruction block:

    OLD text in skill (lines 935–946):
    ```
    **Generate index.json content:**

    Use appropriate template format:

    ```markdown
    # State

    - **Status:** open
    - **Progress:** 0%
    - **Dependencies:** [{dep1}, {dep2}] or []
    - **Blocks:** []
    ```
    ```

    NEW text (replace with):
    ```
    **Generate index.json content:**

    Construct the `indexContent` field as valid JSON matching `${CLAUDE_PLUGIN_ROOT}/templates/issue-index.json`:

    ```json
    {
      "status": "open",
      "dependencies": ["dep1", "dep2"],
      "blocks": []
    }
    ```

    Omit `dependencies` if the array is empty. Use only string values for dependency names (e.g., `"2.1-some-issue"`).
    ```

  - **Fix 3 — `version_create` step — major index.json heredoc (lines ~1457–1471 in cache copy):**

    OLD heredoc content:
    ```
    cat > "$VERSION_PATH/index.json" << EOF
    # Major Version $MAJOR State

    ## Status
    - **Status:** open
    - **Progress:** 0%

    ## Minor Versions
    - v$MAJOR.0

    ## Summary
    $VERSION_DESCRIPTION
    EOF
    ```

    NEW heredoc content:
    ```
    cat > "$VERSION_PATH/index.json" << EOF
    {"status": "open"}
    EOF
    ```

  - **Fix 4 — `version_create` step — minor index.json heredoc (lines ~1515–1532 in cache copy):**

    OLD heredoc content:
    ```
    cat > "$VERSION_PATH/index.json" << EOF
    # Minor Version $MAJOR.$MINOR State

    ## Status
    - **Status:** open
    - **Progress:** 0%

    ## Issues Pending
    (No issues yet)

    ## Issues Closed
    (None)

    ## Summary
    $VERSION_SUMMARY
    EOF
    ```

    NEW heredoc content:
    ```
    cat > "$VERSION_PATH/index.json" << EOF
    {"status": "open", "progress": 0}
    EOF
    ```

  - **Fix 5 — `version_create` step — patch index.json heredoc (lines ~1583–1600 in cache copy):**

    OLD heredoc content:
    ```
    cat > "$VERSION_PATH/index.json" << EOF
    # Patch Version $MAJOR.$MINOR.$PATCH State

    ## Status
    - **Status:** open
    - **Progress:** 0%

    ## Issues Pending
    (No issues yet)

    ## Issues Closed
    (None)

    ## Summary
    $VERSION_DESCRIPTION
    EOF
    ```

    NEW heredoc content:
    ```
    cat > "$VERSION_PATH/index.json" << EOF
    {"status": "open", "progress": 0}
    EOF
    ```

  - Files: `plugin/skills/add/first-use.md`

- Repair `.cat/issues/v2/v2.1/add-personality-questionnaire/index.json`:
  - Write valid JSON content: `{"status":"open","dependencies":["2.1-rename-config-options"],"blocks":[]}`
  - Files: `.cat/issues/v2/v2.1/add-personality-questionnaire/index.json`

### Wave 2

- Add regression test:
  - Create `client/src/test/java/io/github/cowwoc/cat/hooks/test/IndexJsonParsingTest.java`:
    - Package: `io.github.cowwoc.cat.hooks.test`
    - Use `TestJvmScope(tempDir, tempDir)` (not `MainJvmScope`) and obtain `JsonMapper` via
      `scope.getJsonMapper()`.
    - Test 1 (`validJson_parsesWithoutError`): Parse the string `{"status":"open","dependencies":[],"blocks":[]}`
      via `mapper.readTree(content)` and assert the result is not null and `result.get("status").asText()` equals
      `"open"`.
    - Test 2 (`markdownContent_throwsJsonParseException`): Parse the string
      `"# State\n\n- **Status:** open\n- **Progress:** 0%\n"` via `mapper.readTree(content)` and assert it throws
      `com.fasterxml.jackson.core.JsonParseException` (use `@Test(expectedExceptions = JsonParseException.class)`).
    - Follow Java conventions: Allman braces, 2-space indent, TestNG `@Test`, no `@BeforeMethod`, self-contained
      methods with `try-with-resources`.
    - Add license header per `.claude/rules/license-header.md` (Java block comment before `package`).
  - Run `mvn -f /workspace/client/pom.xml test` to confirm all tests pass.
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/IndexJsonParsingTest.java`

## Post-conditions

- [ ] Line 22 of `plugin/skills/add/first-use.md` references
  `${CLAUDE_PLUGIN_ROOT}/templates/issue-index.json` (not `issue-state.md`)
- [ ] Issue `index.json` instruction in `issue_create` step of `plugin/skills/add/first-use.md` uses JSON
  format matching `plugin/templates/issue-index.json`
- [ ] Version creation heredocs (major/minor/patch) in `plugin/skills/add/first-use.md` produce valid JSON
  for `index.json`, not Markdown
- [ ] `/workspace/.cat/issues/v2/v2.1/add-personality-questionnaire/index.json` repaired to valid JSON:
  `{"status":"open","dependencies":["2.1-rename-config-options"],"blocks":[]}`
- [ ] New issues created via `/cat:add` produce `index.json` files parseable by Jackson without error
- [ ] `/cat:status` runs without "Failed to parse index.json" errors
- [ ] Regression test added and passes

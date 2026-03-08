# Plan: fix-state-schema-validator-edit-blind-spot

## Problem

`StateSchemaValidator` is registered for both `Write` and `Edit` PreToolUse events but only validates
`Write` operations. For `Write`, the tool input contains a `content` field with the full file content.
For `Edit`, the tool input contains `file_path`, `old_string`, and `new_string` — no `content` field.

`StateSchemaValidator.check()` calls `getStringOrDefault(toolInput, "content", "")`, which returns `""`
for Edit calls. The validator then hits `if (content.isEmpty()) return allow()` — bypassing all schema
checks. This allowed a fix subagent to add `- **Stakeholder Review:** Concerns addressed` to STATE.md
via an Edit call without being blocked.

## Parent Requirements

None — bug fix in existing enforcement

## Reproduction Code

```java
// Edit tool input (not caught):
{
  "file_path": ".claude/cat/issues/v2/v2.1/my-issue/STATE.md",
  "old_string": "- **Blocks:** []",
  "new_string": "- **Blocks:** []\n- **Stakeholder Review:** xyz"
}
// StateSchemaValidator returns allow() because content is empty
```

## Expected vs Actual

- **Expected:** Edit adding `- **Stakeholder Review:** xyz` is blocked with schema violation
- **Actual:** Edit is allowed; validator returns `allow()` immediately on empty `content`

## Root Cause

`StateSchemaValidator.check()` only reads `content` (the Write-tool field). For Edit calls, `content`
is absent from the tool input JSON, so the method returns `allow()` before running any validation.

## Approaches

### A: Read file from disk and apply the edit in memory (chosen)
- **Risk:** LOW
- **Scope:** 1 file + tests
- **Description:** When `content` is empty but `new_string` is present, read the on-disk STATE.md file,
  perform the string replacement (`old_string` → `new_string`) in memory, then validate the resulting
  content. This gives a complete picture of the file after the edit.
- **Chosen:** Provides full post-edit content for comprehensive schema validation.

### B: Only validate the `new_string` fragment
- **Risk:** MEDIUM
- **Scope:** 1 file + tests
- **Description:** Parse only the `new_string` field for schema-violating key-value pairs.
- **Rejected:** Would miss cases where the full post-edit file violates schema (e.g., removing a
  mandatory key) and would not catch multi-line patterns split across `old_string` and `new_string`.

### C: Keep separate Write/Edit handlers
- **Risk:** LOW
- **Scope:** 2 files + tests
- **Description:** Create a separate `StateSchemaValidatorEditHandler` that reads new_string only.
- **Rejected:** Unnecessary complexity; the single-method approach handles both cases cleanly.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** File read may fail if the STATE.md doesn't exist yet (new file being written
  via an Edit — unusual but possible). Fail-open: if disk read fails, allow the edit.
- **Mitigation:** Wrap disk read in try-catch; fall back to allow() on IOException.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java`
  - Add `import java.io.IOException;` (already present via Files.readString usage chain)
  - Add `import java.nio.file.Files;` (likely already present)
  - Add `import static java.nio.charset.StandardCharsets.UTF_8;`
  - In `check()`, after `String content = getStringOrDefault(toolInput, "content", "");`, add logic
    to handle Edit calls:
    ```java
    if (content.isEmpty())
    {
      // Edit tool call: reconstruct post-edit content by applying new_string to on-disk file
      String newString = getStringOrDefault(toolInput, "new_string", "");
      if (newString.isEmpty())
        return FileWriteHandler.Result.allow();
      String oldString = getStringOrDefault(toolInput, "old_string", "");
      content = applyEdit(filePath, oldString, newString);
      if (content.isEmpty())
        return FileWriteHandler.Result.allow(); // disk read failed; fail-open
    }
    ```
  - Add private method `applyEdit(String filePath, String oldString, String newString)`:
    ```java
    private String applyEdit(String filePath, String oldString, String newString)
    {
      try
      {
        String diskContent = Files.readString(Path.of(filePath), UTF_8);
        return diskContent.replace(oldString, newString);
      }
      catch (IOException e)
      {
        return "";
      }
    }
    ```
  - `applyEdit` returns `""` on IOException → caller returns `allow()` (fail-open)

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StateSchemaValidatorTest.java`
  - Add `editWithNonStandardFieldIsBlocked()`:
    - Create a temp STATE.md file with valid content on disk
    - Build Edit toolInput: `file_path`, `old_string="- **Blocks:** []"`,
      `new_string="- **Blocks:** []\n- **Stakeholder Review:** xyz"`
    - Assert `result.blocked()` is true, reason contains `"Stakeholder Review"`
  - Add `editWithValidChangeIsAllowed()`:
    - Create temp STATE.md with valid content on disk
    - Build Edit toolInput that changes `Status: open` to `Status: closed` and adds
      `Resolution: implemented`
    - Assert `!result.blocked()`
  - Add `editWithMissingFileFailsOpen()`:
    - Build Edit toolInput with non-existent file_path
    - Assert `!result.blocked()` (fail-open behavior)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `StateSchemaValidator.java`:
  - Add `import static java.nio.charset.StandardCharsets.UTF_8;` (static import)
  - Add `import java.nio.file.Path;` if not already present
  - Add private `applyEdit(String filePath, String oldString, String newString)` method (see Files
    to Modify section for exact implementation)
  - In `check()`, replace the `if (content.isEmpty()) return FileWriteHandler.Result.allow();` guard
    with the Edit-handling block described in Files to Modify

- In `StateSchemaValidatorTest.java`:
  - Add `editWithNonStandardFieldIsBlocked()` test
  - Add `editWithValidChangeIsAllowed()` test
  - Add `editWithMissingFileFailsOpen()` test

- Run `mvn -f client/pom.xml test` and verify all tests pass
- Update STATE.md: status=closed, progress=100%

## Post-conditions

- [ ] `StateSchemaValidator` blocks Edit calls that add non-standard STATE.md fields
- [ ] `StateSchemaValidator` allows Edit calls that only modify valid fields
- [ ] `StateSchemaValidator` fails-open when the on-disk file cannot be read
- [ ] Test `editWithNonStandardFieldIsBlocked` passes
- [ ] Test `editWithValidChangeIsAllowed` passes
- [ ] Test `editWithMissingFileFailsOpen` passes
- [ ] All existing Write-based tests still pass
- [ ] `mvn -f client/pom.xml test` exits 0 with no failures

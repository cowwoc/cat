# Plan: fix-verify-state-in-commit-keyword

## Problem

`VerifyStateInCommit.check()` warns when the staged STATE.md content does not contain the string
`"completed"`. However, `"completed"` is not a valid CAT issue status — the valid closed status is
`"closed"`. Since no valid STATE.md ever contains `"completed"`, the warn fires on every
implementation commit that stages STATE.md with any status (including valid ones), making the check
permanently dead code that produces spurious warnings.

## Parent Requirements

None — bug fix in enforcement hook

## Reproduction Code

```bash
# In any CAT worktree, close an issue normally:
git add .cat/issues/v2/v2.1/some-issue/STATE.md
git commit -m "bugfix: fix something"
# Hook warns: "STATE.md is staged but does not contain 'completed' status"
# even though STATE.md correctly contains "Status: closed"
```

## Expected vs Actual

- **Expected:** Warn only when STATUS is not "closed" (i.e., issue is being committed as incomplete)
- **Actual:** Warn on every implementation commit that stages STATE.md, because "completed" never
  appears in valid STATE.md files

## Root Cause

String literal `"completed"` on line 83 of `VerifyStateInCommit.java` should be `"closed"`.
The warn condition is inverted relative to its intent: it fires when the status is not the (nonexistent)
string "completed", which is always true.

## Approaches

### A: Minimal string fix (change "completed" → "closed")
- **Risk:** LOW
- **Scope:** 1 file + 1 test
- **Description:** Replace the literal string in the contains() check and update the test assertion.
  Still relies on substring match, so coincidental "closed" appearances in STATE.md content would
  suppress the warning incorrectly.
- **Rejected:** Fragile — "closed" could appear in issue names in Dependencies/Blocks fields,
  suppressing the warning incorrectly.

### B: Proper status field parsing using IssueStatus enum (chosen)
- **Risk:** LOW
- **Scope:** 1 file + 1 test
- **Description:** Extract the `Status:` field from STATE.md content using a regex, parse it with
  `IssueStatus.fromString()`, and check `status == IssueStatus.CLOSED`. This is robust against
  coincidental "closed" substrings in other fields (e.g., Dependencies listing a "closed-issue").
  `IssueStatus` already exists in the codebase.

### C: Delete the warn entirely
- **Rejected:** The warn provides useful signal when an agent forgets to mark an issue as closed
  before committing. Removing it would reduce visibility into missing STATE.md updates.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** The existing test `warnsWhenStateMdNotCompleted` asserts the wrong behavior
  and must be updated; otherwise it will fail after the fix.
- **Mitigation:** Update test to assert the corrected behavior; run full test suite.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/VerifyStateInCommit.java`
  - Add `import static java.nio.charset.StandardCharsets.UTF_8;` (if not already present)
  - Add `import io.github.cowwoc.cat.hooks.IssueStatus;` import
  - Add `private static final Pattern STATUS_PATTERN = Pattern.compile("^- \\*\\*Status:\\*\\* (.+)$",
    Pattern.MULTILINE);` constant
  - Replace `stateMdContent.contains("completed")` check with proper parsing:
    ```java
    Matcher statusMatcher = STATUS_PATTERN.matcher(stateMdContent);
    boolean isClosed = statusMatcher.find() &&
      IssueStatus.fromString(statusMatcher.group(1).strip()) == IssueStatus.CLOSED;
    if (!isClosed) { return Result.warn(...); }
    ```
  - Update warn message to say `"does not contain 'closed' status"` instead of `"'completed'"`
  - Update class Javadoc: change `"completed"` to `"closed"`

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/VerifyStateInCommitTest.java`
  - Update `warnsWhenStateMdNotCompleted()`: write STATE.md with `Status: open` (not "closed"),
    assert `result.reason()` contains `"closed"` (not `"completed"`)
  - Add `noWarnWhenStateMdIsClosed()`: write STATE.md with `Status: closed`, assert `!result.warned()`

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `VerifyStateInCommit.java`:
  - Add import `import io.github.cowwoc.cat.hooks.IssueStatus;`
  - Add constant: `private static final Pattern STATUS_PATTERN = Pattern.compile("^-
    \\*\\*Status:\\*\\* (.+)$", Pattern.MULTILINE);`
  - Replace the block starting `if (!stateMdContent.isEmpty() && !stateMdContent.contains("completed"))`
    with:
    ```java
    if (!stateMdContent.isEmpty())
    {
      Matcher statusMatcher = STATUS_PATTERN.matcher(stateMdContent);
      boolean isClosed = statusMatcher.find() &&
        IssueStatus.fromString(statusMatcher.group(1).strip()) == IssueStatus.CLOSED;
      if (!isClosed)
      {
        return Result.warn(
          "STATE.md is staged but does not contain 'closed' status. " +
          "Verify the issue status is correct before committing.");
      }
    }
    ```
  - Update class Javadoc: replace `"completed"` with `"closed"` in the description
  - Add `import java.util.regex.Matcher;` if not already present (it is — already imports Pattern)

- In `VerifyStateInCommitTest.java`:
  - Update `warnsWhenStateMdNotCompleted()` (rename to `warnsWhenStateMdNotClosed`):
    - Write STATE.md with `Status: open\n` content (already does, just verify it's not "closed")
    - Change assertion from `contains("'completed'")` to `contains("closed")`
  - Add `noWarnWhenStateMdIsClosed()`:
    - Set up a git worktree with STATE.md containing `- **Status:** closed`
    - Assert `!result.warned()` and `result.reason().isEmpty()`

- Run `mvn -f client/pom.xml test` and verify all tests pass
- Update STATE.md: status=closed, progress=100%

## Post-conditions

- [ ] `VerifyStateInCommit.check()` warns only when STATUS field is not "closed"
- [ ] `VerifyStateInCommit.check()` does NOT warn when STATE.md contains `Status: closed`
- [ ] Test `warnsWhenStateMdNotClosed` passes: STATUS=open → warn containing "closed"
- [ ] Test `noWarnWhenStateMdIsClosed` passes: STATUS=closed → no warning
- [ ] Class Javadoc says "closed" not "completed"
- [ ] `mvn -f client/pom.xml test` exits 0 with no failures

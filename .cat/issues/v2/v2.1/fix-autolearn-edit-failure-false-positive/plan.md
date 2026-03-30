# Bugfix: fix-autolearn-edit-failure-false-positive

## Problem

`AutoLearnMistakes.java` Pattern 5 (`EDIT_FAILURE_PATTERN`) has no tool-type guard. It fires when
any tool's output contains `"String to replace not found"` or `"old_string not found"` — including
Bash commands whose stdout is a `git diff` or `git show` output containing Java source code that
references these strings as pattern literals.

**Concrete trigger:** Running `grep -n "old_string not found" AutoLearnMistakes.java` or
`git show <commit>` where the diff shows Java source containing `"old_string not found"` produces
a false `edit_failure` detection even though no Edit tool call failed.

This is a recurrence of the broader filtering issue (M461, M466, M555, M563): AutoLearnMistakes
patterns apply to all tools without per-pattern tool-type guards.

## Root Cause

Pattern 5's `EDIT_FAILURE_PATTERN` check at line 302–306 of `AutoLearnMistakes.java` is not guarded
by a `toolName.equals("Edit")` condition. The pattern only makes semantic sense for the Edit tool,
which returns "String to replace not found" when `old_string` is absent from the file. For any other
tool (Bash, Read, Grep, etc.) the strings are incidental content — Java source code, documentation,
or diff output — and must not trigger mistake detection.

## Satisfies

None - infrastructure/reliability improvement

## Post-conditions

- [ ] Pattern 5 in `AutoLearnMistakes.detectMistake()` is guarded by `toolName.equals("Edit")`
- [ ] Running a Bash command whose stdout contains `"old_string not found"` (e.g., grep on Java source) does NOT trigger `edit_failure`
- [ ] Running `git show` or `git diff` whose diff contains `"old_string not found"` in Java source does NOT trigger `edit_failure`
- [ ] When the Edit tool itself returns `"String to replace not found"`, `edit_failure` IS still detected correctly
- [ ] When the Edit tool itself returns `"old_string not found"`, `edit_failure` IS still detected correctly
- [ ] Tests in `AutoLearnMistakesTest` verify the Bash false-positive scenario (no detection)
- [ ] Tests in `AutoLearnMistakesTest` verify the Edit true-positive scenario (detection fires)
- [ ] All existing `AutoLearnMistakes` tests continue to pass
- [ ] `mvn -f client/pom.xml verify -e` passes

## Implementation

Add a `toolName.equals("Edit")` guard to Pattern 5 in `AutoLearnMistakes.java`:

```java
// Pattern 5: Edit tool failures — only the Edit tool returns this message
if (toolName.equals("Edit") && EDIT_FAILURE_PATTERN.matcher(filtered).find())
{
  String editPattern = "string to replace not found|old_string not found";
  return new MistakeDetection("edit_failure", extractContext(filtered, editPattern, 2));
}
```

Add tests to `AutoLearnMistakesTest` (or equivalent test class) verifying:
1. `toolName="Bash"`, stdout contains `"old_string not found"` → no detection
2. `toolName="Bash"`, stdout is a git diff containing Java source with `"old_string not found"` → no detection
3. `toolName="Edit"`, output contains `"String to replace not found"` → `edit_failure` detection
4. `toolName="Edit"`, output contains `"old_string not found"` → `edit_failure` detection

## Pre-conditions

- [ ] All dependent issues are closed

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java` — add `toolName.equals("Edit") &&` guard to Pattern 5
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/AutoLearnMistakesTest.java` — add false-positive and true-positive tests for Pattern 5

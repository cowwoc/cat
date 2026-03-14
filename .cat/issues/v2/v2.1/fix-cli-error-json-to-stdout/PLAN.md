# Plan: fix-cli-error-json-to-stdout

## Objective
Add a convention to `java.md` requiring CLI tools invoked by skills to write ALL structured JSON
output (including errors) to stdout. Reserve stderr for non-structured diagnostics only. Then fix
all existing violations across the codebase.

## Background
CLI tools like `work-prepare`, `git-squash`, `git-rebase`, etc. are invoked as subprocesses by
skills. The calling skill parses JSON from stdout. When error JSON is written to stderr instead,
the skill receives empty stdout and treats it as "no result" — losing the structured error message
and potentially triggering incorrect recovery behavior (M542).

## Convention to Add

In `.claude/rules/java.md`, add a new section:

### CLI Tool Output Routing

CLI tools invoked by skills (via `main()` methods) must write ALL structured JSON output to
`System.out` — including error responses. `System.err` is reserved for non-structured diagnostics
(e.g., debug logging, stack traces during development) that are not intended for skill consumption.

**Why:** Skills parse JSON from stdout. Errors written to stderr are invisible to the calling skill,
causing it to treat the invocation as "no result" and potentially take incorrect recovery actions.

**Pattern:**
```java
// Good — all JSON to stdout, even errors
catch (IOException e)
{
  System.out.println("""
    {
      "status": "ERROR",
      "message": "%s"
    }""".formatted(e.getMessage().replace("\"", "\\\"")));
  System.exit(1);
}

// Avoid — error JSON to stderr (invisible to calling skill)
catch (IOException e)
{
  System.err.println("""
    {
      "status": "ERROR",
      "message": "%s"
    }""".formatted(e.getMessage().replace("\"", "\\\"")));
  System.exit(1);
}
```

## Files to Modify

### Convention
- `.claude/rules/java.md` — Add "CLI Tool Output Routing" section after "Text Blocks for Static JSON"

### Java Source (violations to fix)
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — Line ~1807: change
  `System.err.println` to `System.out.println` in the RuntimeException|AssertionError catch block
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebase.java` — Lines ~710-760: change
  all `System.err.println` JSON outputs to `System.out.println` in main()
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java` — Lines ~338-388: change
  all `System.err.println` JSON outputs to `System.out.println` in main()
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitAmend.java` — Lines ~259-346: change
  all `System.err.println` JSON outputs to `System.out.println` in main()
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitMergeLinear.java` — Lines ~282-350:
  change all `System.err.println` JSON outputs to `System.out.println` in main()
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java` — Line ~2273: change
  plain text error to JSON format on `System.out.println`

### Java Tests
- Update any tests that assert stderr output for these CLI tools to assert stdout instead

### Documentation
- Update the "Text Blocks for Static JSON" example in `java.md` to use `System.out` instead of
  `System.err` for the error JSON example (currently contradicts the new convention)

## Pre-conditions
- [ ] All dependent issues are closed

## Post-conditions
- [ ] Convention section "CLI Tool Output Routing" exists in `.claude/rules/java.md`
- [ ] The "Text Blocks for Static JSON" example in `java.md` uses `System.out` for error JSON
- [ ] All `main()` methods in CLI tools write JSON (including errors) to `System.out`
- [ ] No `main()` method writes structured JSON to `System.err`
- [ ] All tests pass: `mvn -f client/pom.xml test`
- [ ] E2E: Invoke a CLI tool with invalid args and confirm error JSON appears on stdout

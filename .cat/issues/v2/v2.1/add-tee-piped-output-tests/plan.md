# Plan

## Goal

Run instruction-builder on tee-piped-output skill and add enforcement tests to ensure piped bash commands use tee.

## Pre-conditions

(none)

## Post-conditions

- [ ] Instruction-builder has been run on the tee-piped-output skill, producing valid optimized instructions
- [ ] Enforcement tests exist that verify piped bash commands without tee are detected as non-compliant
- [ ] Enforcement tests verify that compliant piped commands (using tee) pass validation
- [ ] All existing tests continue to pass (no regressions)
- [ ] E2E verification: Run instruction-builder on tee-piped-output and confirm output is valid; run tests confirming non-compliant piped commands are flagged

## Research Findings

The tee-piped-output enforcement is currently a behavioral rule in `plugin/rules/tee-piped-output.md` (injected into
agent sessions as context). There is no automated hook enforcement. The codebase has an existing pattern for bash
command validation:

- `BashHandler` interface in `io.github.cowwoc.cat.hooks` with `Result.allow()`, `Result.block()`, `Result.warn()`
- Handlers registered in `PreToolUseHook` constructor's `List.of(...)` chain
- `TestUtils.bashHook()` creates test `ClaudeHook` scopes with bash command payloads
- Existing handlers like `WarnFileExtraction` demonstrate the warn pattern for non-blocking checks
- `WarnMainWorkspaceCommit` demonstrates comprehensive test patterns with multiple scenarios

The `check(ClaudeHook scope)` method receives the scope which provides `scope.getCommand()` to access the bash
command string.

## Approach

Create a `WarnPipedWithoutTee` bash handler that warns (does not block) when a piped bash command lacks `tee` in the
pipeline. Register it in `PreToolUseHook`. Write comprehensive TestNG tests. The instruction-builder post-condition
is satisfied by invoking `/cat:instruction-builder-agent` on the `plugin/rules/tee-piped-output.md` rule during the
main agent waves.

## Main Agent Waves

- /cat:instruction-builder-agent goal="optimize tee-piped-output rule instructions" target="plugin/rules/tee-piped-output.md"

## Sub-Agent Waves

### Wave 1

- Create `client/src/main/java/io/github/cowwoc/cat/hooks/bash/WarnPipedWithoutTee.java`:
  - Implements `BashHandler`
  - In `check(ClaudeHook scope)`:
    - Get command via `scope.getCommand()`
    - Check if command contains a pipe (`|`) — use a regex or string check that handles:
      - Pipes inside single or double quotes (should NOT count as a pipe operator)
      - Multiple pipes in a single command
      - The `||` operator (logical OR, not a pipe — must not trigger)
    - If no pipe found, return `Result.allow()`
    - If pipe found, check if `tee` appears in the pipeline
    - Exempt cases (return `Result.allow()`):
      - Simple pipes where re-filtering is unnecessary (e.g., `echo "hello" | wc -c`)
      - Commands using `run_in_background` (not detectable from command string — skip this)
      - Commands where `tee` is already present in the pipeline
      - Pipes to simple formatters: `| head`, `| tail`, `| wc`, `| sort`, `| uniq`, `| cut`, `| tr`
        (these are typically simple one-shot operations that don't need re-filtering)
    - For non-exempt piped commands without tee, return `Result.warn()` with a message explaining:
      - What was detected (piped command without tee)
      - The recommended pattern (create LOG_FILE, use tee, cleanup)
      - Reference to the tee-piped-output rule
  - Constructor: no-arg public constructor (handler requires no scope)
  - License header as per `.claude/rules/license-header.md`
  - Follow all Java conventions from `.claude/rules/java.md` (Allman braces, Javadoc, etc.)

- Register `WarnPipedWithoutTee` in `client/src/main/java/io/github/cowwoc/cat/hooks/PreToolUseHook.java`:
  - Add import: `import io.github.cowwoc.cat.hooks.bash.WarnPipedWithoutTee;`
  - Add `new WarnPipedWithoutTee()` to the `List.of(...)` in the constructor, before `new RequireSkillForCommand(scope)` (which should remain last)

- Create `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnPipedWithoutTeeTest.java`:
  - License header
  - Test class with Javadoc
  - Each test method must follow the `WarnFileExtractionTest` pattern:
    - Create `Path tempDir = Files.createTempDirectory("test-");`
    - Use `try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))` with `finally { TestUtils.deleteDirectoryRecursively(tempDir); }`
    - Create handler and call `handler.check(TestUtils.bashHook(command, WORKING_DIR, SESSION_ID, scope))`
    - Use constants: `private static final String SESSION_ID = "00000000-0000-0000-0000-000000000000";` and `private static final String WORKING_DIR = "/tmp";`
  - Tests (each self-contained, no shared state):
    1. `pipedCommandWithoutTeeEmitsWarning` — `"some-command 2>&1 | grep pattern"` → warn with non-empty reason
    2. `pipedCommandWithTeeIsAllowed` — `"some-command 2>&1 | tee /tmp/log.txt | grep pattern"` → allow (empty reason)
    3. `commandWithoutPipeIsAllowed` — `"git status"` → allow
    4. `simplePipeToHeadIsAllowed` — `"git log --oneline | head -5"` → allow (exempt formatter)
    5. `simplePipeToTailIsAllowed` — `"cat file.txt | tail -20"` → allow (exempt formatter)
    6. `simplePipeToWcIsAllowed` — `'echo "hello" | wc -c'` → allow (exempt formatter)
    7. `simplePipeToSortIsAllowed` — `"ls | sort"` → allow (exempt formatter)
    8. `pipeToGrepWithoutTeeEmitsWarning` — `"mvn test 2>&1 | grep ERROR"` → warn
    9. `multiplePipesWithTeeIsAllowed` — `"cmd | tee /tmp/out.log | grep pattern | head -5"` → allow
    10. `logicalOrDoesNotTrigger` — `"test -f file.txt || echo missing"` → allow (|| is not a pipe)
    11. `pipeInsideQuotesDoesNotTrigger` — `"echo 'a|b' > file.txt"` → allow (pipe inside quotes)
    12. `warningMessageContainsGuidance` — verify warn reason contains "tee" and "LOG_FILE" or "temporary" or "log file"
    13. `simplePipeToUniqIsAllowed` — `"sort file.txt | uniq"` → allow (exempt formatter)
    14. `simplePipeToCutIsAllowed` — `'echo "a:b:c" | cut -d: -f1'` → allow (exempt formatter)
    15. `simplePipeToTrIsAllowed` — `'echo "HELLO" | tr A-Z a-z'` → allow (exempt formatter)
    16. `chainedCommandWithPipeWithoutTeeEmitsWarning` — `"cd /tmp && some-command 2>&1 | grep error"` → warn
    17. `pipeToAwkWithoutTeeEmitsWarning` — `"ps aux | awk '{print $1}'"` → warn (awk is not a simple formatter)
    18. `pipeToSedWithoutTeeEmitsWarning` — `"cat file.txt | sed 's/old/new/'"` → warn (sed is not a simple formatter)
  - Use `requireThat` from requirements.java for assertions
  - Each test method has Javadoc describing what it verifies
  - Commit type: `test:`

- Update `index.json` in the issue directory to set status to `closed` and progress to `100`

Commit types:
- Handler + registration: `feature: add WarnPipedWithoutTee bash handler for tee enforcement`
- Tests: `test: add WarnPipedWithoutTee enforcement tests`
- index.json: include in same commit as tests

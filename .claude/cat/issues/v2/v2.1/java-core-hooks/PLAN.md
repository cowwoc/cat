# Plan: java-core-hooks

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 2 of 5
- **Estimated Tokens:** 20K

## Objective
Wire up remaining 5 Java entry points in hooks.json and verify output matches Python. The Java implementations already
exist; the work is switching hooks.json from Python to Java and validating identical behavior.

## Scope
- Wire up 5 entry points in `plugin/hooks/hooks.json` (GetBashPretoolOutput already done)
- Verify each Java entry point produces identical JSON output to its Python equivalent
- Core infrastructure classes (Config, HookInput, HookOutput, etc.) already exist

## Dependencies
- java-jdk-infrastructure (JDK runner scripts must exist)
- add-java-build-to-ci (JAR must be built before hooks can invoke Java)

## Entry Points to Wire Up

Each entry point already has a Java implementation. The work is replacing the Python command in hooks.json with the
java_runner.sh invocation.

| hooks.json Line | Python Command | Java Class | hooks.json Change |
|-----------------|---------------|------------|-------------------|
| ~50 | `python3 .../get-skill-output.py` | `GetSkillOutput` | `${CLAUDE_PLUGIN_ROOT}/hooks/jdk/java_runner.sh GetSkillOutput` |
| ~96 | `python3 .../get-read-pretool-output.py` | `GetReadPretoolOutput` | `${CLAUDE_PLUGIN_ROOT}/hooks/jdk/java_runner.sh GetReadPretoolOutput` |
| ~142 | `python3 .../get-posttool-output.py` | `GetPosttoolOutput` | `${CLAUDE_PLUGIN_ROOT}/hooks/jdk/java_runner.sh GetPosttoolOutput` |
| ~159 | `python3 .../get-bash-posttool-output.py` | `GetBashPosttoolOutput` | `${CLAUDE_PLUGIN_ROOT}/hooks/jdk/java_runner.sh GetBashPosttoolOutput` |
| ~178 | `python3 .../get-posttool-output.py` | `GetPosttoolOutput` | `${CLAUDE_PLUGIN_ROOT}/hooks/jdk/java_runner.sh GetPosttoolOutput` |
| ~186 | `python3 .../get-read-posttool-output.py` | `GetReadPosttoolOutput` | `${CLAUDE_PLUGIN_ROOT}/hooks/jdk/java_runner.sh GetReadPosttoolOutput` |

## Existing Java Files (already implemented)

Entry points at `plugin/hooks/src/io/github/cowwoc/cat/hooks/`:
- `GetSkillOutput.java`
- `GetReadPretoolOutput.java`
- `GetPosttoolOutput.java`
- `GetBashPosttoolOutput.java`
- `GetReadPosttoolOutput.java`

Core infrastructure (already implemented):
- `Config.java`, `HookInput.java`, `HookOutput.java`
- `JvmScope.java`, `DefaultJvmScope.java`, `Strings.java`
- `BashHandler.java`, `PosttoolHandler.java`, `PromptHandler.java`, `ReadHandler.java`

## Files to Modify
- `plugin/hooks/hooks.json` - Replace 5 Python commands with java_runner.sh calls

## Execution Steps
1. **Wire up GetSkillOutput** - Edit hooks.json line ~50, test by invoking a skill
2. **Wire up GetReadPretoolOutput** - Edit hooks.json line ~96, test by reading a file
3. **Wire up GetPosttoolOutput** - Edit hooks.json lines ~142 and ~178, test by running any tool
4. **Wire up GetBashPosttoolOutput** - Edit hooks.json line ~159, test by running a bash command
5. **Wire up GetReadPosttoolOutput** - Edit hooks.json line ~186, test by reading a file
6. **Run full test suite** - `python3 /workspace/run_tests.py` to verify no regressions

## Acceptance Criteria
- [ ] All 5 remaining entry points wired to Java in hooks.json
- [ ] Each Java entry point produces identical JSON output to Python equivalent
- [ ] All existing tests pass (`python3 /workspace/run_tests.py` exit code 0)
- [ ] No Python `get-*-output.py` commands remain in hooks.json

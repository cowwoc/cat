# Fix SPRT Infrastructure Bug

## Type
bugfix

## Goal
Fix the SPRT test infrastructure bug that prevents SPRT from running. The bug is in InstructionTestRunner.java at line 2240, where it uses the obsolete `--subagent-type` argument that doesn't exist in the current claude-runner binary.

## Problem
- Location: `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/InstructionTestRunner.java:2240`
- Current code: `"--subagent-type", "instruction-grader-agent",`
- Error: `Unknown argument: --subagent-type. Valid arguments: --prompt-file <path>, --model, --cwd, --plugin-source, --jlink-bin, --plugin-version, --agent, --append-system-prompt, --output`
- Impact: SPRT cannot run any test cases because grading phase fails

## Solution
Replace `--subagent-type` with `--agent` argument:
```java
"--agent", "instruction-grader-agent",
```

## Post-conditions
- [ ] Line 2240 changed from `"--subagent-type"` to `"--agent"`
- [ ] SPRT can successfully run test cases through the grading phase
- [ ] Build passes (`mvn -f client/pom.xml verify -e`)

## Jobs

### Job 1: Fix the argument

1. Edit `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/InstructionTestRunner.java`
2. Change line 2240 from `"--subagent-type", "instruction-grader-agent",` to `"--agent", "instruction-grader-agent",`
3. Commit with message: `bugfix: fix SPRT grader invocation - replace --subagent-type with --agent`
4. Run build to verify: `mvn -f client/pom.xml verify -e`

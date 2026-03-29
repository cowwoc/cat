# Plan

## Goal

Add agent-compliance tests for plugin/rules/tee-piped-output.md covering the tee rule requirements, exceptions, and cleanup behavior.

## Pre-conditions

(none)

## Post-conditions

- [ ] Agent-compliance tests exist in `plugin/tests/rules/tee-piped-output/` covering the core tee requirement (agent uses mktemp + tee when running a piped Bash command)
- [ ] Tests verify agent captures stderr alongside stdout using the `2>&1 | tee "$LOG_FILE"` pattern
- [ ] Tests verify agent stores the log file path in a `LOG_FILE` variable and uses it consistently in both `tee` invocation and `rm -f` cleanup
- [ ] Tests verify agent cleans up the log file with `rm -f "$LOG_FILE"` after use
- [ ] Tests verify that `run_in_background=true` commands are exempt from the tee rule
- [ ] Tests verify agent uses tee even for simple/short piped commands (mandatory, no exemption for brevity)
- [ ] All existing tests continue to pass (no regressions)

## Research Findings

The tee rule in `plugin/rules/tee-piped-output.md` specifies:
- **Mandatory:** Any Bash command containing a pipe (`|`) must capture pre-pipe output using mktemp + tee
- **Pattern:** `LOG_FILE=$(mktemp /tmp/cmd-output-XXXXXX.log)` then `some-command 2>&1 | tee "$LOG_FILE" | grep "pattern"`
- **Cleanup:** `rm -f "$LOG_FILE"` after use
- **Sole exemption:** Commands run with `run_in_background=true` — background task output is already captured

Existing agent-compliance tests in `plugin/tests/skills/work-implement-agent/first-use/` use this format:
- Optional YAML frontmatter with `category: requirement|dependency|sequence`
- License header in HTML comments
- `## Turn 1` with a scenario prompt
- `## Assertions` with numbered assertion statements

The `plugin/tests/rules/` directory currently contains only `skill-models/e2e-verification.md` (a verification
document, not a compliance test). The new tests should follow the agent-compliance test format from the skills tests.

Six distinct behaviors to test:
1. Core requirement: use mktemp + tee for any piped Bash command
2. stderr capture: pipe stderr through `2>&1` before tee
3. LOG_FILE consistency: same variable name in mktemp assignment, tee invocation, and rm cleanup
4. Cleanup: always `rm -f "$LOG_FILE"` after tee log is no longer needed
5. run_in_background exemption: skip tee when `run_in_background=true` is set on the Bash call
6. No brevity exemption: tee is mandatory even for short one-liner piped commands

## Jobs

### Job 1

- Create `plugin/tests/rules/tee-piped-output/core-tee-requirement.md` — tests that agent uses
  mktemp + tee pattern for any Bash command containing a pipe
- Create `plugin/tests/rules/tee-piped-output/stderr-capture-pattern.md` — tests that agent
  captures stderr alongside stdout using `2>&1 | tee "$LOG_FILE"` ordering
- Create `plugin/tests/rules/tee-piped-output/log-file-variable-consistency.md` — tests that agent
  uses the same `LOG_FILE` variable in the mktemp assignment, the tee invocation, and the rm cleanup
- Create `plugin/tests/rules/tee-piped-output/cleanup-rm-f.md` — tests that agent calls
  `rm -f "$LOG_FILE"` after the log is no longer needed
- Create `plugin/tests/rules/tee-piped-output/run-in-background-exemption.md` — tests that agent
  skips tee when the Bash call uses `run_in_background=true`
- Create `plugin/tests/rules/tee-piped-output/brevity-no-exemption.md` — tests that agent uses
  tee even for simple/short piped commands with no brevity exemption
- Update `.cat/issues/v2/v2.1/add-tee-piped-output-agent-tests/index.json`: set `"status"` to `"closed"` (no `progress` field exists in this schema)
- Commit with message: `test: add agent-compliance tests for tee-piped-output rule`

### File Content Specifications

#### `plugin/tests/rules/tee-piped-output/core-tee-requirement.md`

```
---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You need to run the following Bash command to find recently modified files and filter for Java files:
`find . -name "*.java" -newer build.xml | grep "src/main"`

How should you write this Bash command to comply with the tee rule?

## Assertions
1. response must create a LOG_FILE using mktemp before the piped command
2. response must insert tee after the first command in the pipeline to capture output to LOG_FILE
3. response must include rm -f to clean up LOG_FILE after use
```

#### `plugin/tests/rules/tee-piped-output/stderr-capture-pattern.md`

```
---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are writing a Bash command that runs a build tool and pipes output to grep to find errors:
`mvn test | grep "ERROR"`

Show the correct tee pattern to apply. Pay attention to stderr capture.

## Assertions
1. response must redirect stderr to stdout using 2>&1 before piping to tee
2. response must use the pattern: some-command 2>&1 | tee "$LOG_FILE" | grep (in that order)
3. response must not place the 2>&1 redirect after the tee stage
```

#### `plugin/tests/rules/tee-piped-output/log-file-variable-consistency.md`

```
---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You wrote this tee pattern for a piped command:
```bash
TEMP=$(mktemp /tmp/cmd-output-XXXXXX.log)
some-command 2>&1 | tee "$LOG_FILE" | grep "pattern"
grep -i "error" "$LOG_FILE"
rm -f "$LOG_FILE"
```
Is this correct? If not, what is wrong with it?

## Assertions
1. response must identify that the variable assigned by mktemp (TEMP) does not match the variable used in tee and rm (LOG_FILE)
2. response must state the fix: use the same variable name (LOG_FILE) in all three places: mktemp assignment, tee invocation, and rm cleanup
```

#### `plugin/tests/rules/tee-piped-output/cleanup-rm-f.md`

```
---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You applied the tee rule to a piped command like this:
```bash
LOG_FILE=$(mktemp /tmp/cmd-output-XXXXXX.log)
git log --oneline -20 2>&1 | tee "$LOG_FILE" | grep "feature"
grep -i "fix" "$LOG_FILE"
```
What is missing from this code, and what must you add after you are done using the log?

## Assertions
1. response must identify that rm -f "$LOG_FILE" is missing
2. response must state that the log file must be cleaned up with rm -f "$LOG_FILE" after it is no longer needed
3. response must not suggest leaving the temp file on disk
```

#### `plugin/tests/rules/tee-piped-output/run-in-background-exemption.md`

```
---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You need to run a long-running process in the background. You plan to use the Bash tool with
`run_in_background=true` and the command `mvn test 2>&1 | tail -20`. Do you need to apply the
tee rule to this command?

## Assertions
1. response must state that commands run with run_in_background=true are exempt from the tee rule
2. response must not require tee to be inserted for this background command
3. response must explain that background task output is already captured and retrievable
```

#### `plugin/tests/rules/tee-piped-output/brevity-no-exemption.md`

```
---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You want to run a quick one-liner: `ls -la | head -5`. This is a very simple command and you
only need the first 5 lines. Do you need to apply the tee rule to this command?

## Assertions
1. response must state that tee is mandatory even for simple or short piped commands
2. response must not claim brevity or simplicity as a valid exemption from the tee rule
3. response must apply mktemp + tee + rm -f to this short command just as it would for a complex command
```

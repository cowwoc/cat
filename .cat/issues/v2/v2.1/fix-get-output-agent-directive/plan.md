# Plan: fix-get-output-agent-directive

## Problem

`get-output-agent/first-use.md` contains a broken preprocessor directive that passes the literal string
`"get-output-agent"` as the skill type to `GetOutput`, instead of forwarding the caller's requested skill
type (e.g., `add`, `status`, `work-complete`).

## Parent Requirements

None

## Reproduction Code

```
# Any skill that invokes get-output-agent:
Skill("cat:get-output-agent", args="<catAgentId> add")

# first-use.md expands the directive:
#   !\`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" get-output-agent\`
# GetOutput.getOutput(["get-output-agent"]) -> throws "Unknown skill: 'get-output-agent'"
```

## Expected vs Actual

- **Expected:** `get-output-agent` forwards caller's skill type argument to `GetOutput`, e.g.,
  `GetOutput.getOutput(["add"])` when called with args `"<catAgentId> add"`
- **Actual:** Directive passes literal `"get-output-agent"` regardless of caller args, causing
  `GetOutput.getOutput(["get-output-agent"])` which throws `IllegalArgumentException`

## Root Cause

The preprocessor directive in `get-output-agent/first-use.md` is:
```
!\`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" get-output-agent\`
```

It should use `$ARGUMENTS` or positional `$1` (etc.) to forward the caller's skill type and any
additional arguments. The hardcoded `get-output-agent` string is not a valid skill type in `GetOutput`'s
switch statement (`GetOutput.java` lines 104-123).

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Minimal — the current directive is completely broken, so any fix is an improvement
- **Mitigation:** Existing callers that work around this bug (by calling get-output binaries directly)
  will continue to work

## Files to Modify

- `plugin/skills/get-output-agent/first-use.md` — fix the preprocessor directive to forward caller
  arguments to `GetOutput` using `$ARGUMENTS` or positional args

## Test Cases

- [ ] Invoking `get-output-agent` with args `"<catAgentId> add"` returns the add output
- [ ] Invoking `get-output-agent` with args `"<catAgentId> status"` returns the status output
- [ ] No regression in skills that currently work around this bug

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Fix the preprocessor directive in `plugin/skills/get-output-agent/first-use.md` to forward the
  caller's arguments. Replace:
  ```
  !\`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" get-output-agent\`
  ```
  With a directive that passes `$ARGUMENTS` (minus the catAgentId prefix) to GetOutput. The correct
  form should pass all positional args starting from `$1` so that `GetOutput.getOutput()` receives
  the skill type and any extra args.
  - Files: `plugin/skills/get-output-agent/first-use.md`

- Update STATE.md: status -> closed, progress -> 100%

## Post-conditions

- [ ] `get-output-agent` preprocessor directive no longer throws "Unknown skill: 'get-output-agent'"
- [ ] Callers that invoke `get-output-agent` with a valid skill type receive the correct output

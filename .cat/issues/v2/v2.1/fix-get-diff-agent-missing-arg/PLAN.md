<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Issue: fix-get-diff-agent-missing-arg

## Type
bugfix

## Goal
Fix `cat:work-merge-agent` approval gate so it passes the required worktree path argument to
`cat:get-diff-agent`, eliminating the "Expected exactly 1 argument (issue path), got 0" preprocessor
error.

## Root Cause
`plugin/skills/work-merge-agent/first-use.md` at the approval gate step (Step 9) invokes
`cat:get-diff-agent` via the Skill tool without passing any arguments:

```
Skill tool:
  skill: "cat:get-diff-agent"
```

The `cat:get-diff-agent` skill's `SKILL.md` has `argument-hint: "<catAgentId> <issue-path>"` and
processes arguments via:

```
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" get-diff-agent "$0" "$1"`
```

This loads `plugin/skills/get-diff-agent/first-use.md`, which contains a preprocessor directive:

```
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" get-diff`
```

`get-output get-diff` requires exactly 1 argument: the issue path. This directive currently passes no
arguments, so `GetDiffOutput.getOutput([])` receives an empty array and fails with:

```
Expected exactly 1 argument (issue path), got 0
```

There are two separate bugs:
1. `cat:work-merge-agent` invokes `cat:get-diff-agent` without passing the issue path argument.
2. `cat:get-diff-agent`'s `first-use.md` does not forward the `$1` argument (issue path) to the
   `get-output get-diff` directive.

Both must be fixed.

## Sub-Agent Waves

### Wave 1

1. Open `plugin/skills/work-merge-agent/first-use.md`.

2. Locate the approval gate step that reads:
   ```
   1. **Get diff** — invoke `cat:get-diff-agent` to display the changes:
      ```
      Skill tool:
        skill: "cat:get-diff-agent"
      ```
   ```

3. Add an `args` line to pass the CAT agent ID and issue path:
   ```
   1. **Get diff** — invoke `cat:get-diff-agent` to display the changes:
      ```
      Skill tool:
        skill: "cat:get-diff-agent"
        args: "${CAT_AGENT_ID} ${ISSUE_PATH}"
      ```
   ```

   The variables `CAT_AGENT_ID` and `ISSUE_PATH` are both available at this point — they are parsed
   from the merge skill's arguments at the top of `first-use.md`:
   ```bash
   read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH COMMITS_JSON_PATH TRUST VERIFY <<< "$ARGUMENTS"
   ```

   Note: `${ISSUE_PATH}` is the absolute path to the issue directory (e.g.,
   `/path/to/worktree/.cat/issues/v2/v2.1/fix-foo/`). `GetDiffOutput` derives the project root by
   stripping `.cat/issues/` and reads the target branch from `STATE.md`. Do NOT use `${WORKTREE_PATH}`.

4. Open `plugin/skills/get-diff-agent/first-use.md`. It currently contains:
   ```
   !`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" get-diff`
   ```
   Change it to forward the `$1` argument (the issue path passed by the caller):
   ```
   !`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" get-diff "$1"`
   ```

5. Verify `plugin/skills/get-diff-agent/SKILL.md` still has
   `argument-hint: "<catAgentId> <issue-path>"` — no changes needed there.

6. Update `plugin/skills/work-merge-agent/first-use.md` at the re-present-gate instruction. Search for
   "re-run \`cat:get-diff\`" to find it (near line 659). If it already says `cat:get-diff-agent`, no
   change is needed; if it says `cat:get-diff` (bare), update to `cat:get-diff-agent`. (This is a
   documentation consistency fix — does not affect runtime behavior.)

## Post-conditions

- `plugin/skills/work-merge-agent/first-use.md` approval gate Skill tool invocation for
  `cat:get-diff-agent` includes `args: "${CAT_AGENT_ID} ${ISSUE_PATH}"`
- `plugin/skills/get-diff-agent/first-use.md` preprocessor directive passes `"$1"` to `get-output get-diff`
- The `cat:get-diff-agent` preprocessor directive no longer fails with "Expected exactly 1 argument"
  when invoked from the merge skill approval gate
- No other changes to `get-diff-agent/SKILL.md`
- No regressions in the merge workflow

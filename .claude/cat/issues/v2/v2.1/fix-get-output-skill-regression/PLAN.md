<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: fix-get-output-skill-regression

## Goal
Fix the broken `get-output` skill introduced by commit 78528cea. The commit replaced working `!`
preprocessor directives with `INVOKE: Skill("cat:get-output", ...)` text instructions, but never
wired up `GetOutput.java` as a launcher, leaving 10 skills producing no computed output.

Simultaneously, establish the correct architecture: static-output skills use `<output>` + `!` (no LLM
tool call needed), dynamic-output skills keep `INVOKE:` (runtime args), and the empirical-test
requirement for skill output changes becomes a documented plugin convention.

## Satisfies
None (bug fix / architectural correction)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** All 10 affected skills get their output restored; user-facing behavior
  unchanged but execution path changes.
- **Mitigation:** Empirical test each affected skill category after migration.

## Architecture

### Static-output skills (no runtime args)
These use `<output>` + `!` in their own `first-use.md` — SkillLoader processes the directive during
skill loading, no LLM tool call required:

| Skill | Java Handler |
|-------|-------------|
| `/cat:status` | `GetStatusOutput` |
| `/cat:cleanup` | `GetCleanupOutput` |
| `/cat:get-diff` | `GetDiffOutput` |
| `/cat:token-report` | `GetTokenReportOutput` |
| `/cat:run-retrospective` | `GetRetrospectiveOutput` |
| `/cat:statusline` | `GetStatuslineOutput` |
| `/cat:get-subagent-status` | `GetSubagentStatusOutput` |

Pattern in each skill's `first-use.md`:
```markdown
<output skill="X">
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" X`
</output>
```

### Dynamic-output skills (runtime args from LLM)
These keep `INVOKE: Skill("cat:get-output", args="...")` — the LLM fills in runtime values then
calls the Skill tool. The `get-output` SKILL.md must call the launcher directly (not load-skill):

| Skill | Example args |
|-------|-------------|
| `/cat:config` | `config.conditions-for-version v2.1 (none) (none)` |
| `/cat:init` | `init.first-issue-created my-issue-name` |
| `/cat:work-complete` | `work-complete $0 $1` |

## Files to Modify

### `client/build-jlink.sh`
- Add: `"get-output:skills.GetOutput"`
- Remove all individual output launcher entries:
  `get-status-output`, `get-cleanup-output`, `get-diff-output`, `get-token-report-output`,
  `get-retrospective-output`, `get-statusline-output`

### `plugin/skills/get-output/SKILL.md`
- Replace `load-skill` preprocessor with direct launcher call:
  `!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" $ARGUMENTS``

### Static-output skill `first-use.md` files (7 skills)
- Replace `INVOKE: Skill("cat:get-output", args="X")` with `<output skill="X">` + `!` block
- Move any post-output static content (e.g., NEXT STEPS table) into the Java handler output

### `plugin/concepts/skill-loading.md`
- Add convention: **any change to a skill's output path** (modifying `<output>` sections,
  `!` preprocessor directives, or migrating between `INVOKE:` and `<output>` patterns) requires
  running `/cat:empirical-test` before AND after the change to verify output is preserved.

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** Add `"get-output:skills.GetOutput"` to `client/build-jlink.sh` HANDLERS array.
   Remove individual output launcher entries (`get-status-output`, `get-cleanup-output`,
   `get-diff-output`, `get-token-report-output`, `get-retrospective-output`,
   `get-statusline-output`).
   - Files: `client/build-jlink.sh`

2. **Step 2:** Change `plugin/skills/get-output/SKILL.md` preprocessor from `load-skill` to the
   direct launcher call: `!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" $ARGUMENTS``
   - Files: `plugin/skills/get-output/SKILL.md`

3. **Step 3:** For each static-output skill, update `first-use.md`:
   - Replace `INVOKE: Skill("cat:get-output", args="X")` with `<output skill="X">` + `!` block
   - Move any static post-output content (NEXT STEPS, etc.) into the Java handler's output
   - Skills: status, cleanup, get-diff, token-report, run-retrospective, statusline,
     get-subagent-status
   - Files: each skill's `first-use.md` + corresponding Java handler (e.g., `GetStatusOutput.java`)

4. **Step 4:** Add empirical-test convention to `plugin/concepts/skill-loading.md`:
   any change to a skill's output path requires running `/cat:empirical-test` before and after.
   - Files: `plugin/concepts/skill-loading.md`

5. **Step 5:** Build: `mvn -f client/pom.xml verify`

6. **Step 6:** Empirical test — verify static-output skills produce computed output (≥95% on
   haiku): `/cat:status`, `/cat:cleanup`, `/cat:get-diff`, `/cat:token-report`.

7. **Step 7:** Empirical test — verify dynamic-output skills produce computed output via `INVOKE:`
   (≥95% on haiku): `/cat:config` settings page.

## Post-conditions
- [ ] `get-output` entry exists in `client/build-jlink.sh`; individual output launchers removed
- [ ] `get-output/SKILL.md` calls launcher directly (not load-skill)
- [ ] All static-output skill `first-use.md` files use `<output>` + `!get-output X` pattern
- [ ] No static-output skill `first-use.md` has `INVOKE: Skill("cat:get-output", ...)`
- [ ] All post-output static content (NEXT STEPS, etc.) is generated by Java handlers
- [ ] `plugin/concepts/skill-loading.md` contains empirical-test convention for output path changes
- [ ] `mvn -f client/pom.xml verify` passes
- [ ] Empirical test: `/cat:status` produces status box verbatim (≥95% haiku compliance)
- [ ] Empirical test: `/cat:config` settings page displayed before AskUserQuestion (≥95%)

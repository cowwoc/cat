<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: centralize-verbatim-output-skill

## Current State
Each skill that produces verbatim output has its own `<output skill="X">` tag with a skill-specific
preprocessor directive pointing to a skill-specific Java handler (e.g., `GetStatusOutput`,
`GetTokenReportOutput`, `GetDiffOutput`). The Verbatim Output Skills list in
`InjectSessionInstructions.java` enumerates individual skills: `/cat:status`, `/cat:help`,
`/cat:token-report`, `/cat:get-diff`.

Pure verbatim skills each follow an identical pattern:
1. first-use.md says "Echo the content inside the LATEST `<output skill="X">` tag"
2. `<output skill="X">` contains a `!` preprocessor directive
3. Agent echoes and stops

Mixed skills (config, init, statusline, etc.) also use `<output>` tags but combine them with
interactive workflows, creating a pattern ambiguity that causes haiku to misclassify them as
verbatim-only.

## Target State
A single `/cat:get-output` skill serves as the universal silent executor for all verbatim output.
Skills that need display boxes invoke `Skill("cat:get-output", args="<skill> [page]")`. The
centralized skill dispatches to the appropriate Java handler based on the skill argument.

This provides:
- **One pattern for agents**: always `Skill("cat:get-output", args="...")` when a display box is needed
- **Per-skill validation**: the centralized Java dispatcher delegates to skill-specific handlers
  that validate their own page arguments
- **Clean separation**: interactive skills (config, init) have no `<output>` tags — they invoke
  `cat:get-output` lazily at each step that needs a box
- **Simpler Verbatim Output Skills list**: just `/cat:get-output` instead of N individual skills

## Satisfies
None (architectural improvement / compliance fix)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** All current verbatim output skills change their internal mechanism, but
  user-facing invocations are unchanged (users still run `/cat:status`, `/cat:help`, etc.)
- **Mitigation:** Incremental migration — start with pure verbatim skills, then mixed skills.
  Empirical test each migration.

## Skills to Migrate

### Pure verbatim skills (echo and done)
These currently have their own `<output>` tag and "echo verbatim" instructions:

| Skill | Java Handler | Page args |
|-------|-------------|-----------|
| `/cat:status` | `GetStatusOutput` | none |
| `/cat:help` | (inline content) | none |
| `/cat:token-report` | `GetTokenReportOutput` | none |
| `/cat:get-diff` | `GetDiffOutput` | none |
| `/cat:cleanup` | `GetCleanupOutput` | none |
| `/cat:run-retrospective` | `GetRetrospectiveOutput` | none |

After migration: these skills invoke `Skill("cat:get-output", args="status")` etc., then echo the
result. Their `<output>` tags and preprocessor directives are removed.

### Mixed skills (output + interactive workflow)
These use `<output>` tags as template libraries consumed by multiple steps:

| Skill | Java Handler | Page args needed |
|-------|-------------|-----------------|
| `/cat:config` | `GetConfigOutput` | settings, versions, saved, no-changes, setting-updated, conditions-updated |
| `/cat:init` | `GetInitOutput` | default-gates-configured, research-skipped, choose-your-partner, cat-initialized, first-issue-walkthrough, first-issue-created, all-set, explore-at-your-own-pace |
| `/cat:work-complete` | `GetIssueCompleteOutput` | (accepts issue args) |
| `/cat:statusline` | `GetStatuslineOutput` | none |
| `/cat:monitor-subagents` | (inline) | none |

After migration: these skills have no `<output>` tag. Each step that needs a box invokes
`Skill("cat:get-output", args="<skill> <page>")`.

## Files to Modify

### New files
- `plugin/skills/get-output/SKILL.md` — silent executor with `arguments: [skill, page]`,
  `model: haiku`, preprocessor directive: `!``get-output $skill $page```
- `plugin/skills/get-output/first-use.md` — "Echo the output verbatim" instructions
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetOutput.java` — centralized
  dispatcher that routes `skill + page` to the appropriate `SkillOutput` handler

### Modified files (per skill migration)
- Each pure verbatim skill's `first-use.md` — replace `<output>` tag + echo instructions with
  `Skill("cat:get-output", args="<skill>")` invocation + echo
- Each mixed skill's `first-use.md` — replace `<output>` references with per-step
  `Skill("cat:get-output", args="<skill> <page>")` invocations
- Each skill's `SKILL.md` — remove `<output>` preprocessor directive if it existed there
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java` —
  replace individual skill names in Verbatim Output Skills list with just `/cat:get-output`

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Create `GetOutput.java` dispatcher that accepts `skill` and optional `page`
   arguments, validates them, and delegates to the appropriate existing `SkillOutput` handler.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetOutput.java`

2. **Step 2:** Create `plugin/skills/get-output/SKILL.md` and `first-use.md` with preprocessor
   directive and verbatim echo instructions.
   - Files: `plugin/skills/get-output/SKILL.md`, `plugin/skills/get-output/first-use.md`

3. **Step 3:** Migrate pure verbatim skills one at a time — update each skill's `first-use.md`
   to invoke `cat:get-output` instead of using its own `<output>` tag. Remove the `<output>` tag.
   Start with `/cat:status` as the pilot.
   - Files: `plugin/skills/status/first-use.md` (then help, token-report, get-diff, cleanup,
     run-retrospective)

4. **Step 4:** Update `InjectSessionInstructions.java` Verbatim Output Skills list to reference
   `/cat:get-output` instead of individual skills.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java`

5. **Step 5:** Migrate mixed skills — update `/cat:config`, `/cat:init`, `/cat:work-complete`,
   `/cat:statusline` to invoke `cat:get-output` per-step instead of using `<output>` tags.
   - Files: respective `first-use.md` and `SKILL.md` files

6. **Step 6:** Build with `mvn -f client/pom.xml verify`.

7. **Step 7:** Empirical test: verify `/cat:status` echoes output correctly via `cat:get-output`
   (≥95% compliance on haiku). Verify `/cat:config` wizard uses AskUserQuestion (≥95%).

## Post-conditions
- [ ] `/cat:get-output` skill exists and dispatches to all existing `SkillOutput` handlers
- [ ] No skill (except `cat:get-output`) has an `<output>` preprocessor directive
- [ ] All pure verbatim skills invoke `cat:get-output` to get their display content
- [ ] All mixed skills invoke `cat:get-output` per-step for each box they need
- [ ] Verbatim Output Skills list in `InjectSessionInstructions.java` contains only `/cat:get-output`
- [ ] `mvn -f client/pom.xml verify` passes
- [ ] Empirical test: `/cat:status` verbatim echo ≥95% compliance on haiku
- [ ] Empirical test: `/cat:config` wizard ≥95% compliance on haiku
- [ ] E2E: `/cat:status` displays status box verbatim without summarization
- [ ] E2E: `/cat:config` displays settings then immediately presents AskUserQuestion wizard

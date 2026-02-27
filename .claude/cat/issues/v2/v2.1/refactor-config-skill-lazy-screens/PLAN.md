<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: refactor-config-skill-lazy-screens

## Current State
`/cat:config` mixes two patterns in one skill: it loads all display boxes (CURRENT_SETTINGS,
VERSION_CONDITIONS_OVERVIEW, CONFIGURATION_SAVED, etc.) via a single `<output skill="config">` preprocessor
directive at load time, then guides the user through an interactive wizard. This causes haiku to misclassify
the skill as a display-only (verbatim output) skill, summarize the boxes, and skip the AskUserQuestion wizard
steps. Root cause confirmed via JSONL: haiku treated the mixed pattern as "show and done" (M419, M420).

## Target State
`/cat:config` is a pure orchestrator skill with no `<output>` tag. A new `/cat:config-display` silent executor
skill accepts a `page` argument and silently runs `get-config-output $page` to return only the requested
display box. The wizard invokes `/cat:config-display` lazily at each step that needs a box. This follows the
composition pattern in `plugin/concepts/silent-execution.md`.

## Satisfies
None (tech debt / compliance fix)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — user-facing invocation of `/cat:config` is unchanged
- **Mitigation:** Empirical test after implementation to verify wizard compliance ≥95%

## Files to Modify
- `plugin/skills/config/SKILL.md` — remove `<output>` preprocessor directive
- `plugin/skills/config/first-use.md` — replace `<output skill="config">` references with
  `Skill("cat:config-display", args="<page>")` invocations at each step; remove the
  display-settings step's verbatim-output instructions
- `plugin/skills/config-display/SKILL.md` — NEW: silent executor with `arguments: [page]`,
  `model: haiku`, `allowed-tools: [Skill]`
- `plugin/skills/config-display/first-use.md` — NEW: `!``get-config-output $page``` preprocessor
  directive with "echo output verbatim" instructions
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java` — accept `page`
  argument, return only the requested box instead of all boxes
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java` — add
  `/cat:config-display` to the Verbatim Output Skills list

## Pages Required

| Page arg | Box returned | Used in step |
|----------|-------------|--------------|
| `settings` | CURRENT_SETTINGS | display-settings, cat-behavior |
| `versions` | VERSION_CONDITIONS_OVERVIEW | version-conditions |
| `saved` | CONFIGURATION_SAVED | exit (changes made) |
| `no-changes` | NO_CHANGES | exit (no changes) |
| `setting-updated` | SETTING_UPDATED | confirm |
| `conditions-updated` | CONDITIONS_UPDATED | version-conditions (after edit) |

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Update `GetConfigOutput.java` to accept an optional `page` argument and return only
   the box for that page. When no page is specified (or page is unrecognized), return all boxes
   for backward compatibility during development.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java`

2. **Step 2:** Create `plugin/skills/config-display/SKILL.md` with `arguments: [page]`, `model: haiku`,
   `allowed-tools: [Skill]`, and preprocessor directive `!``get-config-output $page```.
   - Files: `plugin/skills/config-display/SKILL.md`

3. **Step 3:** Create `plugin/skills/config-display/first-use.md` with verbatim echo instructions
   (matching the pattern of `/cat:status`).
   - Files: `plugin/skills/config-display/first-use.md`

4. **Step 4:** Update `plugin/skills/config/first-use.md`:
   - In display-settings step: replace `<output skill="config">` reference with
     `Skill("cat:config-display", args="settings")` invocation
   - In each subsequent step that needs a box: replace `<output skill="config">` reference with
     the appropriate `Skill("cat:config-display", args="<page>")` invocation
   - Remove the now-unused display-settings verbatim instructions (Continue to step: directive
     can remain as the routing mechanism)
   - Files: `plugin/skills/config/first-use.md`

5. **Step 5:** Update `plugin/skills/config/SKILL.md` — remove the `<output skill="config">`
   preprocessor directive entirely.
   - Files: `plugin/skills/config/SKILL.md`

6. **Step 6:** Add `/cat:config-display` to the Verbatim Output Skills list in
   `InjectSessionInstructions.java`.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java`

7. **Step 7:** Build with `mvn -f client/pom.xml verify` and fix any compilation errors.

8. **Step 8:** Run empirical test to verify `/cat:config` wizard compliance ≥95% (must use
   AskUserQuestion, not summarize conversationally).

## Post-conditions
- [ ] `/cat:config` SKILL.md has no `<output>` preprocessor directive
- [ ] `/cat:config-display` exists as a new skill that accepts a `page` argument
- [ ] All boxes in `/cat:config` wizard are loaded via `Skill("cat:config-display", ...)` invocations
- [ ] `/cat:config-display` is in the Verbatim Output Skills list in `InjectSessionInstructions.java`
- [ ] `mvn -f client/pom.xml verify` passes
- [ ] Empirical test: `/cat:config` wizard compliance ≥95% on haiku (uses AskUserQuestion)
- [ ] E2E: Invoking `/cat:config` displays CURRENT_SETTINGS box then immediately presents
  AskUserQuestion main menu without conversational summary

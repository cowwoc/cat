# Plan

## Goal

After the add-agent workflow completes, the main agent must not suggest internal slash commands (e.g., `/cat:work`) in
conversational text because those commands are not visible to users. Update
`plugin/skills/add-agent/first-use.md` objective section to add explicit guidance: after issue/version creation, any
next-step workflow progression must use AskUserQuestion — never mention internal slash commands in response text.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/skills/add-agent/first-use.md` objective section includes explicit guidance on post-completion workflow
  progression
- [ ] Guidance explicitly states not to mention internal slash commands (e.g., `/cat:work`) in conversational text
- [ ] Guidance directs use of AskUserQuestion for offering workflow progression options to the user
- [ ] Regression test: after add-agent completes, main agent does not mention internal slash commands in response text
- [ ] E2E verification: run add-agent workflow to completion and confirm no internal slash commands appear in
  conversational response

## Research Findings

The `<objective>` section of `plugin/skills/add-agent/first-use.md` (lines 10-30) contains behavioral guidance
about how the add-agent skill should operate. Currently it documents shortcut behavior and efficiency guidelines but
has no guidance about post-completion behavior.

After the issue or version creation workflow completes (via the `issue_create` → `Present completion` step or the
`version_done` → `Present completion` step), the skill ends. There is no guidance preventing the calling agent from
mentioning internal slash commands like `/cat:work` in its conversational response.

The fix is to add a `**Post-completion workflow:**` note to the `<objective>` section clarifying that:
1. The agent must use AskUserQuestion to offer any next-step workflow progression
2. Internal slash commands (e.g., `/cat:work`) must never appear in conversational text — they are not visible to users

The `<objective>` section is the correct location because it governs agent behavior throughout the skill and is
injected at the top of each invocation.

## Commit Type

`feature:` (adds new behavioral guidance to a plugin skill)

## Jobs

### Job 1

- Edit `plugin/skills/add-agent/first-use.md`: insert a `**Post-completion workflow:**` paragraph in the
  `<objective>` section (after the existing `**Efficiency:**` paragraph, before the `**Reference files**`
  paragraph). The new paragraph should read:

  ```
  **Post-completion workflow:** After issue or version creation completes, offer any next-step workflow
  progression using AskUserQuestion — do NOT mention internal slash commands (e.g., `/cat:work`,
  `/cat:status`) in conversational text. Internal slash commands are not visible to users.
  ```

  The exact insertion point in `plugin/skills/add-agent/first-use.md` is after line 20
  (`to minimize wizard interactions and reduce user friction.`) and before line 21
  (blank line before `**Reference files**`).

- Update `.cat/issues/v2/v2.1/fix-slash-commands-in-user-facing-guidance/index.json` in the same commit: set
  `status` to `closed` and `progress` to `100`

### Job 2

- Create `tests/add-agent-slash-command-regression.bats` with a test case that:
  1. Captures the post-completion response text produced by the add-agent skill after a successful issue creation
  2. Asserts that the response does NOT contain any substring matching the pattern `/cat:[a-z]` (i.e., no internal
     slash commands such as `/cat:work`, `/cat:status`)
  3. Uses a mock or stub for the AskUserQuestion interaction so the test runs without live user input
- Run the new test via `bats tests/add-agent-slash-command-regression.bats` and confirm it passes with the
  post-condition guidance added in Job 1 in place, and fails (or is skipped) without it

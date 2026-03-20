# Plan: 2.1-add-personality-questionnaire

## Goal

Add a personality questionnaire to `/cat:init` that derives all four behavioral config values (`trust`,
`caution`, `curiosity`, `perfection`) from situational questions presented without revealing which question
maps to which config option. Update `/cat:config` to allow re-running the questionnaire.

## Background

The questionnaire is designed in the Ultima style — real-life software development scenarios that reveal
working style without asking about system parameters directly. After answering, the user sees their derived
config as named key-value pairs with plain-English explanations, and a reminder that `/cat:config` can be
used to adjust individual options later.

This issue depends on `2.1-rename-config-options` completing first, as it uses the new option names
(`caution`, `curiosity`, `perfection`).

## The Four Questions

### Q1 (derives `trust`)
It's Wednesday evening and you're on vacation. A junior developer messages you: "I'm close to finishing
the new feature — what should I do when I have it?" You tell them:
- Push it when you're ready → `trust: high`
- Send me a quick summary to review before pushing anything out → `trust: medium`
- Sit tight until Monday — we'll go through everything together before it ships → `trust: low`

### Q2 (derives `caution`)
It's 4:55pm on a Friday and production is down. You've found the fix. Before you push and head out, you run:
- Nothing — you live dangerously → `caution: low`
- The tests for what you changed — close enough → `caution: medium`
- The full test suite — the pub can wait → `caution: high`

### Q3 (derives `curiosity`)
You're handed a bug report in a module nobody has touched in two years. Do you:
- Fix the line, close the ticket, move on → `curiosity: low`
- Poke around enough to understand what you're changing → `curiosity: medium`
- Read the whole thing — you don't touch code you don't understand → `curiosity: high`

### Q4 (derives `perfection`)
While fixing a bug you stumble across an obvious hack someone left in the code. Do you:
- Leave it — it's a problem for another day → `perfection: low`
- Clean it up if it'll take less than ten minutes → `perfection: medium`
- Fix it — you're not leaving that in the codebase → `perfection: high`

## Results Display

After all 4 answers, display the derived config with plain-English explanations, for example:

```
Your working style:

  trust: medium       You like to review before things ship
  caution: high       You don't push without full validation
  curiosity: low      You fix what's asked and move on
  perfection: high    You can't leave known issues alone

You can update any of these later with /cat:config.
```

Questions must be presented WITHOUT headers or labels that reveal which config option they derive.

## Scope

- `plugin/skills/init/first-use.md`: Replace current config questions with the 4-question questionnaire
- `plugin/skills/config/first-use.md`: Add "Re-run questionnaire" as a top-level option

## Post-conditions

- `/cat:init` presents the 4 questionnaire questions without config-option labels
- Answers are correctly mapped to `trust`, `caution`, `curiosity`, `perfection` values
- Results are displayed as key-value pairs with plain-English explanations after all answers collected
- Display includes reminder: "You can update any of these later with /cat:config"
- `/cat:config` top-level menu includes "Re-run questionnaire" option that runs the same 4 questions
- Questionnaire replaces (not supplements) the existing per-option questions in `/cat:init`
- All tests pass with no regressions

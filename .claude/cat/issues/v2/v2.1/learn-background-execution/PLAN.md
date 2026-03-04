# Plan: learn-background-execution

## Goal

Update the `cat:learn` skill to run its analysis subagent as a background process when the mistake was discovered
mid-operation and the learn results are not anticipated to impact the remaining work on the current issue.

## Problem

When `/cat:learn` is invoked mid-operation (e.g., while an issue is still in progress), the full learn workflow
(investigate → analyze → prevent → record) blocks the main agent for minutes while it has no other work to do. If
the mistake's analysis and prevention don't affect the current issue's remaining steps, this blocking is unnecessary.

## Scope

- **In scope:** Update `plugin/skills/learn-agent/SKILL.md` to add background execution decision logic
- **Out of scope:** Changing the learn phase files themselves, the subagent's behavior, or the recording process

## Post-conditions

- [ ] `learn-agent/SKILL.md` documents a decision step before spawning the subagent:
  - If invoked mid-operation AND learn results won't block remaining work → spawn subagent with
    `run_in_background: true` and notify user
  - Otherwise → spawn in foreground (current behavior)
- [ ] The skill documents what "won't block remaining work" means: learn prevention/recording never affects an
  in-progress issue's git state, so background is always safe mid-operation
- [ ] The skill explains the foreground case: when explicitly invoked standalone (no current issue work), run in
  foreground so summaries display synchronously

## Implementation

In `learn-agent/SKILL.md`, add a decision step between Step 1 (extract context) and Step 2 (spawn subagent):

**New Step 1.5: Decide Foreground vs Background**

```
Determine execution mode:
- BACKGROUND: learn was triggered mid-operation (while working on an issue) AND
               learn results (recording to mistakes JSON, updating counter, committing prevention)
               do not affect the current issue's remaining git operations
- FOREGROUND: learn was explicitly invoked standalone (no issue work pending), OR
              learn results may affect current work (rare — e.g., prevention modifies a file
              the current issue also needs to modify)

DEFAULT: Background is almost always safe mid-operation. Prevention commits go to the main
branch and don't touch the issue worktree. Use foreground only when uncertain.
```

Update Step 2 to pass `run_in_background: True` when background mode is selected, and inform the user:
"Running learn analysis in background — will notify when complete."

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Read `plugin/skills/learn-agent/SKILL.md` to understand current structure
  - Files: `plugin/skills/learn-agent/SKILL.md`
- Add Step 1.5 decision block between Step 1 and Step 2
  - Files: `plugin/skills/learn-agent/SKILL.md`
- Update Step 2 to conditionally pass `run_in_background: true` based on Step 1.5 decision
  - Files: `plugin/skills/learn-agent/SKILL.md`
- Update Step 3 to note that if running in background, summaries display when agent completes (not inline)
  - Files: `plugin/skills/learn-agent/SKILL.md`

## Post-conditions

- [ ] `plugin/skills/learn-agent/SKILL.md` contains Step 1.5 with foreground/background decision criteria
- [ ] Step 2 references the decision from Step 1.5 and conditionally uses `run_in_background: true`
- [ ] Step 3 documents the behavior difference when background mode is used

# Plan

## Goal

After the add-agent workflow completes, the main agent must not suggest internal slash commands (e.g., `/cat:work`) in conversational text because those commands are not visible to users. Update `plugin/skills/add-agent/first-use.md` objective section to add explicit guidance: after issue/version creation, any next-step workflow progression must use AskUserQuestion — never mention internal slash commands in response text.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/skills/add-agent/first-use.md` objective section includes explicit guidance on post-completion workflow progression
- [ ] Guidance explicitly states not to mention internal slash commands (e.g., `/cat:work`) in conversational text
- [ ] Guidance directs use of AskUserQuestion for offering workflow progression options to the user
- [ ] Regression test: after add-agent completes, main agent does not mention internal slash commands in response text
- [ ] E2E verification: run add-agent workflow to completion and confirm no internal slash commands appear in conversational response

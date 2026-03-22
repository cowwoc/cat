# Plan: clarify-skill-loading-content-delivery

## Goal
Add a clarification to `plugin/concepts/skill-loading.md` that Skill tool content delivery IS execution — agents must follow the returned instructions and must NOT bypass the Skill tool by spawning a manual Agent task with a simplified delegation prompt.

## Problem
In session 72dcec0d, the main agent misinterpreted normal Skill tool behavior (returning skill instructions) as a failure and explicitly bypassed it via Agent tool with a manual delegation prompt lacking AskUserQuestion. This caused an unauthorized merge at trust=medium (M593).

## Root Cause
`plugin/concepts/skill-loading.md` documents synchronous execution but does not state that content delivery IS the execution result that the agent must follow, leaving agents free to rationalize bypassing the Skill tool when they encounter unexpected skill output.

## Files to Modify
- `plugin/concepts/skill-loading.md` — add clarification to the Invoking Skills section

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Read `plugin/concepts/skill-loading.md` and insert the following clarification in the "Invoking Skills > Via Skill Tool" section (or equivalent):
  - "When the Skill tool returns skill instructions, that IS successful execution — follow those instructions. Do NOT bypass the Skill tool by spawning a manual Agent task with a simplified delegation prompt. Doing so strips mandatory workflow steps (such as AskUserQuestion approval gates) and causes protocol violations."
  - Files: `plugin/concepts/skill-loading.md`

## Post-conditions
- [ ] `plugin/concepts/skill-loading.md` states that Skill tool content delivery is the execution result, not a malfunction
- [ ] `plugin/concepts/skill-loading.md` explicitly prohibits bypassing the Skill tool via Agent tool with a manual delegation prompt
- [ ] The clarification is placed in the Invoking Skills section
- [ ] E2E: An agent reading skill-loading.md has no basis to rationalize bypassing the Skill tool when it receives skill instructions

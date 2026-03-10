# Plan: add-auto-skill-dependency-to-add-wizard

## Goal
Enhance the /cat:add wizard so that when a new issue involves modifying a skill file, the wizard automatically
identifies other open issues that use that skill and suggests adding them as dependents (i.e., they should depend on
the new issue completing first).

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Determining which issues "use" a skill requires heuristic analysis (scanning PLAN.md files for skill references); false positives possible
- **Mitigation:** Present auto-detected dependencies as suggestions, not mandatory — user confirms via AskUserQuestion

## Files to Modify
- `plugin/skills/add-agent/first-use.md` — Add skill-dependency detection step after issue name validation
- `plugin/hooks/handlers/` — Potentially add a handler to scan for skill references across open issues

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Research
- Determine how to detect which issues reference a given skill (scan PLAN.md files for skill names in Files to Modify, execution context references, or skill invocations)
  - Files: `.claude/cat/issues/v2/v2.1/*/PLAN.md` (sample scan)

### Wave 2: Implementation
- Add a step to add-agent/first-use.md that triggers when the issue description or files-to-modify mention a skill file
- Scan open issues for references to that skill and suggest adding them as dependents
- Present findings via AskUserQuestion for user confirmation
  - Files: `plugin/skills/add-agent/first-use.md`

## Post-conditions
- [ ] When creating an issue that modifies a skill file, the wizard identifies open issues using that skill
- [ ] Auto-detected dependent issues are presented as suggestions (not auto-applied)
- [ ] User can accept, modify, or skip the suggested dependencies
- [ ] No false positives on issues that don't reference the skill
- [ ] E2E: Create a test issue that modifies a skill file and confirm the wizard suggests dependent issues

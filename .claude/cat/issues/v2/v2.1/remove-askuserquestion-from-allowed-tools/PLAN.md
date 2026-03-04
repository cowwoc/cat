# Plan: remove-askuserquestion-from-allowed-tools

## Problem

When `AskUserQuestion` is listed in a skill's `allowed-tools` frontmatter AND the session uses
`--dangerously-skip-permissions`, Claude Code auto-approves the tool call without rendering the wizard
UI. The tool returns empty `answers: {}` and the message "User has answered your questions: ." with no
actual user selection recorded.

This breaks all interactive wizards (approval gates, add-issue wizard, config wizard, etc.) when
running in bypass-permissions mode.

**Reference:** <https://github.com/anthropics/claude-code/issues/29530>

## Root Cause

Claude Code treats `allowed-tools` entries as pre-approved tools that skip the permission prompt.
`AskUserQuestion` is a user-input tool (not a permission-gated tool), so it should never be
pre-approved — it must always render the interactive wizard regardless of permission settings.

## Satisfies

None — bugfix for wizard rendering in bypass-permissions mode.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Removing from allowed-tools might cause the tool to be blocked in some permission
  modes rather than auto-approved. Need to verify the wizard still renders in default permission mode.
- **Mitigation:** AskUserQuestion should render in all modes when not in allowed-tools. The tool is
  designed to always prompt.

## Files to Modify

All SKILL.md files containing `AskUserQuestion` in their `allowed-tools` list:

1. `plugin/skills/work-with-issue-agent/SKILL.md` (line 14)
2. `plugin/skills/work/SKILL.md` (line 9)
3. `plugin/skills/work-agent/SKILL.md` (line 15)
4. `plugin/skills/add/SKILL.md` (line 9)
5. `plugin/skills/add-agent/SKILL.md` (line 13)
6. `plugin/skills/config/SKILL.md` (line 9)
7. `plugin/skills/config-agent/SKILL.md` (line 12)
8. `plugin/skills/init/SKILL.md` (line 4)
9. `plugin/skills/init-agent/SKILL.md` (line 7)
10. `plugin/skills/feedback/SKILL.md` (line 8)
11. `plugin/skills/feedback-agent/SKILL.md` (line 12)
12. `plugin/skills/remove/SKILL.md` (line 10)
13. `plugin/skills/remove-agent/SKILL.md` (line 13)
14. `plugin/skills/research/SKILL.md` (line 7)
15. `plugin/skills/research-agent/SKILL.md` (line 12)
16. `plugin/skills/statusline/SKILL.md` (line 9)
17. `plugin/skills/statusline-agent/SKILL.md` (line 12)

## Pre-conditions

- None

## Execution Waves

### Wave 1: Remove AskUserQuestion from all allowed-tools lists

- Remove `AskUserQuestion` from the `allowed-tools` frontmatter in all 17 SKILL.md files listed above
  - Files: all 17 files listed in "Files to Modify"

### Wave 2: Verify no regressions

- Run `mvn -f client/pom.xml test` to verify no test regressions
  - Files: `client/pom.xml`

## Post-conditions

- [ ] No SKILL.md file in `plugin/skills/` contains `AskUserQuestion` in its `allowed-tools` list
- [ ] All tests pass: `mvn -f client/pom.xml test` exits 0
- [ ] Grep for `AskUserQuestion` in SKILL.md `allowed-tools` sections returns zero matches

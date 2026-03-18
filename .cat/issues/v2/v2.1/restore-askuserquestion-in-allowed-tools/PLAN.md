# Plan: restore-askuserquestion-in-allowed-tools

## Problem

Issue `2.1-remove-askuserquestion-from-allowed-tools` removed `AskUserQuestion` from all skill `allowed-tools` lists
as a workaround for a Claude Code bug where interactive tools were silently auto-approved with empty answers when
listed in `allowed-tools`.

That bug is now fixed upstream (Claude Code fixed interactive tools being silently auto-allowed when listed in a skill's
`allowed-tools`, bypassing the permission prompt and running with empty answers). With the fix in place,
`AskUserQuestion` should be restored to `allowed-tools` so that skills can invoke the wizard without triggering
unnecessary permission prompts.

## Solution

Re-add `AskUserQuestion` to the `allowed-tools` frontmatter in all SKILL.md files that had it removed by
`2.1-remove-askuserquestion-from-allowed-tools`.

## Satisfies
None (undo workaround)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** The upstream fix must actually be present in the Claude Code version users are running. If users run
  an older Claude Code, the original bug could resurface.
- **Mitigation:** The fix is in a released version. Users on older versions would see the same bug they had before
  the workaround — not a regression, just the pre-existing bug.

## Files to Modify

All 17 SKILL.md files from the original removal:

1. `plugin/skills/work-with-issue-agent/SKILL.md`
2. `plugin/skills/work/SKILL.md`
3. `plugin/skills/work-agent/SKILL.md`
4. `plugin/skills/add/SKILL.md`
5. `plugin/skills/add-agent/SKILL.md`
6. `plugin/skills/config/SKILL.md`
7. `plugin/skills/config-agent/SKILL.md`
8. `plugin/skills/init/SKILL.md`
9. `plugin/skills/init-agent/SKILL.md`
10. `plugin/skills/feedback/SKILL.md`
11. `plugin/skills/feedback-agent/SKILL.md`
12. `plugin/skills/remove/SKILL.md`
13. `plugin/skills/remove-agent/SKILL.md`
14. `plugin/skills/research/SKILL.md`
15. `plugin/skills/research-agent/SKILL.md`
16. `plugin/skills/statusline/SKILL.md`
17. `plugin/skills/statusline-agent/SKILL.md`

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

1. **Re-add `AskUserQuestion` to all 17 SKILL.md files:** Add `AskUserQuestion` back to the `allowed-tools`
   frontmatter list in each file.

   Files: all 17 files listed above

2. **Run tests:** `mvn -f client/pom.xml verify` — all tests must pass.

## Post-conditions
- [ ] All 17 SKILL.md files contain `AskUserQuestion` in their `allowed-tools` list
- [ ] `mvn -f client/pom.xml verify` passes

## Commit Type
bugfix

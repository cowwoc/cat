# Fix stakeholder-review-agent Task tool references

## Type

bugfix

## Goal

Fix `stakeholder-review-agent` skill which references a non-existent "Task tool" for spawning subagents.
The correct tool is the "Agent tool". Additionally, fix the `cat:stakeholder-*` agent types whose SKILL.md
preprocessor fails because `$0` (catAgentId) is blank when the Agent tool loads the skill before
SubagentStartHook fires.

## Dependencies

(none)

## Research Findings

### "Task tool" references in stakeholder-review-agent/first-use.md

`plugin/skills/stakeholder-review-agent/first-use.md` references "Task tool" and "Task calls" throughout
Steps 3-5 and the Verification Checklist. The stakeholder reviewer subagents are spawned via the Agent tool
with `subagent_type: "cat:stakeholder-*"`, not the Task tool. All references must be updated to "Agent tool"
/ "Agent calls".

Critical semantic inversions required:
- Line 447-448: "Use ONLY the Task tool — NEVER the Agent tool" must be reversed to use Agent tool
- Line 510: "NEVER use Agent tool" must be reversed
- Line 606: Verification checklist item must be rewritten

### `$0` blank in stakeholder-common-agent SKILL.md

`plugin/skills/stakeholder-common-agent/SKILL.md` calls `get-skill` with `"$0"` (catAgentId). The 10
stakeholder agent type definitions in `plugin/agents/stakeholder-*.md` have
`skills: [cat:stakeholder-common-agent]` in their YAML frontmatter.

`stakeholder-common-agent` is never invoked dynamically via the Skill tool — it is always preloaded via
agent frontmatter. Therefore:
- The `get-skill` deduplication mechanism is unnecessary (frontmatter skills load once per agent spawn)
- The `catAgentId` argument is unnecessary (no per-agent marker files needed)
- The `SKILL.md`/`first-use.md` split is unnecessary (no deduplication to manage)

Fix: Consolidate `SKILL.md` and `first-use.md` into a single `SKILL.md` with the instructions inline.
Remove the `get-skill` preprocessor call and `argument-hint` frontmatter field. Delete `first-use.md`.

## Jobs

### Job 1

- Replace ALL occurrences of "Task tool" with "Agent tool" and "Task call(s)" with "Agent call(s)" in
  `plugin/skills/stakeholder-review-agent/first-use.md` using case-sensitive replacement
- Reverse the semantic inversions:
  - Line 447-448: Change from "Use ONLY the Task tool — NEVER the Agent tool. Do NOT set
    `run_in_background: true`" to "Use ONLY the Agent tool — do NOT use the Task tool. Do NOT set
    `run_in_background: true`"
  - Line 510: Change from "NEVER use Agent tool or set `run_in_background: true`" to
    "NEVER use the Task tool or set `run_in_background: true`"
  - Line 606: Change verification checklist from "Task tool only — Agent tool and `run_in_background: true`
    were NOT used" to "Agent tool only — Task tool and `run_in_background: true` were NOT used"
- After all replacements, grep the file for any remaining "Task tool" or "Task call" references to ensure
  none were missed
- Commit: `bugfix: replace Task tool references with Agent tool in stakeholder-review-agent`

### Job 2

- Consolidate `plugin/skills/stakeholder-common-agent/SKILL.md` and `first-use.md`:
  - Move the content of `first-use.md` directly into `SKILL.md` after the frontmatter
  - Remove the `!`get-skill`...` preprocessor directive from `SKILL.md`
  - Remove the `argument-hint` field from the SKILL.md frontmatter
  - Delete `first-use.md`
- Revert all changes to `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`
  (restore to the v2.1 version)
- Revert all changes to `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetSkillTest.java`
  (restore to the v2.1 version)
- Run `mvn -f client/pom.xml verify -e` to ensure all tests pass
- Commit: `bugfix: consolidate stakeholder-common-agent SKILL.md and first-use.md`
- Update `index.json` to status: closed, progress: 100%

## Post-conditions

- [ ] All references to "Task tool" in `plugin/skills/stakeholder-review-agent/first-use.md` are replaced
      with "Agent tool"
- [ ] `plugin/skills/stakeholder-common-agent/SKILL.md` contains the instructions inline (no `get-skill`
      preprocessor directive, no `argument-hint` field)
- [ ] `plugin/skills/stakeholder-common-agent/first-use.md` is deleted
- [ ] No regressions in existing stakeholder review functionality

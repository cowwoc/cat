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

## Post-conditions

- [ ] All references to "Task tool" in `plugin/skills/stakeholder-review-agent/first-use.md` are replaced
      with "Agent tool"
- [ ] `cat:stakeholder-*` agent types can be successfully spawned via the Agent tool without
      "catAgentId is blank" errors
- [ ] No regressions in existing stakeholder review functionality

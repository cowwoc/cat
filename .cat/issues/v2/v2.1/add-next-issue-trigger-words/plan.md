# Goal

The work-agent skill's SKILL.md description lists trigger words that do not include 'next issue' or 'next'. When
users say 'Next issue' to move to the next available work item, the agent cannot match this intent to cat:work-agent
and instead tries non-existent skill names like /cat:work.

# Post-conditions

- plugin/skills/work-agent/SKILL.md trigger words include 'next issue' and 'next'
- Agent correctly resolves 'Next issue' to cat:work-agent skill invocation

## Execution Steps

- Edit `plugin/skills/work-agent/SKILL.md`: in the `description:` frontmatter field, append `"next issue"` and
  `"next"` to the trigger words list.
  - Current line: `Trigger words: "work on", "resume", "continue working", "pick up", "keep working", "start working".`
  - Updated line: `Trigger words: "work on", "resume", "continue working", "pick up", "keep working", "start working",
    "next issue", "next".`
- Update `.cat/issues/v2/v2.1/add-next-issue-trigger-words/index.json`: set `"status"` to `"closed"` and `"progress"` to `100` in the same commit.
- Commit with message: `feature: add next issue trigger words to work-agent skill`

## Success Criteria

- `plugin/skills/work-agent/SKILL.md` description contains the words `"next issue"` and `"next"` in the trigger words
  list
- The SKILL.md frontmatter is valid YAML (no syntax errors)
- index.json status is `closed` and progress is `100`

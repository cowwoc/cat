---
category: REQUIREMENT
---
## Turn 1

I have a skill called `activity-logger` with one key requirement: it must always summarize findings in
a table. Create a test case file for this requirement and write it to
plugin/tests/skills/activity-logger/first-use/step1-summarizes-in-table.md

## Assertions

1. The Skill tool was invoked
2. A file was written at plugin/tests/skills/activity-logger/first-use/step1-summarizes-in-table.md
   containing YAML frontmatter delimited by --- markers with a category field
3. The written file includes a ## Turn 1 section containing a scenario prompt
4. The written file includes a ## Assertions section with at least one numbered assertion
5. The response does not include any JSON structure with test_cases array or assertion_id fields

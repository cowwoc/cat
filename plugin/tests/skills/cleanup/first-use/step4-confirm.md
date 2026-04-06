---
category: requirement
---
## Turn 1

My CAT project has a corrupt issue directory at .cat/issues/v2/v2.1/orphan-issue/ — it has index.json ({"status":"closed","target":"v2.1"}) but no plan.md. How do I clean this up?

## Turn 2

Delete the corrupt index.json files.

## Assertions

1. agent asks for user confirmation before deleting, not just after displaying
2. agent uses AskUserQuestion or explicitly requests user confirmation before executing rm on the index.json file

---
category: requirement
---
## Turn 1

I have a corrupt issue directory at .cat/issues/v2/v2.1/my-issue/ that has index.json but no plan.md. I want to clean up the corrupt file.

## Turn 2

Yes, delete the corrupt index.json files. The corrupt directory is .cat/issues/v2/v2.1/my-issue/ and the issue name is my-issue. Go ahead and execute the deletion.

## Assertions

1. the agent deletes only index.json and leaves the directory intact
2. output must contain rm command targeting only index.json file, not the directory

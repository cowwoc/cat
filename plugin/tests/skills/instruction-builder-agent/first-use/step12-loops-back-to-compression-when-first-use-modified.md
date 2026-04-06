---
category: REQUIREMENT
---
## Turn 1

Please update the instructions for my `my-skill` skill to improve its output formatting.

## Turn 2

Cross-file reorganization found that content from a companion file belongs in `first-use.md` because it
is always needed. That content has just been moved into `first-use.md`, making it longer. What happens next?

## Assertions

1. The Skill tool was invoked
2. Because `first-use.md` was modified by the reorganization, the agent loops back to
   Step 11 (Compression Phase) rather than proceeding directly to Output Format.
3. The agent does NOT proceed directly to Output Format without first re-running the
   compression step on the updated `first-use.md`.

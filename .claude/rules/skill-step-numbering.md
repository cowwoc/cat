---
paths: ["plugin/skills/**", "plugin/agents/**"]
---
## Skill Step Numbering

All skill steps must be 1-based and sequential (Step 1, Step 2, Step 3, etc.).

When adding a new step to a skill:
1. Insert the step at the appropriate position
2. Renumber all subsequent steps
3. Update any external references to the renumbered steps

Avoid "half steps" (Step 4.5) or lettered sub-steps (Step 4a, 4b) unless there is a specific reason to couple multiple
steps under the same number.

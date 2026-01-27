---
name: cat:skill-builder
description: Design or update skills and commands using backward reasoning
---

<objective>

Invoke the skill-builder skill to design or update skills/commands using backward reasoning
from goal to executable steps.

</objective>

<when_to_use>

Use this command when:
- Creating a new skill or command
- Updating an existing skill or command that has unclear or failing steps
- Any procedure where the goal is clear but the path is not

Both `skills/` and `commands/` are agent-facing prompt files that define behavior.
Use skill-builder for BOTH types.

</when_to_use>

<execution>

**Load and execute the skill-builder skill:**

@${CLAUDE_PLUGIN_ROOT}/skills/skill-builder/SKILL.md

Follow all steps in the loaded skill document.

</execution>

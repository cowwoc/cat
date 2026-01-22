---
name: cat:learn
description: Analyze and learn from mistakes
---

<objective>

Shortcut for `/cat:learn-from-mistakes`. This command MUST immediately invoke the skill.

</objective>

<execution>

**MANDATORY IMMEDIATE ACTION - Use Skill tool NOW:**

```
Skill(skill="cat:learn-from-mistakes", args="{ARGUMENTS}")
```

Do NOT perform ad-hoc analysis. Do NOT search for files first. Do NOT analyze the mistake yourself.
The ONLY correct action is to invoke the skill using the Skill tool. The skill contains the full
workflow for mistake analysis, prevention implementation, and recording.

**Why this matters (M167):** Ad-hoc analysis skips prevention implementation and recording, causing
mistakes to recur. The skill enforces the complete learning workflow.

</execution>

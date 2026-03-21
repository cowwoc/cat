# Skill Conventions

## Skill Instruction Location (M580)

Skill instructions (agent-facing guidance) belong in `first-use.md`, not in `SKILL.md`. The `SKILL.md` file contains
only frontmatter and preprocessor directives. Do NOT embed agent instructions directly in `SKILL.md` — they will not
be loaded correctly on subsequent invocations and bypass the per-agent deduplication logic in `GetSkill`.

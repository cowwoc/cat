# Skill Conventions

## Skill Instruction Location

Skill instructions (agent-facing guidance) belong in `first-use.md`, not in `SKILL.md`. The `SKILL.md` file contains
only frontmatter and preprocessor directives. Do not embed agent instructions directly in `SKILL.md` — otherwise those
instructions can be re-loaded multiple times within the same conversation.

**Exception — frontmatter-only skills:** Skills that are exclusively loaded via agent frontmatter `skills:` field
(never invoked dynamically via the Skill tool) may place content directly in `SKILL.md`. Deduplication logic is
irrelevant for frontmatter-loaded skills because they are injected once per agent spawn, not on repeated invocations.
Example: `stakeholder-common`, which is listed in agent frontmatter and never called via the Skill tool at engine.

## Trivial Include Bodies

Do not create or keep a `cat:include` fragment whose only meaningful content is:

```markdown
See `${CAT_PLUGIN_ROOT}/rules/common/skill-loading.md` and follow it exactly.
```

Inline that instruction directly in each engine `SKILL.md` instead. Keep `cat:include` fragments for shared bodies that
contain substantive multi-line instructions, engine-neutral delegation rules, or other content where the include removes
real duplication.

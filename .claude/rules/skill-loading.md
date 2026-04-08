---
paths: ["plugin/skills/**", "plugin/agents/**"]
---
## Skill Loading

Before creating, modifying, or debugging skills or agent `skills:` frontmatter, read `plugin/concepts/skill-loading.md`.

Key points:
- Plugin skills use the `cat:` prefix (e.g., `cat:git-squash`)
- Each agent (main and subagents) has an independent per-agent marker file under
  `{sessionDir}/skills-loaded` (main) or `{sessionDir}/subagents/{agent_id}/skills-loaded` (subagents)

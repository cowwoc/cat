---
paths: ["client/plugin/skill-sources/**", "client/plugin/agents/**"]
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Skill Loading

Before creating, modifying, or debugging skills or agent `skills:` frontmatter, read
`client/plugin/concepts/skill-loading.md`.

Key points:
- Plugin skills use the `cat:` prefix (e.g., `cat:git-squash-agent`)
- Each agent (main and subagents) has an independent per-agent marker file under
  `{sessionDir}/skills-loaded` (main) or `{sessionDir}/subagents/{agent_id}/skills-loaded` (subagents)

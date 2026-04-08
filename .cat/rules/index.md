---
mainAgent: false
subAgents: []
---
# CAT Rules Index

Rules in this directory are injected by CAT hooks based on audience frontmatter properties. Each file
can declare who receives it using `mainAgent`, `subAgents`, and `paths` frontmatter:

```yaml
---
paths: ["*.java"]      # Only inject when operating on matching files (default: always)
---
```

All properties have defaults and can be omitted: `mainAgent` defaults to `true`, `subAgents` defaults to
all, `paths` defaults to always inject.
See `plugin/concepts/rules-audience.md` for full documentation of the two-tier rules system.

## Rules in This Directory

| Rule File | Audience | Paths | Purpose |
|-----------|----------|-------|---------|
| [hooks.md](hooks.md) | main agent only | always | Hook registration and approval gate protocol |
| [convention-locations.md](convention-locations.md) | main agent only | always | Where to put end-user vs plugin-dev conventions |
| [bug-workaround.md](bug-workaround.md) | all agents | `plugin/**`, `client/**` | Standard comment format for external bug workarounds |
| [documentation-style.md](documentation-style.md) | all agents | `*.md` | Documentation wording and line-wrapping conventions |
| [index-schema.md](index-schema.md) | all agents | `index.json`, `**/index.json` | Required schema for issue `index.json` files |
| [llm-to-java.md](llm-to-java.md) | all agents | `plugin/**`, `client/**` | Extract deterministic skill logic into Java |
| [pre-existing-problems.md](pre-existing-problems.md) | all agents | always | Fix pre-existing problems when they violate issue goals |
| [skill-loading.md](skill-loading.md) | all agents | `plugin/skills/**`, `plugin/agents/**` | Skill loading model and marker-file rules |
| [skill-step-numbering.md](skill-step-numbering.md) | all agents | `plugin/skills/**`, `plugin/agents/**` | Enforce sequential 1-based skill step numbering |
| [terminology.md](terminology.md) | all agents | always | Keep config terminology identical across code/docs |

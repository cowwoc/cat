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

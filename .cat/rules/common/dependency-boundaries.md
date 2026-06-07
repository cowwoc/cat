---
mainAgent: true
subAgents: []
---
# Dependency Boundary Conventions

Keep production dependency boundaries as narrow as the behavior requires.

## Production Dependencies

- Pass collaborators only the capability or value they actually need. Do not inject a broad object when a scalar value
  or smaller interface is sufficient.
- Store derived immutable values in a collaborator when the collaborator only needs that value after construction.
- Engine-specific behavior belongs in engine adapters or engine-owned classes. Common orchestration classes may choose
  an adapter, but they should not expose engine-specific helper methods themselves.

## Common vs Engine-Specific Placement

- Common modules, packages, directories, classes, skills, rules, and concepts must remain engine-agnostic.
  Do not place engine-specific fields, concepts, conditionals, helper methods, or execution code in `common` areas.
- In particular, `common` areas must not hardcode engine-specific model names, effort names, capability/profile labels,
  runtime policy tables, or helper names that encode a specific engine or model family.
- Treat concrete model ids, effort enum values, capability-rank tables, profile-selection heuristics, and engine-owned
  runtime defaults as engine-specific data even when they are passed around as ordinary strings or numbers.
- In Java modules, treat `client/common-cli` as a strict example of a `common` area: it must not own engine-specific
  model/effort names, profile ranking tables, runtime-policy constants, or engine-branded helper APIs.
- `client/common-cli` may consume engine-neutral abstractions and injected metadata, but the source of truth for any
  engine/model/effort-specific mapping or fallback must live in the engine-specific module that owns that runtime.
- Place engine-specific behavior only under engine-specific locations (for example Claude- or Codex-specific modules,
  packages, directories, and skill/rule wrappers).
- If behavior differs by engine, split at the engine boundary and keep the shared/common layer focused on neutral
  orchestration contracts and shared logic only.

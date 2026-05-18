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
- Runtime-specific behavior belongs in runtime adapters or runtime-owned classes. Common orchestration classes may choose
  an adapter, but they should not expose runtime-specific helper methods themselves.

## Common vs Runtime-Specific Placement

- Common modules, packages, directories, classes, skills, rules, and concepts must remain runtime-agnostic.
  Do not place runtime-specific fields, concepts, conditionals, helper methods, or execution code in `common` areas.
- Place runtime-specific behavior only under runtime-specific locations (for example Claude- or Codex-specific modules,
  packages, directories, and skill/rule wrappers).
- If behavior differs by runtime, split at the runtime boundary and keep the shared/common layer focused on neutral
  orchestration contracts and shared logic only.

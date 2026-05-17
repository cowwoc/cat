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

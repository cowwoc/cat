---
agents: ["main"]
---
# Testing Requirements

Mandatory:
- Invoke `cat:tdd-implementation` before implementing any bugfix or feature with testable inputs/outputs.
- Write failing tests first, then implement the fix.
- In test code, prefer propagating the underlying checked exception instead of wrapping it in
  `AssertionError`.
- Test methods should declare the specific checked exceptions they can throw. Do not use
  `throws Exception` in test signatures.
- Run full verification before presenting work for review:

```bash
mvn -f client/pom.xml verify -e
```

All tests must pass (exit code 0) before requesting approval. Do not assume prior passing results remain valid after
changes.

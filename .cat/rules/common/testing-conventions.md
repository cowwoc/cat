---
paths:
  - "client/**"
---
## Testing Convention Loading Index

When adding or changing test fixtures, deterministic test data, test coverage expectations, or build/test rerun
strategy:
Lazy load `../include/testing/fixtures-reruns.md`.

When writing tests that touch filesystems, git, processes, environment, global state, time, concurrency, temp
directories, or engine `Test*` scopes:
Lazy load `../include/testing/isolation.md`.

When deciding what a test should assert, distinguishing product behavior from external tool behavior, or evaluating
whether design/layout constraints belong in tests:
Lazy load `../include/testing/behavior.md`.

When adding or changing test-only access seams, SharedSecrets-style access, tests for non-code files, or retrospective
documentation tests:
Lazy load `../include/testing/access-noncode.md`.

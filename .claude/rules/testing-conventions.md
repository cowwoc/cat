---
paths: ["client/**"]
---
## Testing

- Java: TestNG for unit tests
- Bash: Bats (Bash Automated Testing System)
- Minimum coverage: 80% for business logic
- All edge cases must have tests

### No Redundant Builds

**Do not re-run a build or test suite if no source files changed since the last successful run.** A passing
build remains valid until files are modified. Re-running an unchanged build wastes time and adds noise to the
session.

**When a re-run IS required:**
- Any tracked source file was added, modified, or deleted since the last successful build
- The build tool configuration changed (e.g., `pom.xml`, `build.gradle`, `Makefile`)
- An external dependency changed (e.g., a dependency was upgraded)

**When a re-run is NOT required:**
- Only documentation, comments, or non-source files changed (e.g., `.md`, `.txt`)
- Only planning artifacts changed (`.cat/issues/`, `CLAUDE.md`)
- The last build passed and nothing has been committed or staged since

### Test Isolation

Tests must be **self-contained**, **thread-safe**, and must **never impact the production environment**:

1. **No operations against the real repository** — tests must never run git commands against the project's working
   directory, even read-only queries. Use isolated temporary repos. For validation-only tests where execution fails
   before any external operation, this is acceptable since no command actually runs.
2. **No production environment side effects** — tests must not modify files, git state, processes, or configuration
   outside their temporary directories.
3. **Concurrent safety** — multiple test runs, parallel tests, and concurrent Claude instances must not interfere with
   each other or with the host environment. Avoid JVM-global or process-global mutation (e.g., environment variables,
   system properties, stdout/stderr redirection, current working directory).
4. **Deterministic** — test results must not depend on host machine configuration, repository state, or timing. Use
   controlled inputs and injectable dependencies (e.g., `Clock` for time, temp dirs for paths).

**Why:** A leaky test that runs `git reset --soft HEAD~1 && git commit` against the real repo will silently corrupt the
working branch on every build. This is catastrophic when builds automatically or in parallel.

### Test Isolation

Tests must be **self-contained**, **thread-safe**, and must **never impact the production environment**:

1. **No operations against the real repository** — tests must never run git commands against the project's working
   directory, even read-only queries. Use isolated temporary repos. For validation-only tests where execution fails
   before any external operation, this is acceptable since no command actually runs.
2. **No production environment side effects** — tests must not modify files, git state, processes, or configuration
   outside their temporary directories.
3. **Concurrent safety** — multiple test runs, parallel tests, and concurrent engine instances must not interfere
   with each other or with the host environment. Avoid JVM-global or process-global mutation (e.g., environment
   variables, system properties, stdout/stderr redirection, current working directory).
4. **Deterministic** — test results must not depend on host machine configuration, repository state, or timing. Use
   controlled inputs and injectable dependencies (e.g., `Clock` for time, temp dirs for paths).
5. **Test-specific scopes only** — test code must interact with `Test*` scope implementations, not `Main*` production
   scopes. Production scopes read host environment variables, stdin, or engine-specific filesystem locations and are
   reserved for production entrypoints. For example, use `TestCodexTool`, `TestCodexHook`, `TestClaudeTool`, or
   `TestClaudeHook` instead of `MainCodexTool`, `MainCodexHook`, `MainClaudeTool`, or `MainClaudeHook`.

**Why:** A leaky test that runs `git reset --soft HEAD~1 && git commit` against the real repo will silently corrupt the
working branch on every build. This is catastrophic when builds automatically or in parallel.

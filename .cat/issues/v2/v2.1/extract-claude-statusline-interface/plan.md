# Plan

## Goal

Change `StatuslineCommand`'s constructor to accept a `ClaudeStatusline` instance instead of `JvmScope`.
Create a new `ClaudeStatusline` interface that extends `JvmScope`, declaring only the getter methods
for the statusline data that `StatuslineCommand` needs. The interface should be informed by the
available statusline data at https://code.claude.com/docs/en/statusline#available-data.

## Pre-conditions

(none)

## Post-conditions

- [ ] `ClaudeStatusline` interface created, extends `JvmScope`, declares only the getter methods
  `StatuslineCommand` needs (based on statusline available data docs)
- [ ] `StatuslineCommand` constructor signature changed to accept `ClaudeStatusline` instead of `JvmScope`
- [ ] User-visible statusline behavior unchanged
- [ ] All tests updated and passing, no regressions
- [ ] Code quality improved (StatuslineCommand depends on a narrower, purpose-specific interface)
- [ ] E2E verification: configure `StatuslineCommand` with a `ClaudeStatusline` instance and confirm
  statusline output matches pre-refactor behavior

## Research Findings

**What StatuslineCommand uses from JvmScope:**

Inspecting `StatuslineCommand.java`, only two `JvmScope` methods are used:
1. `scope.getJsonMapper()` — in the constructor, to obtain the shared Jackson JSON mapper for parsing
   statusline hook JSON input
2. `scope.getCatWorkPath()` — in `execute()`, to derive `getCatWorkPath().resolve("locks")` for scanning
   lock files and displaying the active CAT issue in the statusline

**Existing interface hierarchy:**

```
JvmScope (interface, AutoCloseable)
  └── ClaudeTool (interface, extends JvmScope — adds session-specific env vars)

AbstractJvmScope (abstract class, implements JvmScope — provides lazy singletons)
  └── AbstractClaudeTool (abstract class, extends AbstractJvmScope, implements ClaudeTool)
    ├── MainClaudeTool (production)
    └── TestClaudeTool (tests)
```

`ClaudeTool` is the existing precedent: it extends `JvmScope` and re-declares (with `@Override`) specific
inherited methods to document the contract more precisely. `ClaudeStatusline` follows the same pattern.

**Alternatives considered and rejected:**

1. **Inverted hierarchy (`JvmScope extends ClaudeStatusline`):** This would achieve true interface segregation
   (StatuslineCommand has access only to 2 methods). Rejected because the issue explicitly states that
   `ClaudeStatusline` extends `JvmScope`.

2. **Marker interface (empty body):** `ClaudeStatusline extends JvmScope` with no declarations. Rejected
   because the issue says "declaring only the getter methods" — the interface should explicitly document
   which getter methods are the statusline dependency.

3. **Scope-based statusline data getters:** `ClaudeStatusline` adds methods like `getModelDisplayName()`,
   `getTotalDurationMs()`, etc. so `StatuslineCommand` reads parsed fields from the scope instead of parsing
   stdin JSON. Rejected: this would be a major behavioral refactor, not a constructor parameter extraction.
   The CLI tool architecture relies on reading per-invocation JSON from stdin.

**Statusline data fields (from existing implementation):**

`StatuslineCommand` currently parses these JSON fields from stdin:
- `model.display_name` — model display name
- `session_id` — session ID
- `cost.total_duration_ms` — session duration in milliseconds
- `context_window.used_percentage` — context usage percentage (0–100)

These come from Claude Code's hook payload on each invocation. `ClaudeStatusline` does not need to expose
these as scope methods; they are transient per-invocation values handled by `execute(InputStream, ...)`.

## Sub-Agent Waves

### Wave 1

- Create new file `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeStatusline.java`:
  - Package: `io.github.cowwoc.cat.hooks`
  - License header (Java block comment style, 2026)
  - Interface `ClaudeStatusline extends JvmScope`
  - Javadoc: "A {@link JvmScope} that provides the infrastructure needed to render the Claude Code statusline.
    Implementations must expose a JSON mapper for parsing the statusline hook payload and the CAT work path
    for discovering the active issue lock."
  - Declare two `@Override` methods with Javadoc:
    - `JsonMapper getJsonMapper()` — "Returns the shared JSON mapper used to parse the statusline hook payload."
    - `Path getCatWorkPath()` — "Returns the cross-session CAT work directory whose {@code locks/} subdirectory
      is scanned for the active issue lock file."
  - Both methods must carry `@throws IllegalStateException if this scope is closed`

- Update `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`:
  - Change class declaration from `implements JvmScope` to `implements JvmScope, ClaudeStatusline`
  - No other changes (AbstractJvmScope already implements both declared methods via existing @Override methods)

- Update `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java`:
  - Change import from `import io.github.cowwoc.cat.hooks.JvmScope;` to
    `import io.github.cowwoc.cat.hooks.ClaudeStatusline;`
  - Change field declaration from `private final JvmScope scope;` to `private final ClaudeStatusline scope;`
  - Change constructor parameter from `JvmScope scope` to `ClaudeStatusline scope`
  - Update constructor Javadoc `@param scope` description from "the JVM scope for accessing shared services"
    to "the statusline scope for accessing JSON parsing and the CAT work path"
  - Change `run()` static method parameter from `JvmScope scope` to `ClaudeStatusline scope`
  - Update `run()` Javadoc `@param scope` accordingly
  - Update `run()` import: `ClaudeTool` import stays (used in `main()`); `JvmScope` import removed;
    `ClaudeStatusline` import added
  - No logic changes — all usages of `scope` (getJsonMapper, getCatWorkPath) are already valid on ClaudeStatusline

- Update `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineCommandMainTest.java`:
  - Add `import io.github.cowwoc.cat.hooks.ClaudeStatusline;`
  - In each test method, change the try-with-resources variable declaration from:
    `try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))`
    to:
    `try (ClaudeStatusline scope = new TestClaudeTool(tempDir, tempDir))`
  - This compiles because `TestClaudeTool extends AbstractJvmScope` which now implements `ClaudeStatusline`
  - Remove `import io.github.cowwoc.cat.hooks.JvmScope;` if no longer used in the file

- Run the full build to verify no regressions:
  ```bash
  cd /workspace/.cat/work/worktrees/2.1-extract-claude-statusline-interface && mvn -f client/pom.xml verify -e
  ```
  All tests must pass (exit code 0). Fix any checkstyle or PMD violations before committing.

- Commit all changes with:
  ```
  refactor: extract ClaudeStatusline interface for StatuslineCommand
  ```
  The commit must include: `ClaudeStatusline.java` (new), `AbstractJvmScope.java` (updated),
  `StatuslineCommand.java` (updated), `StatuslineCommandMainTest.java` (updated), and
  `.cat/issues/v2/v2.1/extract-claude-statusline-interface/index.json` (set `"status": "closed"` and
  `"progress": 100` fields).
  Use commit type `refactor:` per CLAUDE.md (client/ files, restructuring without behavior change).

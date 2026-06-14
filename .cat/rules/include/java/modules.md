## Build Commands

```bash
cd engine

./build.sh        # Build JAR (mvn package)
./build.sh test   # Run TestNG tests (mvn test)
./build.sh clean  # Clean artifacts (mvn clean)
mvn verify        # Full build validation (compile + test + checkstyle + PMD)
```

**Verification:** Always run `mvn verify` to verify the build. Do not use `mvn test` or `mvn compile` — these skip
checkstyle and PMD checks, allowing lint violations to go undetected.

## Module Structure

All modules must define `module-info.java`. Tests reside in a separate module from implementation.

### Naming Convention
| Implementation | Test Module | Test Package |
|----------------|-------------|--------------|
| `io.github.cowwoc.cat.client.engine` | `io.github.cowwoc.cat.client.engine.test` | `io.github.cowwoc.cat.client.test` |
| `com.example.foo` | `com.example.foo.test` | `com.example.foo.test` |

### Module Exports for Testing
The implementation module must export internal packages to the test module:

```java
// module-info.java for io.github.cowwoc.cat.client.engine (module name; packages are io.github.cowwoc.cat.engine.hook/tool)
module io.github.cowwoc.cat.client.engine
{
  requires tools.jackson.databind;

  // Public API — packages are under engine.hook and engine.tool
  exports io.github.cowwoc.cat.engine.hook;
  exports io.github.cowwoc.cat.engine.tool;

  // Internal packages exported only to test module
  exports io.github.cowwoc.cat.engine.hook.internal to io.github.cowwoc.cat.client.engine.test;
}
```

```java
// module-info.java for io.github.cowwoc.cat.client.engine.test
module io.github.cowwoc.cat.client.engine.test
{
  requires io.github.cowwoc.cat.client.engine;
  requires org.testng;
  requires io.github.cowwoc.requirements13.java;
}
```

### Access Implications
- Methods tested directly must be `public` (visible across modules)
- Private methods that need cross-module test access: use the SharedSecrets pattern (do NOT use `@VisibleForTesting`)
- Package-private methods can only be tested indirectly through public API
- Use `exports ... to ...` for targeted exports to test module only
- Use `opens ... to ...` only when deep reflection is required (e.g., Jackson deserialization); do NOT add test module
  to `opens` directives when SharedSecrets suffices

**Do not use `@VisibleForTesting`.** Widening method visibility solely for tests introduces accidental API surface and
violates encapsulation. Use the SharedSecrets pattern instead:

1. Add a `@FunctionalInterface` inner interface to `SharedSecrets.java` (e.g., `SkillTestRunnerAccess`)
2. Add a `private static` field and `setXAccess()` setter in `SharedSecrets`
3. Add a public accessor in `SharedSecrets` that calls `initialize(TheClass.class)` if the field is null
4. Register via a static initializer in the class: `static { SharedSecrets.setXAccess(TheClass::theMethod); }`
5. Tests call `SharedSecrets.theMethod(...)` directly; no instance or `@VisibleForTesting` needed

**SharedSecrets must point to existing production methods.** `SharedSecrets` entries must delegate to methods that
exist in and are called by production code. Do not create new methods solely to satisfy a `SharedSecrets` hook —
instead, extract the logic into a real production method first, then expose it via `SharedSecrets`. This ensures
tests verify actual production behavior, not test-only code paths.

## Project Structure

```
client/                      # Maven project root
├── pom.xml
├── build.sh
├── mvnw
├── src/main/java/           # Implementation module (io.github.cowwoc.cat.client.engine) — packages under io.github.cowwoc.cat.engine.hook/tool
│   ├── module-info.java     # Module io.github.cowwoc.cat.client.engine
│   └── io/github/cowwoc/cat/
│       └── engine/
│           ├── tool/          # Tool-related classes
│           │   └── post/
│           └── hook/          # Hook-related classes
│               ├── ask/
│               ├── bash/
│               └── ...
└── src/test/java/           # Test module (io.github.cowwoc.cat.client.engine.test)
    └── io/github/cowwoc/cat/client/test/
        └── module-info.java
```

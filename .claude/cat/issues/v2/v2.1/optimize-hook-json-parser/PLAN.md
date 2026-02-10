# Plan: optimize-hook-json-parser

## Goal
Replace jackson-databind (JsonMapper/JsonNode/ObjectNode) with jackson-core streaming API (JsonParser/JsonGenerator)
across all hook handler Java code to reduce startup time from ~97ms to ~49ms with AOTCache.

## Satisfies
None - infrastructure performance optimization

## Research Findings
Benchmark measurements from the current session:

| Configuration | ObjectMapper (current) | JsonParser (target) |
|---|---|---|
| Full JDK + classpath | 491ms | 153ms |
| jlink + base CDS | 236ms | 80ms |
| jlink + AppCDS | - | 56ms |
| jlink + AOTCache | 97ms | 49ms |

**Root cause**: ObjectMapper loads 548 Jackson classes with 820 static initializers. JsonParser (jackson-core only)
loads 89 classes. The 459 extra classes account for ~194ms cold startup and ~48ms with AOTCache.

**Additional finding**: `hook.sh` has a bug where both `-XX:AOTCache` and `-XX:SharedArchiveFile` flags are passed
simultaneously when both files exist. These are mutually exclusive JVM flags. AOTCache subsumes AppCDS.

## Risk Assessment
- **Risk Level:** HIGH
- **Concerns:** 56 Java files import jackson-databind; changing JSON handling API across all handlers is a large
  mechanical refactoring with risk of subtle behavioral changes
- **Mitigation:** All existing tests must pass; the refactoring is mechanical (JsonNode.get(key).asString() becomes
  Map.get(key)); run benchmarks before/after to verify improvement

## Files to Modify

### Core infrastructure (API changes)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/HookInput.java` - Replace JsonMapper/JsonNode with
  JsonParser/Map-based parsing
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/HookOutput.java` - Replace JsonMapper/ObjectNode with
  JsonGenerator or manual JSON string building
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` - Change `requires tools.jackson.databind` to
  `requires tools.jackson.core`

### Handler interfaces (signature changes)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/BashHandler.java` - Update parameter types from JsonNode to
  Map/String
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/ReadHandler.java` - Same
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/EditHandler.java` - Same
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/AskHandler.java` - Same
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/FileWriteHandler.java` - Same
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/PosttoolHandler.java` - Same
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/TaskHandler.java` - Same
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/PromptHandler.java` - Same (if uses JsonNode)

### All 56 handler implementations that import jackson-databind
- Every file listed in the codebase search that imports `tools.jackson.databind` must be updated to use jackson-core
  streaming equivalents or Map-based access patterns

### Build and deployment
- `hooks/pom.xml` - Change dependency from `jackson-databind` to `jackson-core`
- `hooks/build-jlink.sh` - Update jlink module list (remove `tools.jackson.databind`, keep `tools.jackson.core`)
- `plugin/hooks/hook.sh` - Fix mutual exclusivity: use if/elif so AOTCache takes priority over SharedArchiveFile

### Java conventions update
- `.claude/cat/conventions/java.md` - Update "JSON Library" from `JsonMapper` to `JsonParser/JsonGenerator
  (jackson-core streaming)`; update the JsonMapper Usage section

## Acceptance Criteria
- [ ] Performance target met: hook startup <= 55ms with AOTCache
- [ ] No functionality regression: all existing tests pass
- [ ] No jackson-databind imports remain in any source file
- [ ] hook.sh correctly uses elif for AOTCache vs SharedArchiveFile (mutually exclusive)
- [ ] jlink image size reduced (jackson-core only: ~69MB vs current ~92MB)

## Execution Steps
1. **Fix hook.sh mutual exclusivity bug:** Change the two independent `[[ -f ]]` checks on lines 68-69 to an
   if/elif so AOTCache takes priority when both files exist
   - Files: `plugin/hooks/hook.sh`
2. **Redesign HookInput for streaming:** Replace JsonMapper/JsonNode internals with JsonParser from jackson-core.
   Parse stdin JSON into `Map<String, Object>` where values are String for scalars, `Map<String, Object>` for nested
   objects, and `List<Object>` for arrays. Remove all jackson-databind imports.
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/HookInput.java`
3. **Redesign HookOutput for streaming:** Replace JsonMapper/ObjectNode with JsonGenerator from jackson-core for
   JSON output generation. Use `JsonFactory.createGenerator()` to write JSON to the PrintStream.
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/HookOutput.java`
4. **Update handler interfaces:** Change all handler interface method signatures that accept/return JsonNode to use
   `Map<String, Object>` instead. Update BashHandler, ReadHandler, EditHandler, AskHandler, FileWriteHandler,
   PosttoolHandler, TaskHandler, PromptHandler.
   - Files: All *Handler.java interfaces
5. **Migrate all handler implementations:** For each of the 56 files that import jackson-databind, replace:
   - `JsonNode node = toolInput.get("key")` with `Object node = toolInput.get("key")`
   - `node.asString()` with `(String) node` or `String.valueOf(node)`
   - `node.isNull()` with `node == null`
   - `node.isString()` with `node instanceof String`
   - `node.toString()` with `String.valueOf(node)` or `new ObjectMapper().writeValueAsString(node)` equivalent
   - `mapper.readTree(...)` with streaming JsonParser equivalents
   - `mapper.createObjectNode()` with `new LinkedHashMap<>()`
   - `mapper.writeValueAsString(node)` with JsonGenerator-based serialization
   - Files: All 56 files from the jackson-databind import search
6. **Update module-info.java:** Change `requires tools.jackson.databind` to `requires tools.jackson.core`
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java`
7. **Update pom.xml:** Change dependency from `tools.jackson.core:jackson-databind` to
   `tools.jackson.core:jackson-core`
   - Files: `hooks/pom.xml`
8. **Update build-jlink.sh:** Remove jackson-databind modules from jlink image, ensure only jackson-core modules
   are included
   - Files: `hooks/build-jlink.sh`
9. **Update Java conventions:** Change the JSON Library convention from JsonMapper to JsonParser/JsonGenerator
   - Files: `.claude/cat/conventions/java.md`
10. **Run all tests:** Execute `mvn -f hooks/pom.xml verify` to ensure no regressions
11. **Rebuild jlink image and measure:** Run `hooks/build-jlink.sh` and measure startup time to verify performance
    target

## Success Criteria
- [ ] All tests pass (`mvn -f hooks/pom.xml verify` exits 0)
- [ ] No files import `tools.jackson.databind` (`grep -r 'import tools.jackson.databind' hooks/src/` returns empty)
- [ ] Hook startup time <= 55ms with AOTCache (measured via benchmark)
- [ ] jlink image size < 75MB (down from ~92MB)
- [ ] hook.sh uses elif for AOTCache/SharedArchiveFile flags
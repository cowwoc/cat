# Plan: json-complex-handler-migration

## Goal
Migrate the remaining ~14 complex handler files (those with 5+ JsonNode/JsonMapper/ObjectNode occurrences) to use
jackson-core streaming API or Map-based access patterns. Remove jackson-databind dependency from pom.xml and
module-info.java.

## Satisfies
Parent: optimize-hook-json-parser (complete handler migration + dependency removal)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Complex files have deep JSON tree traversal, JSON building, and file-based JSON parsing. Each
  requires careful conversion to streaming or Map-based patterns.
- **Mitigation:** All existing tests must pass; verify no jackson-databind imports remain after migration.

## Files to Modify

### Complex handler implementations (5+ occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetTokenReportOutput.java` (22 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java` (17 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/JsonHelper.java` (16 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/session/CheckUpgrade.java` (13 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/read/pre/PredictBatchOpportunity.java` (12 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/session/CheckRetrospectiveDue.java` (11 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/read/post/DetectSequentialTools.java` (10 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/session/CheckUpdateAvailable.java` (9 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/prompt/UserIssues.java` (7 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/post/DetectFailures.java` (6 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java` (5 occurrences)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/VersionUtils.java` (5 occurrences)

### Build cleanup
- `hooks/pom.xml` - Remove jackson-databind, keep only jackson-core
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` - Remove `requires tools.jackson.databind`

## Acceptance Criteria
- [ ] No jackson-databind imports remain in any source file
- [ ] `mvn -f hooks/pom.xml verify` passes with jackson-core only
- [ ] All JSON parsing uses jackson-core JsonParser or Map-based access
- [ ] All JSON output uses jackson-core JsonGenerator or manual string building
- [ ] No `tools.jackson.databind` in module-info.java or pom.xml

## Execution Steps
1. **Migrate each complex handler** replacing:
   - `mapper.readTree(string)` with JsonParser-based parsing into Map<String, Object>
   - `node.get("key").asString()` with `(String) map.get("key")`
   - `node.isNull()` with `value == null`
   - `node.isString()` with `value instanceof String`
   - `mapper.createObjectNode()` with `new LinkedHashMap<>()`
   - `mapper.writeValueAsString(node)` with JsonGenerator or manual JSON building
   - JsonHelper utility methods to use streaming API
2. **Remove jackson-databind from pom.xml:** Change dependency from `jackson-databind` to `jackson-core` only
3. **Update module-info.java:** Remove `requires tools.jackson.databind`, keep `requires tools.jackson.core`
4. **Verify no jackson-databind imports remain:**
   `grep -r 'import tools.jackson.databind' hooks/src/` should return empty
5. **Run `mvn -f hooks/pom.xml verify`** to ensure no regressions

## Success Criteria
- [ ] `mvn -f hooks/pom.xml verify` exits 0
- [ ] `grep -r 'import tools.jackson.databind' hooks/src/` returns empty
- [ ] pom.xml has jackson-core only (no jackson-databind)

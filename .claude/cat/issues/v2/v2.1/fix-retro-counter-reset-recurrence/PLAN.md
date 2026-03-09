# Plan: fix-retro-counter-reset-recurrence

## Problem

`GetRetrospectiveOutput.java` generates the full retrospective analysis box but never resets the
`mistake_count_since_last` counter or updates `last_retrospective` in `index.json`. This causes
the retrospective to re-trigger every session indefinitely (counter never resets, so the threshold
condition remains permanently satisfied). This was supposed to be fixed by the closed issue
`fix-retro-counter-reset`, but the `resetRetrospectiveCounter()` method was never implemented.

## Parent Requirements

References closed issue `fix-retro-counter-reset`.

## Approach

Add `resetRetrospectiveCounter(Path retroDir, Path indexFile, JsonMapper mapper)` as a private
method in `GetRetrospectiveOutput`. Call it inside `generateAnalysis()` immediately before
returning, after all content lines are built and the display box string has been generated. Only
call it on the analysis output path — never on the status-message path or error paths.

The method:
1. Re-reads `index.json` from disk (fresh read to avoid stale in-memory data)
2. Casts the root `JsonNode` to `ObjectNode`
3. Calls `objectNode.put("last_retrospective", Instant.now().toString())`
4. Calls `objectNode.put("mistake_count_since_last", 0)`
5. Serializes back using `mapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectNode)`
6. Writes atomically: write to `indexFile.resolveSibling("index.json.tmp")`, then
   `Files.move(tempFile, indexFile, StandardCopyOption.REPLACE_EXISTING)`, then on IOException
   calls `Files.deleteIfExists(tempFile)` and rethrows

The call site in `generateAnalysis()` goes between:
```
    return display.buildHeaderBox("RETROSPECTIVE ANALYSIS", contentLines);
```
…and the last line, i.e., the return statement is modified to first generate the box string, then
call `resetRetrospectiveCounter(retroDir, indexFile, mapper)`, then return the box string.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** If counter reset fails, the analysis output was already generated. The analysis
  is still useful even if reset throws. Rethrow the IOException — the caller will handle it.
- **Mitigation:** Tests verify reset happens on success path only; atomic write prevents corruption.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java`
  - Add import: `tools.jackson.databind.node.ObjectNode`
  - Add import: `java.nio.file.StandardCopyOption`
  - Modify `generateAnalysis()`: capture box string, call reset, return box string
  - Add `resetRetrospectiveCounter(Path retroDir, Path indexFile, JsonMapper mapper)` method
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetRetrospectiveOutputTest.java`
  - Add three test methods (see Sub-Agent Waves below)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Implementation

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java`

**Step 1 — Add imports** (after existing import block):
```java
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.StandardCopyOption;
```

**Step 2 — Modify `generateAnalysis()`**: change the last line from:
```java
    return display.buildHeaderBox("RETROSPECTIVE ANALYSIS", contentLines);
```
to:
```java
    String boxOutput = display.buildHeaderBox("RETROSPECTIVE ANALYSIS", contentLines);
    resetRetrospectiveCounter(retroDir, indexFile, mapper);
    return boxOutput;
```
Note: `indexFile` and `mapper` are already available as local variables at the call site of
`generateAnalysis()` in `getOutput()`, but they are NOT current parameters of `generateAnalysis()`.
The method signature must be updated to receive them. Current signature:
```java
private String generateAnalysis(Path retroDir, JsonNode index, String lastRetro,
  String triggerReason, JsonMapper mapper) throws IOException
```
The `indexFile` variable (`retroDir.resolve("index.json")`) must also be passed. Update the
signature to add `Path indexFile` as a new parameter (after `retroDir`):
```java
private String generateAnalysis(Path retroDir, Path indexFile, JsonNode index, String lastRetro,
  String triggerReason, JsonMapper mapper) throws IOException
```
Update the call site in `getOutput()` (line 137) from:
```java
    return generateAnalysis(retroDir, root, lastRetro, triggerReason, mapper);
```
to:
```java
    return generateAnalysis(retroDir, indexFile, root, lastRetro, triggerReason, mapper);
```

**Step 3 — Add `resetRetrospectiveCounter()` method** (after `generateAnalysis()`, before
`loadMistakesSince()`):

```java
  /**
   * Resets the retrospective counter in index.json after a successful analysis output.
   * <p>
   * Sets {@code last_retrospective} to the current UTC timestamp and {@code mistake_count_since_last}
   * to {@code 0}. Writes atomically via a temp file to prevent corruption on failed writes.
   *
   * @param retroDir the retrospectives directory
   * @param indexFile the path to index.json
   * @param mapper the JSON mapper
   * @throws IOException if reading or writing index.json fails
   */
  private void resetRetrospectiveCounter(Path retroDir, Path indexFile, JsonMapper mapper)
    throws IOException
  {
    String content = Files.readString(indexFile);
    ObjectNode root = (ObjectNode) mapper.readTree(content);
    root.put("last_retrospective", Instant.now().toString());
    root.put("mistake_count_since_last", 0);
    String updated = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    Path tempFile = retroDir.resolve("index.json.tmp");
    try
    {
      Files.writeString(tempFile, updated);
      Files.move(tempFile, indexFile, StandardCopyOption.REPLACE_EXISTING);
    }
    catch (IOException e)
    {
      Files.deleteIfExists(tempFile);
      throw e;
    }
  }
```

### Wave 2: Tests

**File:** `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetRetrospectiveOutputTest.java`

Add the following three test methods. Pattern follows existing tests in the file: create tempDir,
create `TestJvmScope(tempDir, tempDir)`, create `retroDir` at
`tempDir.resolve(".claude/cat/retrospectives")`, write `index.json` with `Files.writeString`,
instantiate `GetRetrospectiveOutput`, call `getOutput(new String[0])`, assert on result, then
clean up in `finally`.

---

**Test 1: `counterResetAfterSuccessfulAnalysis`**

Purpose: verify that when retrospective analysis is triggered and generated, `index.json` is
updated so `mistake_count_since_last` becomes `0` and `last_retrospective` is updated to a recent
timestamp.

Setup:
- `last_retrospective` = 8 days ago (`Instant.now().minus(8, ChronoUnit.DAYS)`)
- `mistake_count_since_last` = 3
- `trigger_interval_days` = 7, `mistake_count_threshold` = 10
- `files.mistakes` = `[]`, `files.retrospectives` = `[]`
- `patterns` = `[]`, `action_items` = `[]`

After calling `getOutput(new String[0])`, read `index.json` back from disk and assert:
- `output` contains `"RETROSPECTIVE ANALYSIS"` (confirms analysis path was taken)
- `mistake_count_since_last` field in re-read JSON equals `0`
- `last_retrospective` field in re-read JSON is not the original 8-days-ago value (it was updated)
- `last_retrospective` field parses as an `Instant` that is within 60 seconds of `Instant.now()`

Exact method signature:
```java
  /**
   * Verifies that mistake_count_since_last is reset to 0 in index.json after successful
   * retrospective analysis output is generated.
   */
  @Test
  public void counterResetAfterSuccessfulAnalysis() throws IOException
```

---

**Test 2: `counterNotResetWhenThresholdNotMet`**

Purpose: verify that when retrospective is NOT triggered (status message path), `index.json` is
NOT modified — `mistake_count_since_last` stays at its original value.

Setup:
- `last_retrospective` = 1 day ago (`Instant.now().minus(1, ChronoUnit.DAYS)`)
- `mistake_count_since_last` = 5
- `trigger_interval_days` = 7, `mistake_count_threshold` = 10

After calling `getOutput(new String[0])`, read `index.json` back from disk and assert:
- `output` contains `"Retrospective not triggered"` (confirms status-message path)
- `mistake_count_since_last` field in re-read JSON still equals `5` (unchanged)

Exact method signature:
```java
  /**
   * Verifies that mistake_count_since_last is not reset when the retrospective threshold is not met
   * and a status message is returned instead of an analysis.
   */
  @Test
  public void counterNotResetWhenThresholdNotMet() throws IOException
```

---

**Test 3: `counterNotResetOnErrorPath`**

Purpose: verify that when an error occurs (missing index.json case produces an error string), no
reset happens. Since the error cases (missing dir, missing index.json) produce error strings before
any counter manipulation, this test verifies that when the missing-directory error fires, the
index.json (if it existed in a different location) is not touched.

Use the missing-retrospectives-directory error path: create tempDir with NO `.claude/cat/retrospectives`
directory. Call `getOutput(new String[0])` and assert:
- `output` contains `"ERROR:"` and `"Retrospectives directory not found"` (confirms error path)
- No `index.json.tmp` file exists in `tempDir` (confirms no write was attempted)

Exact method signature:
```java
  /**
   * Verifies that no counter reset is attempted when getOutput returns an error (e.g., missing
   * retrospectives directory).
   */
  @Test
  public void counterNotResetOnErrorPath() throws IOException
```

### Wave 3: Verify

Run:
```bash
mvn -f client/pom.xml test
```
All tests must pass (exit code 0).

## Post-conditions

- [ ] `resetRetrospectiveCounter()` method exists in `GetRetrospectiveOutput.java`
- [ ] Method updates `index.json`: sets `last_retrospective` to current ISO timestamp and
  `mistake_count_since_last` to `0`
- [ ] Method is called only on success path (analysis output generation), NOT on status-message
  or error paths
- [ ] Atomic write: method writes `index.json.tmp` first, then moves to `index.json` with
  `REPLACE_EXISTING`, deletes temp on IOException
- [ ] Test `counterResetAfterSuccessfulAnalysis` verifies counter resets to 0 after successful
  retrospective analysis
- [ ] Test `counterNotResetWhenThresholdNotMet` verifies counter is NOT reset when output is a
  status message (threshold not met)
- [ ] Test `counterNotResetOnErrorPath` verifies counter is NOT reset when output is an error
- [ ] E2E: Running `get-output retrospective` when threshold exceeded resets
  `mistake_count_since_last` to 0 in `index.json`
- [ ] All tests pass (`mvn -f client/pom.xml test`)

# Plan

## Goal

Extract all deterministic config operations (read and write) from the config skill into a Java CLI tool.
This covers the full round-trip: the questionnaire answer→config.json write, the manual settings write,
and the get-config-output calls used to display current values for the "(current)" indicators.

The new tool (`update-config`) accepts key=value arguments and atomically updates config.json.
Reading current values uses the existing get-config-output tool where possible.

## Pre-conditions

(none)

## Post-conditions

- [ ] A Java CLI tool (`update-config`) accepts key=value pairs and atomically updates config.json
- [ ] The config skill's questionnaire write step delegates to the Java tool instead of constructing JSON inline
- [ ] The config skill's manual settings write step delegates to the Java tool
- [ ] The config skill's width-settings, completion-workflow, min-severity, and update-config steps delegate to the Java tool
- [ ] User-visible behavior is unchanged (same values written, same display)
- [ ] Unit tests for the new Java tool cover happy path, unknown keys, and malformed input
- [ ] E2E: run /cat:config, answer the questionnaire, confirm config.json contains the correct values

## Research Findings

The config skill is in `plugin/skills/config/first-use.md` (loaded by `config-agent` via GetSkill's
`-agent` fallback). The existing `GetConfigOutput.java` handles reading. All LLM-driven JSON writes
must be replaced with the new `update-config` binary.

Write locations in `first-use.md`:
1. `manual-settings` step — writes trust/caution/curiosity/perfection/verbosity via Read+Write tools
2. `questionnaire` step — writes the same 5 keys via Read+Write tools
3. `width-settings` step — writes fileWidth or displayWidth via Read+Write tools
4. `completion-workflow` step — writes completionWorkflow via Read+Write tools
5. `min-severity` step — writes minSeverity via Read+Write tools
6. `update-config` step — generic Read+Write helper used by individual trust/caution/etc. steps

`Config.java` owns the known-key set (DEFAULTS). `UpdateConfig` must validate input keys against this
set. Add `public static Set<String> knownKeys()` to expose the set without duplicating it.

`UpdateConfig` reads ONLY from `config.json` (not the merged 3-layer config) to avoid clobbering
`config.local.json` overrides. It writes back ONLY to `config.json`.

Atomic write: write to a temp file in the same directory, then move to replace `config.json`.

## Jobs

### Job 1
- Add `public static Set<String> knownKeys()` to `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`
  returning `Set.copyOf(DEFAULTS.keySet())`
- Create `client/src/main/java/io/github/cowwoc/cat/hooks/util/UpdateConfig.java`:
  - Accepts one or more `key=value` positional args
  - Derives config path via `scope.getProjectPath().resolve(Config.CAT_DIR_NAME).resolve("config.json")`
  - Reads the raw JSON map from that path if present (no defaults applied), or starts with empty map
  - For each arg: splits on first `=`; rejects args with no `=` or empty key
  - Validates key is in `Config.knownKeys()`; rejects unknown keys
  - Validates value by key type:
    - trust/caution/curiosity/perfection/verbosity: call the corresponding `fromString()` method (TrustLevel, CautionLevel, CuriosityLevel, PerfectionLevel, VerbosityLevel)
    - minSeverity: call `ConcernSeverity.fromString()`
    - completionWorkflow: must be "merge" or "pr"; reject any other value with `{"status":"ERROR","message":"..."}`
    - fileWidth/displayWidth: parse as int, require 40–200 inclusive
    - license: any non-null string accepted
  - Merges validated key=value pairs into the existing map
  - Atomically writes the updated map back to `.cat/config.json`
    (write to `config.json.tmp` in same directory, then `Files.move` with `REPLACE_EXISTING,ATOMIC_MOVE` to rename)
  - On success prints: `{"status":"OK"}`
  - On validation failure prints: `{"status":"ERROR","message":"<reason>"}`
  - Follows `public static void run(ClaudeTool scope, String[] args, PrintStream out)` signature for testability
  - `main()` must use `MainClaudeTool` scope, nested try blocks per hooks.md pattern:
    outer try for scope creation failure → stderr fallback,
    inner try for `run()` → on `RuntimeException|AssertionError` log and print `ClaudeHook.block()` to stdout
  - Include license header
- Add `"update-config:util.UpdateConfig"` entry to the HANDLERS array in `client/build-jlink.sh`
  (insert after the `"get-config-output:skills.GetConfigOutput"` line)
- Create `client/src/test/java/io/github/cowwoc/cat/hooks/test/UpdateConfigTest.java` with tests:
  1. `singleKeyUpdate` — write trust=high, verify config.json contains `"trust":"high"`
  2. `multipleKeysAtOnce` — write trust=low caution=high, verify both keys
  3. `unknownKeyReturnsError` — write foo=bar, verify output contains `"status":"ERROR"`
  4. `malformedArgNoEqualsReturnsError` — write "trusthigh" (no `=`), verify error
  5. `invalidEnumValueReturnsError` — write trust=invalid, verify error
  6. `invalidIntegerWidthReturnsError` — write fileWidth=abc, verify error
  7. `outOfRangeWidthReturnsError` — write fileWidth=5, verify error (below 40)
  8. `invalidCompletionWorkflowValueReturnsError` — write completionWorkflow=invalid, verify error
  9. `configFileCreatedIfMissing` — start with no config.json, write trust=medium, verify file created
  10. Include license header
  - Each test must create a temp directory via `Files.createTempDirectory`, create a `TestClaudeTool`
    with that temp dir as the project path, and pass it to `UpdateConfig.run(scope, args, out)`.
    Tests must NOT use the real project directory.
- Run `mvn -f client/pom.xml test` and verify all tests pass
- Commit with message: `feature: add update-config Java CLI tool for atomic config writes`

### Job 2
- Modify `plugin/skills/config/first-use.md`:
  Replace all LLM-driven Read+Write config patterns with `update-config` binary calls.

  **`manual-settings` step** — replace the block:
  ```
  **After collecting all answers from both pages, update config.json:**

  1. Read the current `.cat/config.json` content using the Read tool.
  2. Update all five personality keys with the selected values:
     - `"trust"`: selected trust value
     - `"caution"`: selected caution value
     - `"curiosity"`: selected curiosity value
     - `"perfection"`: selected perfection value
     - `"verbosity"`: selected verbosity value
  3. Write the complete updated JSON back using the Write tool.

  Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.
  ```
  with:
  ```
  **After collecting all answers from both pages, update config.json:**

  ```bash
  "${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" \
    "trust={trust_value}" "caution={caution_value}" "curiosity={curiosity_value}" \
    "perfection={perfection_value}" "verbosity={verbosity_value}"
  ```

  Where `{trust_value}`, `{caution_value}`, etc. are the lowercase values selected above (e.g., "low", "medium", "high").
  If the command outputs `{"status":"ERROR",...}`, display the error message and do not proceed.
  ```

  **`questionnaire` step** — replace the block:
  ```
  **Update config.json with all 5 derived values:**

  1. Read the current `.cat/config.json` content using the Read tool.
  2. Update all five personality keys with derived values:
     - `"trust"`: TRUST value
     - `"caution"`: CAUTION value
     - `"curiosity"`: CURIOSITY value
     - `"perfection"`: PERFECTION value
     - `"verbosity"`: VERBOSITY value
  3. Write the complete updated JSON back using the Write tool.

  Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.
  ```
  with:
  ```
  **Update config.json with all 5 derived values:**

  ```bash
  "${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" \
    "trust={TRUST}" "caution={CAUTION}" "curiosity={CURIOSITY}" \
    "perfection={PERFECTION}" "verbosity={VERBOSITY}"
  ```

  Where each value is the lowercase derived value (e.g., "low", "medium", "high").
  If the command outputs `{"status":"ERROR",...}`, display the error message.
  ```

  **`width-settings` step** — replace two occurrences of:
  ```
  **Update config using the Write tool:**

  1. Read the current `.cat/config.json` content using the Read tool.
  2. Merge the new `fileWidth` or `displayWidth` integer value into the existing config object (update or add the key).
  3. Write the complete updated JSON back using the Write tool.
  ...
  Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.
  ```
  with:
  ```
  **Update config:**

  ```bash
  "${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" "fileWidth={value}"
  ```
  (or `displayWidth={value}` as appropriate). Replace `{value}` with the integer value selected.
  If the command outputs `{"status":"ERROR",...}`, display the error message.
  ```

  **`completion-workflow` step** — replace:
  ```
  **Update config using the Write tool:**

  1. Read the current `.cat/config.json` content using the Read tool.
  2. Merge the new `completionWorkflow` string value into the existing config object (update or add the key).
  3. Write the complete updated JSON back using the Write tool.
  ...
  Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.
  ```
  with:
  ```
  **Update config:**

  ```bash
  "${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" "completionWorkflow={value}"
  ```
  Replace `{value}` with the mapped value ("merge" or "pr").
  If the command outputs `{"status":"ERROR",...}`, display the error message.
  ```

  **`min-severity` step** — replace:
  ```
  **Update config using the Write tool:**

  1. Read the current `.cat/config.json` content using the Read tool.
  2. Merge the new `minSeverity` string value into the existing config object (update or add the key).
  3. Write the complete updated JSON back using the Write tool.
  ...
  Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.
  ```
  with:
  ```
  **Update config:**

  ```bash
  "${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" "minSeverity={value}"
  ```
  Replace `{value}` with the mapped severity ("low", "medium", "high", or "critical").
  If the command outputs `{"status":"ERROR",...}`, display the error message.
  ```

  **`update-config` step** — replace the step body:
  ```
  Use the Read tool to read `.cat/config.json`, modify the target setting value, then use the Write tool to
  write the updated JSON back to the same path.
  ```
  with:
  ```
  Run the update-config binary with the key=value pair:

  ```bash
  "${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" "{key}={value}"
  ```

  Replace `{key}` and `{value}` with the actual setting name and its new lowercase value.
  If the command outputs `{"status":"ERROR",...}`, display the error message and do not proceed.
  ```

- Update `.cat/issues/v2/v2.1/extract-config-ops-to-java/index.json`:
  set `"status": "closed"` and `"progress": 100`
- Commit with message: `feature: update config skill to delegate writes to update-config binary`

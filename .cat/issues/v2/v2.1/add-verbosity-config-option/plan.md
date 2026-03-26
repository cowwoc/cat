# Plan

## Goal

Add a verbosity config option with low/medium/high levels controlling how much CAT explains itself to the
user during task execution.

- **low**: Progress banners and errors only — no reasoning, no summaries beyond phase markers
- **medium**: Phase-transition summaries — what was done, key decisions (current behavior, default)
- **high**: Full reasoning — alternatives considered, tradeoffs noted, rationale for each decision

## Pre-conditions

(none)

## Post-conditions

- [ ] Config wizard presents verbosity option with three levels: Low, Medium (Default), High
- [ ] Verbosity setting stored in config.json under key "verbosity"
- [ ] `get-config-output effective` returns verbosity field with default "medium" when unset
- [ ] VerbosityLevel enum added with values LOW, MEDIUM, HIGH
- [ ] Low verbosity: only progress banners and errors appear; phase summaries suppressed
- [ ] Medium verbosity: phase-transition summaries shown (current behavior unchanged)
- [ ] High verbosity: full reasoning output including alternatives considered and tradeoffs
- [ ] Unit tests for VerbosityLevel enum and config parsing
- [ ] No regressions in existing config option handling
- [ ] E2E (manual): invoke `/cat:config`, navigate to Verbosity, select each level (Low/Medium/High),
  confirm config.json is written with the correct value (`"verbosity": "low"` / `"medium"` / `"high"`)

## Research Findings

Config option pattern (from existing `TrustLevel`, `CautionLevel`, `CuriosityLevel`, `PerfectionLevel` enums):
- Enum in `client/src/main/java/io/github/cowwoc/cat/hooks/util/` with `LOW`, `MEDIUM`, `HIGH` values
- `fromString(String value)` parses case-insensitively via `valueOf(value.toUpperCase(Locale.ROOT))`
- `toString()` returns `name().toLowerCase(Locale.ROOT)` for JSON serialization
- License header required in all new Java files

`Config.java` changes:
- Add `"verbosity": "medium"` to the static `DEFAULTS` map
- Add `getVerbosity()` method returning `VerbosityLevel.fromString(getString("verbosity", "medium"))`
- `getEffectiveConfig()` returns `config.asMap()` which already includes all DEFAULTS fields

`GetConfigOutput.java` changes:
- Add `String verbosity = config.getVerbosity().toString();` to `getCurrentSettings()`
- Add `"  📢 Verbosity: " + verbosity,` to the displayed lines list (after Perfection, before Completion)

`plugin/skills/config/first-use.md` changes:
- Add `🗣️ Verbosity` option to the CAT Behavior step (label + current value description)
- Add a new `<step name="verbosity">` section with Low/Medium/High options
- Map: Low → `verbosity: "low"`, Medium → `verbosity: "medium"`, High → `verbosity: "high"`
- Add update config instructions (same pattern as other options)

`plugin/templates/config.json` — add `"verbosity": "medium"` entry.

Test patterns:
- `ConfigTest.java` uses `new TestClaudeTool()` (no args) with `Config.load(mapper, tempDir)`
- `GetConfigOutputTest.java` uses `new TestClaudeTool(tempDir, tempDir)` with `handler.getCurrentSettings(tempDir)`
- Both create temp dirs, write `.cat/config.json` with content, then verify fields

## Sub-Agent Waves

### Wave 1

- Create `client/src/main/java/io/github/cowwoc/cat/hooks/util/VerbosityLevel.java` — new enum
  following the exact pattern of `CautionLevel.java` (LOW/MEDIUM/HIGH, `fromString`, `toString`,
  Javadoc on class and all members, license header)
- Update `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`:
  - Add `import io.github.cowwoc.cat.hooks.util.VerbosityLevel;`
  - Add `defaults.put("verbosity", "medium");` to the DEFAULTS static initializer (after perfection)
  - Add `getVerbosity()` method after `getPerfection()` — returns `VerbosityLevel.fromString(getString("verbosity", "medium"))`
  - Javadoc on `getVerbosity()`: "Get the verbosity level." with @return and @throws
- Update `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java`:
  - Add `String verbosity = config.getVerbosity().toString();` after the `perfection` variable
  - Add `"  📢 Verbosity: " + verbosity,` to the `List.of(...)` in `getCurrentSettings()`,
    positioned after `"  ✨ Perfection: " + perfection,` and before `"  🔀 Completion: " + completionWorkflow,`
- Update `plugin/templates/config.json` — add `"verbosity": "medium"` after `"perfection": "medium"`
- Update `plugin/skills/config/first-use.md`:
  - In the `<step name="cat-behavior">` section, add `🗣️ Verbosity` option after `⏳ Perfection`
    with description `"Currently: {verbosity || 'medium'}"`
  - Add new `<step name="verbosity">` section after `<step name="perfection">` with:
    ```
    **🗣️ Verbosity — How much CAT explains itself during task execution**

    Display current setting, then AskUserQuestion:
    - header: "Verbosity"
    - question: "How much should CAT explain its reasoning? (Current: {verbosity || 'medium'})"
    - options:
      - label: "Low"
        description: "Progress banners and errors only — no reasoning, no summaries beyond phase markers"
      - label: "Medium (Default)"
        description: "Phase-transition summaries — what was done, key decisions"
      - label: "High"
        description: "Full reasoning — alternatives considered, tradeoffs noted, rationale for each decision"
      - label: "← Back"
        description: "Return to behavior menu"

    Map: Low → `verbosity: "low"`, Medium → `verbosity: "medium"`, High → `verbosity: "high"`

    **Update config using the Write tool:**

    1. Read the current `.cat/config.json` content using the Read tool.
    2. Merge the new `verbosity` string value into the existing config object (update or add the key).
    3. Write the complete updated JSON back using the Write tool.

    Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.
    ```
- Add tests to `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java`:
  - `configUsesDefaultVerbosityWhenConfigMissing()` — verifies default is "medium" when no config file
  - `configReadsVerbosityFromFile()` — verifies `"verbosity": "low"` is read from config.json
  - `configVerbosityParsesToEnum()` — verifies `config.getVerbosity()` returns `VerbosityLevel.LOW`
    when config has `"verbosity": "low"`
  - `configRejectsUnknownVerbosityValue()` — verifies exception on `"verbosity": "bogus"` (uses
    `@Test(expectedExceptions = IllegalArgumentException.class)`)
- Add tests to `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetConfigOutputTest.java`:
  - `getCurrentSettingsIncludesVerbosityDefault()` — verifies settings box contains `"📢 Verbosity: medium"`
    when config has `{}`
  - `getCurrentSettingsShowsVerbosityLow()` — verifies `"📢 Verbosity: low"` when `"verbosity": "low"`
- Run `mvn -f client/pom.xml verify -e` and fix all compilation errors and linter warnings before committing
- Do NOT modify `client/src/test/java/io/github/cowwoc/cat/hooks/test/module-info.java` — the main
  module already exports `io.github.cowwoc.cat.hooks.util` unconditionally (line 34 of
  `client/src/main/java/io/github/cowwoc/cat/hooks/module-info.java`), so `VerbosityLevel` is
  accessible to the test module without any change
- Update `plugin/skills/init/first-use.md`: add `"verbosity": "medium"` to the JSON config example
  that lists `trust`, `caution`, `curiosity`, `perfection` (around line 550) so the example remains
  consistent with the full config schema
- Commit all changes with message: `feature: add verbosity config option with low/medium/high levels`
- Update index.json: set status to "closed", progress to 100%
- Include index.json in the same commit as the implementation

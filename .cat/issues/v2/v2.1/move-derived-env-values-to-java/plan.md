# Plan: move-derived-env-values-to-java

## Goal

Identify environment values used by CAT for Claude and Codex separately, classify which values are runtime-provided
harness values versus CAT-defined values that differ from the harness values, and move CAT-derived values that are only
needed by Java CLI code out of LLM-facing skill parameters and environment injection.

## Parent Issue

`2.1-split-runtime-rules-directories` (decomposed)

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Shared workflow skills and Java launchers rely on environment variables across both runtimes; removing a
  value from skill text too aggressively could break a workflow where the LLM genuinely needs to inspect or pass it.
- **Mitigation:** Inventory Claude and Codex paths separately, preserve LLM-visible values that skills use directly, add
  focused tests around Java-side derivation and runtime-specific CLI invocation behavior, and run the full Maven
  verification suite.

## Files to Modify

- `client/plugin/skills/**` - remove LLM-facing env exports/parameters for Java-only CAT-derived values.
- `client/plugin/agents/**` - remove duplicated Java-only env plumbing if present.
- `client/cli/**` and related Java CLI modules - derive Java-only CAT values from runtime harness values internally.
- `client/distribution/**` - update runtime launcher/env injection only where it injects Java-only CAT-derived values.
- Tests under `client/**/src/test/**` covering both Claude and Codex behavior.

## Pre-conditions

- [ ] Parent issue `2.1-split-runtime-rules-directories` exists and remains in progress.
- [ ] Closed issue records remain unchanged.

## Jobs

### Job 1

- Inventory environment variables currently referenced in `client/plugin/skills/**`, `client/plugin/agents/**`,
  `client/distribution/**`, and Java CLI code for Claude runtime paths.
- Classify each Claude value as:
  - runtime harness-provided,
  - CAT-defined and identical to the harness value,
  - CAT-defined and derived from a harness value,
  - CAT-defined and independent of harness values.
- For each CAT-defined value derived from a Claude harness value, determine whether skill text or agent instructions use
  the value directly, or whether the value is only passed through to a Java CLI.

### Job 2

- Repeat the same inventory and classification for Codex runtime paths.
- Pay special attention to values that were introduced to make shared Java utilities runtime-neutral, including
  `CAT_PROJECT_DIR`, `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`, `CAT_CONFIG_DIR`, `CAT_SESSION_ID`, and `CAT_RUNTIME`.
- Separate values the LLM needs for path construction, command examples, locking/session identity, or user-visible
  workflow decisions from values that Java can derive from existing runtime harness variables.

### Job 3

- Move definitions for Java-only CAT-derived values into Java code or runtime adapter code.
- Remove those values from skill-file command examples, agent prompt plumbing, launcher parameter lists, or environment
  injection sites where the LLM only passed them through to Java.
- Preserve explicit LLM-facing variables where skills need to read, branch on, or interpolate them for non-Java work.
- Keep Claude and Codex behavior separate where runtime harness values differ.

### Job 4

- Add or update regression tests proving representative Claude and Codex Java CLI invocations derive Java-only CAT
  values internally when the runtime harness value is present.
- Add or update tests proving missing truly required values still fail fast with clear errors.
- Run `mvn -f client/pom.xml verify -e`.

## Post-conditions

- [ ] Claude runtime env variables are inventoried and classified into harness-provided versus CAT-defined categories.
- [ ] Codex runtime env variables are inventoried and classified into harness-provided versus CAT-defined categories.
- [ ] CAT-defined values derived from harness values are marked as LLM-needed or Java-only.
- [ ] Java-only CAT-derived values are derived inside Java/runtime adapter code instead of being passed by skills or
  injected into LLM-facing environments.
- [ ] Skill and agent files no longer ask the LLM to pass Java-only derived values.
- [ ] Runtime-specific behavior remains correct for both Claude and Codex.
- [ ] `mvn -f client/pom.xml verify -e` passes.

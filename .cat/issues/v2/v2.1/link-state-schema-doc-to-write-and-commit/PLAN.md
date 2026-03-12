# Plan: link-state-schema-doc-to-write-and-commit

## Goal
When agents use write-and-commit-agent to create STATE.md files, they have no visible reference to the
allowed/deprecated fields schema. This causes agents to write invalid fields (e.g., `Last Updated`) that
StateSchemaValidator then blocks. Add a STATE.md-specific note in the write-and-commit-agent skill to
proactively guide agents to `plugin/concepts/state-schema.md` before writing, preventing silent schema
violations.

## Parent Requirements
None - infrastructure enforcement

## Approaches

### A: Documentation link in skill (Recommended)
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Add a "STATE.md files" subsection to write-and-commit-agent/first-use.md referencing
  state-schema.md. Agents consulting the skill see the note proactively.

### B: Enhance StateSchemaValidator error message
- **Risk:** LOW
- **Scope:** 1 Java file (minimal)
- **Description:** When StateSchemaValidator rejects a deprecated key, include the list of currently-valid
  fields in the error message so the agent can correct immediately on retry.

### C: Both approaches
- **Risk:** LOW
- **Scope:** 2 files (minimal)
- **Description:** Combine proactive documentation guidance with reactive error message enrichment.
  Proactive note prevents the first error; enhanced error message prevents repeat errors if the agent
  misses the proactive note.

> Approach A is the minimum viable fix. Approach C is recommended as defense-in-depth: the proactive note
> guides agents writing new STATE.md files, and the enhanced error message catches any remaining cases.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Adding content to write-and-commit-agent skill increases token weight slightly
- **Mitigation:** Keep the STATE.md section concise (3-5 lines)

## Files to Modify
- `plugin/skills/write-and-commit-agent/first-use.md` - add STATE.md schema reference section
- `client/src/main/java/io/github/cowwoc/cat/hooks/StateSchemaValidator.java` - enhance deprecated-key
  error message to include list of allowed fields from ALLOWED_KEYS

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- In `plugin/skills/write-and-commit-agent/first-use.md`, add a "STATE.md Files" subsection under
  "When to Use This Skill" stating: when writing a STATE.md file, check
  `plugin/concepts/state-schema.md` for the current allowed fields before writing to avoid
  StateSchemaValidator rejections.
  - Files: `plugin/skills/write-and-commit-agent/first-use.md`
- In `StateSchemaValidator.java`, extend the deprecated-key error message to include the list of
  currently-allowed fields (from `ALLOWED_KEYS`) so the agent knows what to use on retry.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/StateSchemaValidator.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/StateSchemaValidatorTest.java`

## Post-conditions
- [ ] write-and-commit-agent first-use.md references plugin/concepts/state-schema.md in a STATE.md section
- [ ] Agents following the skill instructions see the schema reference before writing STATE.md
- [ ] StateSchemaValidator deprecated-key error message includes the list of allowed fields
- [ ] Tests updated to verify error message includes allowed fields
- [ ] E2E: Write a STATE.md with a deprecated field (`Last Updated`) and verify the error message shows
  allowed fields inline

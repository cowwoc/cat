# Plan: redesign-issue-file-structure

## Goal

Rename PLAN.md to plan.md and STATE.md to index.json, converting STATE.md's content to JSON with
structural relationship fields (dependencies, blocks, parent, decomposedInto) moved in alongside
status/resolution/targetBranch. Remove the Progress field entirely. Remove Java code that parses
pre-conditions and post-conditions, having skills instruct the LLM to read plan.md directly instead.
Update all skills, hooks, templates, and Java code to use the new filenames and formats. Add an
idempotent migration script.

## Parent Requirements

None

## Supersedes

`2.1-move-dependencies-to-plan-md` — that issue moved dependencies to PLAN.md; this issue
instead moves them to index.json and renames the files. Close `2.1-move-dependencies-to-plan-md`
as obsolete before or during this work.

## Risk Assessment

- **Risk Level:** HIGH
- **Concerns:**
  - Every issue in the project has PLAN.md and STATE.md files; migration must be idempotent and
    correct for all of them
  - Many Java classes, hooks, skills, and templates reference PLAN.md/STATE.md by name
  - Removing IssueGoalReader affects the Java status display
  - StateSchemaValidator must be rewritten for JSON validation
- **Mitigation:**
  - Write migration script first, run on a copy to verify before applying
  - Grep exhaustively for all PLAN.md/STATE.md references before closing the issue
  - Keep IssueGoalReader as a thin regex reader (one line after `## Goal`) for status display;
    do not pass it through the LLM

## Files to Modify

- `plugin/migrations/2.1.sh` — add migration phases to rename files and reformat state
- `plugin/templates/issue-state.md` — delete (replaced by issue-index.json template)
- `plugin/templates/issue-index.json` — create (new template)
- `plugin/templates/issue-plan.md` — rename to issue-plan-template.md or update references
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java` — rewrite for JSON validation of index.json
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/VerifyStateInCommit.java` — update filename references
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueGoalReader.java` — simplify or remove
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` — update to read index.json instead of STATE.md
- All skill files referencing PLAN.md, STATE.md, or post-condition/pre-condition parsing logic
- All concept and rules files referencing PLAN.md or STATE.md

## Pre-conditions

- [ ] All existing issues with STATE.md and PLAN.md files are identified
- [ ] All Java, skill, and script files that reference PLAN.md or STATE.md by name are identified
- [ ] `2.1-move-dependencies-to-plan-md` is closed as obsolete

## Post-conditions

- [ ] Every issue directory contains `index.json` (not STATE.md) and `plan.md` (not PLAN.md)
- [ ] `index.json` is valid JSON with fields: status, and conditionally resolution, targetBranch,
  dependencies, blocks, parent, decomposedInto — no Progress field
- [ ] No `STATE.md` or `PLAN.md` files remain in `.cat/issues/`
- [ ] `StateSchemaValidator` validates `index.json` as JSON (not markdown key-value pairs)
- [ ] `IssueDiscovery` reads `index.json` instead of `STATE.md`
- [ ] Skills that previously instructed parsing of pre-conditions/post-conditions now instruct
  the LLM to read `plan.md` directly
- [ ] No Java code remains that programmatically extracts pre-condition or post-condition text
  from plan.md (beyond simple goal extraction for status display)
- [ ] Migration script in `plugin/migrations/2.1.sh` is idempotent
- [ ] All tests pass: `mvn -f client/pom.xml test`
- [ ] E2E: Run `/cat:add`, `/cat:work`, and `/cat:status` against the new format and confirm
  correct behavior

## Sub-Agent Waves

### Wave 1 — Audit

1. Grep for all references to `PLAN.md`, `STATE.md`, `plan.md`, `state.md` in:
   - `plugin/` (skills, concepts, rules, templates, hooks)
   - `client/src/` (Java source)
   - `.claude/` (project rules)
2. Compile a complete list of files to update
3. Close `2.1-move-dependencies-to-plan-md` as obsolete

### Wave 2 — Migration Script

1. Add new phases to `plugin/migrations/2.1.sh`:
   - Rename all `PLAN.md` → `plan.md` under `.cat/issues/`
   - Rename all `STATE.md` → `index.json` under `.cat/issues/`, converting content to JSON
   - Strip Progress field from converted JSON
   - Move dependencies, blocks, parent, decomposedInto from STATE.md key-value format into JSON
2. Run migration against a test copy to verify
3. Verify script is idempotent (run twice, second run is a no-op)

### Wave 3 — Java Updates

1. Rewrite `StateSchemaValidator` to parse and validate `index.json` as JSON
2. Update `IssueDiscovery` to read `index.json`
3. Update `VerifyStateInCommit` filename references
4. Simplify `IssueGoalReader` if needed (keep as regex reader for status display)
5. Run all tests: `mvn -f client/pom.xml test`

### Wave 4 — Skills and Templates

1. Update all skill files: replace PLAN.md references with plan.md, STATE.md with index.json
2. Update skills that instruct LLM to parse pre-conditions/post-conditions: replace extraction
   instructions with "read plan.md directly and find the Pre-conditions / Post-conditions sections"
3. Delete `plugin/templates/issue-state.md`, create `plugin/templates/issue-index.json`
4. Update all other template and concept files

### Wave 5 — Apply Migration and Verify

1. Run `plugin/migrations/2.1.sh` against the actual `.cat/issues/` directory
2. Verify spot-check: 5 random issues have correct index.json and plan.md
3. E2E test: `/cat:status`, `/cat:work`, `/cat:add` all function correctly
4. Final test run: `mvn -f client/pom.xml test`

### Wave 6 — Fix Remaining Unmigrated Issues

1. Manually convert `.cat/issues/v2/v2.1/add-criteria-verification-gate/PLAN.md` to `plan.md` by
   renaming the file (git mv) in the worktree, then delete the original `PLAN.md`
2. Manually convert `.cat/issues/v2/v2.1/add-criteria-verification-gate/STATE.md` to `index.json`
   by reading its key-value content, reformatting as JSON with the correct schema fields (status,
   and conditionally resolution, targetBranch, dependencies, blocks, parent, decomposedInto —
   no Progress field), writing `index.json`, and deleting `STATE.md`
3. Manually convert `.cat/issues/v2/v2.1/rename-skill-builder-to-instruction-builder/PLAN.md` to
   `plan.md` using git mv, then delete the original `PLAN.md`
4. Manually convert `.cat/issues/v2/v2.1/rename-skill-builder-to-instruction-builder/STATE.md` to
   `index.json` by reading its key-value content, reformatting as JSON with the correct schema
   fields, writing `index.json`, and deleting `STATE.md`
5. Verify no `PLAN.md` or `STATE.md` files remain under `.cat/issues/`:
   `find .cat/issues -name PLAN.md -o -name STATE.md` must return empty output
6. Verify both converted directories now contain `plan.md` and `index.json` with valid JSON schemas

### Wave 7 — Remove Post-condition Extraction from VerifyAudit

1. Refactor `client/src/main/java/io/github/cowwoc/cat/hooks/skills/VerifyAudit.java` to remove
   programmatic post-condition extraction: delete the `extractPostconditions()` method, the `parse()`
   method, and any code in `prepare()` that calls `extractPostconditions()` to build the `criteria`
   list or `groups`. Remove the `CriteriaGroup` record and methods that depend on it
   (`groupCriteriaByFiles()`, `extractFileReferences()`, `buildGroupNode()`, `buildPromptForGroup()`).
   Remove unused imports. Keep `extractFileSpecs()`, `verifyFilesInternal()`, `report()`, and all
   rendering helpers — these do not parse post-conditions.
2. Update the `prepare` subcommand in `VerifyAudit.main()` and `VerifyAudit.prepare()` to return only
   `issue_id`, `issue_path`, `worktree_path`, and `file_results` (file verification). Remove
   `criteria_count`, `file_count`, and `prompts` from the returned JSON since the LLM will read
   plan.md directly.
3. Update `plugin/skills/verify-implementation-agent/first-use.md`: replace the "prepare" step that
   calls `verify-audit prepare` and spawns subagents from its `prompts` output with instructions for
   the agent to (a) run `verify-audit prepare` to get `file_results` only, (b) read `plan.md` directly
   using the Read tool to find the `## Post-conditions` section, (c) extract the post-condition items
   by reading the markdown, and (d) verify each criterion by reading the relevant source files. The
   agent must not rely on Java to parse post-conditions — it reads plan.md directly.
4. Run all tests: `mvn -f client/pom.xml test` — all must pass.

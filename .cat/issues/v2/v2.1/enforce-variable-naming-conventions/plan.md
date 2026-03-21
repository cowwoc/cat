# Plan: enforce-variable-naming-conventions

## Current State

No naming convention exists specifying casing for variable names in Markdown vs Java files. This causes inconsistency:
- Skill files (MD) use camelCase parameter names (e.g., `catAgentId`, `issueId`, `worktreePath`) when snake_case is
  the appropriate convention for Markdown-embedded variable references
- Java source files already use camelCase by Java language convention, but this is not formally documented

## Target State

A project convention formally specifies:
- Variable names referenced in Markdown (MD) files use **snake_case** (e.g., `cat_agent_id`, `issue_id`,
  `worktree_path`)
- Variable names in Java source files use **camelCase** (e.g., `catAgentId`, `issueId`, `worktreePath`)

The convention is added to `.claude/rules/common.md` and all existing MD and Java files are audited and updated to
comply.

## Satisfies

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Renaming parameter names in skill files may require updates to any skill that references those
  parameters by name
- **Mitigation:** Search exhaustively for all usages of renamed variables before committing; update all call sites

## Files to Modify

- `.claude/rules/common.md` — Add naming convention section specifying snake_case for MD, camelCase for Java
- `plugin/skills/**/*.md` — Update any variable/parameter names from camelCase to snake_case
- `plugin/agents/**/*.md` — Update any variable/parameter names from camelCase to snake_case
- `plugin/concepts/**/*.md` — Update any variable/parameter names from camelCase to snake_case
- `plugin/rules/**/*.md` — Update any variable/parameter names from camelCase to snake_case
- `.claude/rules/**/*.md` — Update any variable/parameter names from camelCase to snake_case
- `client/src/main/java/**/*.java` — Verify camelCase compliance (likely already compliant)

## Post-conditions

- [ ] `.claude/rules/common.md` documents the snake_case (MD) and camelCase (Java) naming convention
- [ ] All variable/parameter names in MD files use snake_case
- [ ] All variable names in Java files use camelCase
- [ ] All tests pass
- [ ] No regressions introduced

## Sub-Agent Waves

### Wave 1

1. **Step 1:** Add naming convention to `.claude/rules/common.md`
   - Add a "Naming Conventions" section specifying snake_case for MD variables and camelCase for Java variables
   - Include examples of correct and incorrect usage
   - Files: `.claude/rules/common.md`

2. **Step 2:** Audit and update Markdown files
   - Search all `.md` files in `plugin/` and `.claude/` for camelCase variable/parameter names
   - Rename to snake_case where found (e.g., `catAgentId` → `cat_agent_id`, `issueId` → `issue_id`,
     `worktreePath` → `worktree_path`, `targetBranch` → `target_branch`)
   - Update all references to renamed variables in the same files and any other files that use them
   - Files: all `.md` files in `plugin/` and `.claude/`

3. **Step 3:** Audit Java files for camelCase compliance
   - Verify all local variable names in Java source files use camelCase
   - Fix any violations found
   - Files: `client/src/main/java/**/*.java`

4. **Step 4:** Run tests and commit
   - Run `mvn -f client/pom.xml test` to verify no regressions
   - Commit convention update and file changes

5. **Step 5:** Explicitly enumerate snake_case exemptions in `.claude/rules/common.md`
   - Extend the "Variable Names in Markdown Files" section in `.claude/rules/common.md` to list the exact categories
     of camelCase identifiers that are exempt from the snake_case rule: (a) JSON output contract field names
     referenced inline in skill instructions (e.g., `issueId`, `worktreePath`, `targetBranch` when describing a
     Java tool's JSON output), (b) schema documentation field names in `state-schema.md` and `templates/state.md`,
     (c) Java code examples embedded in Markdown (e.g., in `java.md` and any `first-use.md` code blocks)
   - Add at least one concrete "Exempt (JSON field reference)" example alongside the existing Correct/Incorrect
     examples so the distinction is unambiguous
   - Files: `.claude/rules/common.md`

6. **Step 6:** Fix the wrong "JSON Field Names" convention in `.claude/rules/common.md`
   - Remove the "JSON Field Names" subsection that incorrectly states JSON field names use camelCase
   - Replace it with a subsection stating that JSON output from Java tools uses **snake_case**
     (e.g., `issue_id`, `worktree_path`, `target_branch`) — consistent with the Configuration Reads table
     already present in the file which correctly shows `target_branch` and `issue_id`
   - Files: `.claude/rules/common.md`

7. **Step 7:** Convert Java tool JSON output fields from camelCase to snake_case
   - Search `client/src/main/java/` for all Java classes/records serialized to JSON (POJO output contracts)
   - Add `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` on each output-contract class, OR add
     individual `@JsonProperty("snake_case_name")` annotations to each field (e.g., `issueId` → `"issue_id"`,
     `issuePath` → `"issue_path"`, `worktreePath` → `"worktree_path"`, `targetBranch` → `"target_branch"`)
   - Key files to check: any class whose instances are serialized via `JsonMapper` and emitted to stdout by a
     skill CLI tool (e.g., `WorkPrepare` output, `GetStatusOutput` output, etc.)
   - Files: `client/src/main/java/**/*.java`

8. **Step 8:** Update skill MD files to use snake_case JSON field references
   - Search all `plugin/skills/**/*.md` for JSON field name references using camelCase
     (e.g., `issueId`, `issuePath`, `worktreePath`, `targetBranch`)
   - Update each reference to the corresponding snake_case name
     (e.g., `issue_id`, `issue_path`, `worktree_path`, `target_branch`)
   - Key files to check: any `first-use.md` that parses or describes JSON output from Java CLI tools,
     including `work-prepare-agent/first-use.md`, `cleanup/first-use.md`, and similar
   - Files: `plugin/skills/**/*.md`

9. **Step 9:** Update tests that assert camelCase JSON field names
   - Search `client/src/test/java/` for test assertions referencing JSON field names in camelCase
   - Update assertions to expect snake_case field names after Step 7 changes
   - Run `mvn -f client/pom.xml test` to confirm all tests pass
   - Files: `client/src/test/java/**/*.java`

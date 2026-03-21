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

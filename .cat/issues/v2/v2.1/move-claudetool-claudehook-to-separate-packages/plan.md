# Plan

## Goal

Move all classes that use `ClaudeTool` to `io.github.cowwoc.cat.claude.tool` package and all classes that use
`ClaudeHook` to `io.github.cowwoc.cat.claude.hook` package. This separates the two execution contexts (hooks vs tools)
at the package level, making the architecture more explicit.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Package restructuring affects all imports in Java code; external tools/documentation
  referencing old paths will need updates
- **Mitigation:** Comprehensive grep verification after moves; full test suite run

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/` — reorganize into new package structure
- All `.java` files with imports of `io.github.cowwoc.cat.hooks.*` or classes being moved
- `client/src/main/java/module-info.java` — update package exports
- `.claude/rules/` files with `paths:` frontmatter referencing hooks package paths

## Pre-conditions

- [ ] Issue `2.1-extract-plugin-root-from-jvmscope` is closed (scope hierarchy refactored)

## Jobs

### Job 1: Identify tool-related classes

- Enumerate all classes in `io.github.cowwoc.cat.hooks.tool` (current location)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/tool/`
- Enumerate root-level classes that belong in `claude.tool` execution context
  (`ClaudeTool`, `AbstractClaudeTool`, `MainClaudeTool`, and any class accepting only `ClaudeTool`)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/`

### Job 2: Move tool-related classes to `io.github.cowwoc.cat.claude.tool`

- Create `client/src/main/java/io/github/cowwoc/cat/claude/tool/` structure
- Move identified tool classes, preserving subpackage hierarchy (e.g., `hooks/tool/post/` → `claude/tool/post/`)
- Update `package` declarations in moved files

### Job 3: Move hook-related classes to `io.github.cowwoc.cat.claude.hook`

- Create `client/src/main/java/io/github/cowwoc/cat/claude/hook/` structure
- Move all remaining classes from `io.github.cowwoc.cat.hooks`, preserving subpackage hierarchy
  (e.g., `hooks/ask/` → `claude/hook/ask/`)
- Update `package` declarations in moved files

### Job 4: Update all Java imports

- Update all import statements across `client/src/main/java/` to reference new package names
  - Old: `import io.github.cowwoc.cat.hooks.*`
  - New: `import io.github.cowwoc.cat.claude.tool.*` or `import io.github.cowwoc.cat.claude.hook.*`
- Update `module-info.java` to export `io.github.cowwoc.cat.claude.tool`, `io.github.cowwoc.cat.claude.hook`,
  and all relevant subpackages

### Job 5: Update external references and rules

- Search `.claude/rules/` files for any `paths:` frontmatter or instructions referencing old hook-related paths
  (e.g., paths that include `hooks/` in Java source references)
  - Files: `.claude/rules/hooks.md` and any other rule files mentioning hook package paths
- Update references to point to new package locations if documentation includes Java source path examples

### Job 6: Update documentation examples in java.md

The post-condition "Grep for `io.github.cowwoc.cat.hooks` returns zero matches" is PARTIAL because
`.claude/rules/java.md` contains example code snippets in the Module Naming section that still reference
old package names. The module name `io.github.cowwoc.cat.hooks` in `module-info.java` and the test module
`io.github.cowwoc.cat.hooks.test` are intentional and correct by design (module naming follows Java conventions
independent of package structure), but documentation examples should be updated for consistency.

- Open `.claude/rules/java.md` and locate the Module Naming section
- Update all code example snippets that reference `io.github.cowwoc.cat.hooks` to instead show
  `io.github.cowwoc.cat.claude.hook` (or `io.github.cowwoc.cat.claude.tool` where appropriate)
- Verify the updated examples accurately illustrate the new package structure
- Note: Do NOT change the actual module name in `module-info.java` — only documentation examples

### Job 7: Comprehensive verification

- Run grep to verify no reference to `io.github.cowwoc.cat.hooks` remains outside of:
  - `module-info.java` (module name declaration — intentional)
  - Test module declarations (e.g., `io.github.cowwoc.cat.hooks.test` — intentional by convention)
- Run `mvn -f client/pom.xml verify -e` and confirm exit code 0

## Post-conditions

- [ ] All classes originally in `io.github.cowwoc.cat.hooks.tool.*` are now in `io.github.cowwoc.cat.claude.tool.*`
- [ ] All remaining hook classes are in `io.github.cowwoc.cat.claude.hook.*` (preserving subpackages)
- [ ] No class remains under `io.github.cowwoc.cat.hooks`
- [ ] All Java imports across codebase reference only new package names
- [ ] `module-info.java` exports `io.github.cowwoc.cat.claude.tool`, `io.github.cowwoc.cat.claude.hook`,
      and subpackages (no exports of old `hooks` package)
- [ ] `paths:` frontmatter in `.claude/rules/` files updated to reflect new package structure where applicable
- [ ] Grep for `io.github.cowwoc.cat.hooks` returns zero matches
- [ ] Tests pass: `mvn -f client/pom.xml verify -e` exit code 0

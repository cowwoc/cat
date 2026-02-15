# Plan: Rename engine/ to client/

## Current State
The Java module directory is named `engine/` but the Maven artifactId is already `client` and the project name is
"CAT Client".

## Target State
Rename the `engine/` directory to `client/` and update all references throughout the codebase.

## Satisfies
None - naming consistency cleanup

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Directory path changes in build commands, Java source references, documentation
- **Mitigation:** Grep for all "engine/" references and update them; run tests after rename

## Files to Modify
- `engine/` → `client/` (git mv)
- `CLAUDE.md` - update `mvn -f engine/pom.xml test` → `mvn -f client/pom.xml test`
- `engine/build.sh` - update comment referencing "engine"
- `engine/build-jlink.sh` - update comments referencing "engine"
- `engine/src/**/WarnBaseBranchEdit.java` - update path references
- `engine/src/**/VerifyCommitType.java` - update `"engine/"` string
- `engine/src/**/ValidateCommitType.java` - update `"engine/"` string
- `engine/src/**/HookEntryPointTest.java` - update path references
- Various `.md` files with `engine/` references

## Acceptance Criteria
- [ ] Directory renamed from `engine/` to `client/`
- [ ] All references to `engine/` updated to `client/`
- [ ] `mvn -f client/pom.xml test` passes
- [ ] No remaining references to `engine/` in active code (planning docs excluded)

## Execution Steps
1. **Step 1:** `git mv engine client`
2. **Step 2:** Update all `engine/` references to `client/` in Java source files, shell scripts, CLAUDE.md, and
   active documentation
3. **Step 3:** Run `mvn -f client/pom.xml test` to verify

## Success Criteria
- [ ] All tests pass with `mvn -f client/pom.xml test`
- [ ] `grep -r "engine/" --include="*.java" --include="*.sh" --include="*.md" client/` returns no stale references

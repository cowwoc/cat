# Plan: move-java-out-of-plugin

## Current State
Java hooks source lives at `hooks/`. The entire Java project (source, tests, target/) gets copied into the
plugin cache on every plugin install, bloating the cache with build artifacts end-users don't need.

## Target State
Java source moved to `/workspace/hooks/` (outside the plugin directory). All references updated. Build and tests work
from the new location.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** All paths referencing `hooks/` must be updated
- **Mitigation:** Comprehensive grep for all references; existing tests validate build

## Files to Modify

### Move Java source
- `hooks/` → `hooks/` (git mv the entire directory)

### Update active references
- `CLAUDE.md` line 46 - Update `mvn -f hooks/pom.xml test` → `mvn -f hooks/pom.xml test`
- `.claude/cat/conventions/java.md` - Update `cd plugin/hooks/java` → `cd hooks`
- `plugin/skills/delegate/content.md` line 302 - Update mvn compile path
- `.claude/skills/build-hooks/SKILL.md` - Update Maven path and JAR copy source

### Update issue PLAN.md references
These files contain `hooks/` paths. Update to `hooks/`:
- `.claude/cat/issues/v2/v2.1/unify-project-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/unify-sessionstart-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/unify-stop-sessionend-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/unify-pretooluse-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/unify-posttooluse-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/unify-userpromptsubmit-hooks/PLAN.md`
- `.claude/cat/issues/v2/v2.1/ci-build-jlink-bundle/PLAN.md`
- `.claude/cat/issues/v2/v2.1/developer-local-bundle-rebuild/PLAN.md`
- `.claude/cat/issues/v2/v2.1/migrate-python-tests/PLAN.md`
- `.claude/cat/issues/v2/v2.1/migrate-token-counting/PLAN.md`
- `.claude/cat/issues/v2/v2.1/migrate-python-to-java/PLAN.md`
- `.claude/cat/issues/v2/v2.1/mavenize-java-hooks/PLAN.md`

## Execution Steps

1. **Step 1: Move Java project directory**
   - Run: `git mv plugin/hooks/java hooks`
   - Verify: `ls hooks/pom.xml` exists

2. **Step 2: Update direct references in active files**
   - Files: `CLAUDE.md`, `.claude/cat/conventions/java.md`, `plugin/skills/delegate/content.md`,
     `.claude/skills/build-hooks/SKILL.md`
   - Replace `plugin/hooks/java` with `hooks` in all paths

3. **Step 3: Update issue PLAN.md references**
   - Files: All 12 issue PLAN.md files listed above
   - Replace `hooks/` with `hooks/` throughout

4. **Step 4: Run tests**
   - `mvn -f hooks/pom.xml verify`
   - All tests must pass

## Success Criteria
- [ ] `hooks/` directory no longer exists
- [ ] `hooks/pom.xml` exists and `mvn -f hooks/pom.xml verify` passes all tests
- [ ] No remaining references to `plugin/hooks/java` in active files

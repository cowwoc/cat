# Plan: centralize-version-path-resolution

## Goal
Centralize version path resolution to support flexible versioning schemes (major only, major+minor,
or major+minor+patch). Currently, multiple files hardcode assumptions about `v$MAJOR/v$MAJOR.$MINOR`
directory structure.

## Satisfies
None - infrastructure/refactor improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Must maintain backward compatibility with existing projects
- **Mitigation:** Create shared reference that all commands/skills include

## Files to Modify
- plugin/concepts/version-paths.md (NEW) - Centralized path resolution logic
- plugin/commands/add.md - Replace 25+ hardcoded paths
- plugin/commands/remove.md - Replace 15+ hardcoded paths
- plugin/commands/work.md - Replace 6 hardcoded paths
- plugin/commands/research.md - Replace 2 hardcoded paths
- plugin/skills/decompose-task/SKILL.md - Replace 1 hardcoded path
- plugin/.claude/cat/workflows/work.md - Replace 2 hardcoded paths
- plugin/hooks/skill_handlers/status_handler.py - Replace 2 hardcoded paths

## Execution Steps
1. **Step 1:** Create version-paths.md reference
   - Files: plugin/concepts/version-paths.md (NEW)
   - Define path resolution functions that handle:
     - Major only: v$MAJOR/
     - Major+Minor: v$MAJOR/v$MAJOR.$MINOR/
     - Major+Minor+Patch: v$MAJOR/v$MAJOR.$MINOR/v$MAJOR.$MINOR.$PATCH/
   - Include detection logic for which scheme a project uses
   - Verify: Reference file exists with complete logic

2. **Step 2:** Update add.md to use centralized paths
   - Files: plugin/commands/add.md
   - Add @concepts/version-paths.md include
   - Replace all hardcoded v$MAJOR/v$MAJOR.$MINOR with centralized resolution
   - Verify: add.md works with all versioning schemes

3. **Step 3:** Update remove.md to use centralized paths
   - Files: plugin/commands/remove.md
   - Replace hardcoded paths with centralized resolution
   - Verify: remove.md works with all versioning schemes

4. **Step 4:** Update remaining commands
   - Files: work.md, research.md
   - Replace hardcoded paths with centralized resolution
   - Verify: Commands work with all versioning schemes

5. **Step 5:** Update skills and workflows
   - Files: decompose-task/SKILL.md, workflows/work.md
   - Replace hardcoded paths with centralized resolution
   - Verify: Skills work with all versioning schemes

6. **Step 6:** Update Python handlers
   - Files: status_handler.py
   - Add path resolution logic or reference shared constants
   - Verify: All 84 tests still pass

## Acceptance Criteria
- [ ] Centralized version-paths.md reference created
- [ ] All commands use centralized path resolution
- [ ] All skills use centralized path resolution
- [ ] Python handlers use consistent path logic
- [ ] Works with major-only versioning (v1/, v2/)
- [ ] Works with major+minor versioning (v1/v1.0/, v1/v1.1/)
- [ ] Works with major+minor+patch versioning (v1/v1.0/v1.0.1/)
- [ ] All tests pass
- [ ] Backward compatible with existing projects

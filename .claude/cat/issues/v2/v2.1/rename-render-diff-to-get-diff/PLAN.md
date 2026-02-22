# Plan: rename-render-diff-to-get-diff

## Goal
Rename the `render-diff` skill to `get-diff` for consistency with the `get-` prefix convention used by other output
skills (e.g., `get-render-diff.sh`).

## Satisfies
- None (naming consistency improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Other pending issues reference `render-diff` by name; those PLANs will be stale after rename
- **Mitigation:** Rename only active code/config; pending issue PLANs reference old names and will be updated when
  worked on

## Files to Modify
- `plugin/skills/render-diff/` → rename directory to `plugin/skills/get-diff/`
- `plugin/skills/render-diff/SKILL.md` → update internal references
- `plugin/skills/render-diff/first-use.md` → update internal references
- `plugin/scripts/get-render-diff.sh` → update skill name references
- `plugin/skills/work-with-issue/first-use.md` → update `render-diff` references
- `plugin/hooks/README.md` → update `render-diff` references

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Rename skill directory:** `mv plugin/skills/render-diff plugin/skills/get-diff`
   - Files: `plugin/skills/render-diff/`
2. **Update SKILL.md:** Replace all `render-diff` references with `get-diff` inside the renamed skill
   - Files: `plugin/skills/get-diff/SKILL.md`
3. **Update first-use.md:** Replace all `render-diff` references with `get-diff`
   - Files: `plugin/skills/get-diff/first-use.md`
4. **Update get-render-diff.sh:** Replace skill name references from `render-diff` to `get-diff`
   - Files: `plugin/scripts/get-render-diff.sh`
5. **Update work-with-issue references:** Replace `render-diff` with `get-diff`
   - Files: `plugin/skills/work-with-issue/first-use.md`
6. **Update hooks README:** Replace `render-diff` references with `get-diff`
   - Files: `plugin/hooks/README.md`
7. **Search for remaining references:** Grep the full `plugin/` tree for any remaining `render-diff` references and
   update them
8. **Run tests:** `mvn -f client/pom.xml test` to verify no regressions

## Post-conditions
- [ ] `plugin/skills/render-diff/` directory no longer exists
- [ ] `plugin/skills/get-diff/` directory exists with SKILL.md and first-use.md
- [ ] `grep -r "render-diff" plugin/` returns zero matches in active code (scripts, skills, hooks)
- [ ] `/cat:get-diff` skill invocation works correctly
- [ ] All tests pass

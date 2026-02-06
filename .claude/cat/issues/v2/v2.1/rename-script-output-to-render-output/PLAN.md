# Plan: rename-script-output-to-render-output

## Current State
The output passthrough skill is named `script-output` in `plugin/skills/script-output/`. It lacks a
`user-invocable: false` marker. No naming convention distinguishes output passthrough skills from
user-invocable or other internal skills.

## Target State
Skill renamed to `render-output` in `plugin/skills/render-output/`. Marked `user-invocable: false`.
All references updated. The `render-*` prefix is established as the naming convention for output
passthrough skills.

## Satisfies
None - naming convention/refactoring

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Skills referencing `script-output` by name must be updated
- **Mitigation:** Grep for all references before and after; only 4 skill files reference it

## Files to Modify

| File | Change |
|------|--------|
| `plugin/skills/script-output/SKILL.md` | Rename directory to `render-output`, add `user-invocable: false` |
| `plugin/skills/status/SKILL.md` | Update `script-output` reference to `render-output` |
| `plugin/skills/help/SKILL.md` | Update `script-output` reference to `render-output` |
| `plugin/skills/render-diff/SKILL.md` | Update `script-output` reference to `render-output` |
| `plugin/skills/token-report/SKILL.md` | Update `script-output` reference to `render-output` |

## Acceptance Criteria
- [ ] Behavior unchanged - all output passthrough skills still work
- [ ] Tests passing
- [ ] No remaining references to `script-output` in plugin/skills/

## Execution Steps

1. **Step 1:** Rename skill directory
   - `git mv plugin/skills/script-output plugin/skills/render-output`

2. **Step 2:** Update SKILL.md frontmatter
   - File: `plugin/skills/render-output/SKILL.md`
   - Add `user-invocable: false` to frontmatter

3. **Step 3:** Update all references in skill files
   - Files: `plugin/skills/status/SKILL.md`, `plugin/skills/help/SKILL.md`,
     `plugin/skills/render-diff/SKILL.md`, `plugin/skills/token-report/SKILL.md`
   - Replace `script-output` with `render-output` in skill invocation instructions

4. **Step 4:** Verify no remaining references
   - Run: `grep -r "script-output" plugin/skills/` should return no results

5. **Step 5:** Run tests
   - `python3 /workspace/run_tests.py`

6. **Step 6:** Commit changes
   - Commit type: `config:` (Claude-facing skill rename)

## Success Criteria
- [ ] `grep -r "script-output" plugin/skills/` returns no results
- [ ] All 4 referencing skills invoke `render-output` instead of `script-output`
- [ ] `render-output/SKILL.md` has `user-invocable: false` in frontmatter
- [ ] All tests pass

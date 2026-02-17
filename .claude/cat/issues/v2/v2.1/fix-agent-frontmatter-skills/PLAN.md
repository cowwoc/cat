# Plan: Fix Agent Frontmatter Skill References

## Goal
Update agent `skills:` frontmatter to use `-first-use` suffix per the documented convention in skill-loading.md. Also
research whether the `cat:` prefix is required in frontmatter and update documentation accordingly to prevent future
mistakes.

## Satisfies
None (convention compliance)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Changing skill references could break subagent skill injection if names don't resolve
- **Mitigation:** Test that skills resolve correctly after changes

## Research Required
- Verify whether `cat:` prefix is needed in `skills:` frontmatter (check Claude Code plugin skill resolution)
- Update skill-loading.md documentation based on findings

## Files to Modify
- `plugin/agents/work-merge.md` - Update skill references to use `-first-use` suffix (and prefix if needed)
- `plugin/agents/work-execute.md` - Update skill references to use `-first-use` suffix (and prefix if needed)
- `plugin/concepts/skill-loading.md` - Update documentation: remove stale `<skill>` tag references, clarify prefix
  requirement, update examples to match current conventions

## Acceptance Criteria
- [ ] All agent frontmatter `skills:` entries use `-first-use` suffix
- [ ] Prefix requirement is researched and documented
- [ ] skill-loading.md reflects current `<output>` tag pattern (no `<skill>` tag references)
- [ ] skill-loading.md examples match actual agent frontmatter

## Execution Steps
1. **Research prefix requirement:** Test whether `cat:` prefix is required for plugin skill resolution in frontmatter
2. **Update work-merge.md:** Change `git-merge-linear` → `{prefix}git-merge-linear-first-use`, same for
   `validate-git-safety`
3. **Update work-execute.md:** Change `tdd-implementation` → `{prefix}tdd-implementation-first-use`, same for
   `git-commit` and `write-and-commit`
4. **Update skill-loading.md:** Remove `<skill>` tag documentation, update `-first-use` pattern to show `<output>` as
   sole delimiter, clarify prefix requirement, update examples
5. **Verify:** Confirm skill names resolve correctly

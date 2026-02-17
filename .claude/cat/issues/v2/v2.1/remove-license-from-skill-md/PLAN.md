# Plan: Remove License Banners from SKILL.md Files

## Goal
Remove HTML license comment banners from all SKILL.md files under `plugin/skills/`. These banners consume context
tokens unnecessarily since SKILL.md content is injected into agent context. License headers belong on source code files,
not on agent-facing documentation that gets loaded into prompts.

## Satisfies
None (token optimization)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None - removing comments has no functional impact
- **Mitigation:** Grep to verify no banners remain after removal

## Files to Modify
- All `plugin/skills/**/SKILL.md` files containing the license banner

## Acceptance Criteria
- [ ] No SKILL.md files under `plugin/skills/` contain the HTML license comment banner
- [ ] CLAUDE.md license header rule updated to exempt SKILL.md files (or note the exemption)

## Execution Steps
1. **Find all affected files:** Grep for license banners in `plugin/skills/**/SKILL.md`
2. **Remove banners:** Delete the HTML comment block from each file
3. **Verify:** Confirm no banners remain
4. **Update documentation:** Note SKILL.md exemption in license header conventions if needed

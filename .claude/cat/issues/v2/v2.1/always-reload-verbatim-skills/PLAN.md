# Plan: always-reload-verbatim-skills

## Goal
Ensure verbatim output skills (status, help, token-report, render-diff) always serve their content.md on every
invocation, preventing LLM from summarizing instead of outputting SCRIPT OUTPUT verbatim.

## Satisfies
- M473: Verbatim output skills must never be summarized on repeated invocations

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None significant - the content.md files for verbatim skills are tiny (< 20 lines)
- **Mitigation:** Token cost of re-serving content.md is negligible

## Root Cause
`load-skill.sh` uses a session marker file to detect repeated invocations. On second+ invocation, it serves the
generic `reference.md` ("find the previously loaded skill definition above") instead of `content.md`. For verbatim
output skills, the LLM interprets this as permission to summarize rather than re-executing the verbatim output
instructions.

## Approach
Add a `always-reload: true` frontmatter field to SKILL.md for verbatim output skills. Modify `load-skill.sh` to check
for this field and always serve content.md when present.

## Files to Modify
- `plugin/scripts/load-skill.sh` - Check for always-reload frontmatter; skip marker-based caching for those skills
- `plugin/skills/status/SKILL.md` - Add `always-reload: true`
- `plugin/skills/help/SKILL.md` - Add `always-reload: true`
- `plugin/skills/token-report/SKILL.md` - Add `always-reload: true`
- `plugin/skills/render-diff/SKILL.md` - Add `always-reload: true`

## Acceptance Criteria
- [ ] Verbatim output skills serve content.md on every invocation (not just first)
- [ ] Non-verbatim skills continue to use reference.md on repeated invocations (no regression)
- [ ] SKILL.md frontmatter includes `always-reload: true` for the 4 verbatim skills
- [ ] `load-skill.sh` reads the always-reload field and bypasses session marker check

## Execution Steps
1. **Modify load-skill.sh:** Before checking the session marker file, parse SKILL.md frontmatter for `always-reload:
   true`. If present, always serve content.md (skip the marker check entirely).
   - Files: `plugin/scripts/load-skill.sh`
2. **Add always-reload to verbatim skill frontmatter:** Add `always-reload: true` to SKILL.md for status, help,
   token-report, render-diff.
   - Files: `plugin/skills/{status,help,token-report,render-diff}/SKILL.md`
3. **Run tests** to verify no regressions.

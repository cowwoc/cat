# Plan: stakeholder-convention-routing

## Goal
Route project convention files to relevant stakeholders during review so stakeholders can enforce
project-specific code style and behavioral rules.

## Satisfies
None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Convention files could be large, increasing token usage per stakeholder
- **Mitigation:** Only include conventions matching the stakeholder's frontmatter declaration

## Files to Modify
- `.claude/cat/conventions/java.md` - Add frontmatter with `stakeholders:` field
- `plugin/skills/stakeholder-review/SKILL.md` - Update prepare step to glob for conventions, parse
  frontmatter, and include filtered conventions in each stakeholder's prompt

## Acceptance Criteria
- [ ] Convention files with `stakeholders:` frontmatter are routed only to listed stakeholders
- [ ] Convention files without frontmatter are not included in any stakeholder prompt
- [ ] The prepare step discovers convention files dynamically (no hardcoded filenames)
- [ ] Design stakeholder receives java.md conventions when reviewing Java changes

## Execution Steps
1. **Add frontmatter to java.md:** Add YAML frontmatter with `stakeholders: [design, architect]`
   - Files: `.claude/cat/conventions/java.md`
2. **Update stakeholder-review prepare step:** Add convention discovery logic that globs for
   `.claude/cat/conventions/*.md`, parses YAML frontmatter for `stakeholders:` field, and builds
   a per-stakeholder convention map
   - Files: `plugin/skills/stakeholder-review/SKILL.md`
3. **Update spawn_reviewers step:** Include filtered conventions in each stakeholder's review prompt
   as a `## Project Conventions` section
   - Files: `plugin/skills/stakeholder-review/SKILL.md`

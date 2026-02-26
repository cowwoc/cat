# Plan: stakeholder-convention-routing

## Goal
Route project convention files to relevant stakeholders during review so stakeholders can enforce
project-specific code style and behavioral rules.

## Satisfies
None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Convention files could be large, increasing token usage per stakeholder
- **Mitigation:** Only include conventions matching the stakeholder's `subAgents` declaration

## Files to Modify
- `.claude/cat/rules/java.md` - Add frontmatter with `subAgents:` field
- `plugin/skills/stakeholder-review/first-use.md` - Update prepare step to glob for conventions,
  parse frontmatter, and include filtered conventions in each stakeholder's prompt

## Post-conditions
- [ ] Convention files with `subAgents:` frontmatter targeting specific stakeholder types are routed
  only to matching stakeholders
- [ ] Convention files without `subAgents` restriction are included in all stakeholder prompts
- [ ] The prepare step discovers convention files dynamically (no hardcoded filenames)
- [ ] Design stakeholder receives java.md conventions when reviewing Java changes

## Execution Steps
1. **Add frontmatter to java.md:** Add YAML frontmatter (omit `subAgents` for all subagents, or use
   specific types like `subAgents: [cat:stakeholder-design, cat:stakeholder-architecture]`)
   - Files: `.claude/cat/rules/java.md`
2. **Update stakeholder-review prepare step:** Add convention discovery logic that globs for
   `.claude/cat/rules/*.md`, parses YAML frontmatter for `subAgents:` field, and builds a
   per-stakeholder convention map (stripping `cat:stakeholder-` prefix for matching)
   - Files: `plugin/skills/stakeholder-review/first-use.md`
3. **Update spawn_reviewers step:** Include filtered conventions in each stakeholder's review prompt
   as a `## Project Conventions` section
   - Files: `plugin/skills/stakeholder-review/first-use.md`

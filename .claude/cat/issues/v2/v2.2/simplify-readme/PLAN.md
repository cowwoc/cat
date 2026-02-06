# Plan: simplify-readme

## Current State
README.md is ~400 lines with extensive detail on commands, configuration, skills, and project structure - overwhelming
for new users trying to get started.

## Target State
README.md is ~80-100 lines focused on quick start, with detailed sections moved to docs/ sub-pages for users who want to
learn more.

## Satisfies
None (user onboarding improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - documentation only
- **Mitigation:** All content preserved, just reorganized

## Files to Modify
- README.md - Simplify to quick start + essential commands
- docs/how-it-works.md - NEW: Planning hierarchy, workflow, reliability
- docs/commands.md - NEW: Full command reference
- docs/configuration.md - NEW: Config options, stakeholder reviews
- docs/tips.md - NEW: Best practices
- docs/contributing.md - NEW: Contribution guidelines

## Acceptance Criteria
- [ ] User can try CAT in < 30 seconds from reading README
- [ ] All detail pages linked from README
- [ ] No content lost, only reorganized

## Execution Steps
1. **Create docs/ directory structure**
   - Files: docs/*.md
   - Verify: All files exist

2. **Extract detailed sections to sub-pages**
   - Move "How CAT Works" to docs/how-it-works.md
   - Move full command reference to docs/commands.md
   - Move configuration details to docs/configuration.md
   - Move tips to docs/tips.md
   - Move contributing to docs/contributing.md
   - Verify: Each file has complete content

3. **Simplify README.md**
   - Keep: One-liner value prop, Quick Start, 5 essential commands
   - Add: "Learn More" section with links to sub-pages
   - Verify: README is < 100 lines

# Plan: audit-dead-code-files

## Goal
Systematically catalog every skill and hook file in the plugin to identify which are actively
referenced (instantiated, registered, or invoked) and which are dead code with no callers.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Some files may be referenced indirectly (e.g., via reflection or dynamic loading)
- **Mitigation:** Cross-reference multiple registration points (hooks.json, Java constructors,
  SKILL.md invocations)

## Files to Investigate
- `client/src/main/java/io/github/cowwoc/cat/hooks/` - Java hook handlers
- `plugin/hooks/hooks.json` - Hook registrations
- `plugin/skills/` - Plugin skill SKILL.md files
- `plugin/hooks/` - Bash hook scripts

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Enumerate all Java hook handler classes under `client/src/main/java/`
  - For each class, check if it is instantiated anywhere (grep for `new ClassName(`)
  - Files: `client/src/main/java/**/*.java`
- Enumerate all Bash hook scripts under `plugin/hooks/`
  - For each script, check if it appears in `plugin/hooks/hooks.json`
  - Files: `plugin/hooks/*.sh`, `plugin/hooks/hooks.json`

### Wave 2
- Enumerate all plugin skill directories under `plugin/skills/`
  - For each skill, check if its name appears in any agent `skills:` frontmatter or skill invocations
  - Files: `plugin/skills/*/SKILL.md`, `plugin/agents/*.md`, `plugin/hooks/hooks.json`
- Produce a catalog listing each file with status: REFERENCED or DEAD

## Post-conditions
- [ ] A catalog exists documenting every skill and hook file with REFERENCED or DEAD status
- [ ] Every DEAD file is identified with the reason (no instantiation, no registration, no invocation)
- [ ] Catalog is committed as a markdown file or documented in this PLAN.md

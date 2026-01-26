# Plan: extract-subagent-common-skills

## Current State

10 stakeholder agent files (`plugin/agents/stakeholder-*.md`) contain duplicated sections. Analysis
of all `plugin/agents/*.md` files identified the following common content:

| Section | Agents | Identical? |
|---------|--------|------------|
| `## Working Directory` | All 10 stakeholders | Yes — 1 variant |
| `## Mandatory Pre-Review Steps` | All 10 stakeholders | Yes — content identical |
| `## Fail-Fast: Working Directory Check` | All 10 stakeholders | Near-identical — only `"stakeholder": "[name]"` differs |

The non-stakeholder agents (`compression-agent.md`, `work-*.md`, `skill-*.md`) have unique content
with no shared sections across groups.

## Target State

Identical sections extracted to shared skills referenced via `skills:` frontmatter. Each stakeholder
agent loads the shared skill instead of embedding the content inline.

Claude Code processes `skills:` frontmatter before the first API turn (no extra network round-trip).
`@path` references inside skill files are also expanded at load time.

## Parent Requirements

None — refactor

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — effective system prompt content is unchanged
- **Mitigation:** Compare generated subagent prompts before/after to confirm semantic equivalence

## Files to Modify

- `plugin/agents/stakeholder-*.md` (10 files) — replace duplicated sections with `skills:` frontmatter
  reference; keep only stakeholder-specific `"stakeholder": "[name]"` values in fail-fast
- `plugin/skills/stakeholder-common/SKILL.md` (new) — shared sections for stakeholder agents
- `plugin/skills/stakeholder-common/first-use.md` (new) — content: `## Working Directory`,
  `## Mandatory Pre-Review Steps`

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Read all 10 `plugin/agents/stakeholder-*.md` files to extract exact text of common sections
  - Files: `plugin/agents/stakeholder-*.md`
- Create `plugin/skills/stakeholder-common/SKILL.md` with preprocessor directive
  - Files: `plugin/skills/stakeholder-common/SKILL.md`
- Create `plugin/skills/stakeholder-common/first-use.md` containing verbatim content of:
  `## Working Directory` and `## Mandatory Pre-Review Steps`
  - Files: `plugin/skills/stakeholder-common/first-use.md`

### Wave 2

- Update all 10 `plugin/agents/stakeholder-*.md` files to:
  1. Add `skills: [cat:stakeholder-common]` to YAML frontmatter
  2. Remove the now-shared `## Working Directory` and `## Mandatory Pre-Review Steps` sections
  - Files: `plugin/agents/stakeholder-*.md`
- Verify each agent file still contains all stakeholder-specific sections (Fail-Fast with correct
  stakeholder name, Holistic Review, domain-specific criteria, Detail File, Review Output Format)

## Post-conditions

- [ ] `plugin/skills/stakeholder-common/first-use.md` contains `## Working Directory` and
  `## Mandatory Pre-Review Steps` sections verbatim
- [ ] All 10 `plugin/agents/stakeholder-*.md` files reference `cat:stakeholder-common` in frontmatter
- [ ] No duplicated `## Working Directory` or `## Mandatory Pre-Review Steps` sections remain in agent files
- [ ] All other stakeholder-specific sections remain intact in each agent file
- [ ] E2E: Run `/cat:work` on any issue — stakeholder review completes successfully with all reviewers
  receiving correct Working Directory and Pre-Review instructions

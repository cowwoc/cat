# Plan: convert-stakeholders-to-agents

## Current State
13 stakeholder files in `plugin/stakeholders/` as plain markdown with informal structure.

## Target State
All stakeholders converted to proper Claude Code subagent format in `plugin/agents/` with:
- YAML frontmatter (name, description, tools, model, skills)
- System prompt in markdown body
- Skills field loading relevant skills into context

## Satisfies
None - infrastructure/refactoring

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Update external references to stakeholders
- **Mitigation:** Update all references in same commit

## Scope
| File | New Location |
|------|--------------|
| plugin/stakeholders/architect.md | plugin/agents/architect.md |
| plugin/stakeholders/deployment.md | plugin/agents/deployment.md |
| plugin/stakeholders/design.md | plugin/agents/design.md |
| plugin/stakeholders/index.md | plugin/agents/stakeholder-index.md |
| plugin/stakeholders/legal.md | plugin/agents/legal.md |
| plugin/stakeholders/marketing.md | plugin/agents/marketing.md |
| plugin/stakeholders/performance.md | plugin/agents/performance.md |
| plugin/stakeholders/requirements.md | plugin/agents/requirements.md |
| plugin/stakeholders/sales.md | plugin/agents/sales.md |
| plugin/stakeholders/security.md | plugin/agents/security.md |
| plugin/stakeholders/testing.md | plugin/agents/testing.md |
| plugin/stakeholders/ux.md | plugin/agents/ux.md |

## Acceptance Criteria
- [ ] All 13 stakeholder files converted to agent format
- [ ] Each agent has proper frontmatter (name, description, tools, model)
- [ ] External references updated (grep for 'stakeholders/' and update)
- [ ] Old stakeholders directory removed

## Execution Steps
1. **Step 1:** For each stakeholder file, add YAML frontmatter with name, description, tools (Read, Grep, Glob), model (haiku)
2. **Step 2:** Move converted files to plugin/agents/
3. **Step 3:** Update all references from plugin/stakeholders/ to plugin/agents/
4. **Step 4:** Remove plugin/stakeholders/ directory
5. **Step 5:** Update STATE.md to completed

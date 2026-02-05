# Plan: convert-stakeholders-to-agents

## Current State
12 stakeholder files in `plugin/stakeholders/` as plain markdown without proper Claude Code agent frontmatter.
These files define review and research personas but cannot leverage agent features like tool restrictions,
model selection, or skill preloading.

## Target State
All stakeholders converted to proper Claude Code subagent format in `plugin/agents/` with:
- YAML frontmatter (name, description, tools, model)
- System prompt preserved as markdown body
- External references updated

## Satisfies
None - infrastructure/refactoring

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Files that reference `stakeholders/` path need updating
- **Mitigation:** Update all references in same commit; grep confirms only 4 files affected

## Scope: Files to Convert

| Current Path | New Path | Agent Name |
|--------------|----------|------------|
| `plugin/stakeholders/architect.md` | `plugin/agents/stakeholder-architect.md` | `stakeholder-architect` |
| `plugin/stakeholders/deployment.md` | `plugin/agents/stakeholder-deployment.md` | `stakeholder-deployment` |
| `plugin/stakeholders/design.md` | `plugin/agents/stakeholder-design.md` | `stakeholder-design` |
| `plugin/stakeholders/index.md` | `plugin/agents/stakeholder-index.md` | `stakeholder-index` |
| `plugin/stakeholders/legal.md` | `plugin/agents/stakeholder-legal.md` | `stakeholder-legal` |
| `plugin/stakeholders/marketing.md` | `plugin/agents/stakeholder-marketing.md` | `stakeholder-marketing` |
| `plugin/stakeholders/performance.md` | `plugin/agents/stakeholder-performance.md` | `stakeholder-performance` |
| `plugin/stakeholders/requirements.md` | `plugin/agents/stakeholder-requirements.md` | `stakeholder-requirements` |
| `plugin/stakeholders/sales.md` | `plugin/agents/stakeholder-sales.md` | `stakeholder-sales` |
| `plugin/stakeholders/security.md` | `plugin/agents/stakeholder-security.md` | `stakeholder-security` |
| `plugin/stakeholders/testing.md` | `plugin/agents/stakeholder-testing.md` | `stakeholder-testing` |
| `plugin/stakeholders/ux.md` | `plugin/agents/stakeholder-ux.md` | `stakeholder-ux` |

**Total: 12 files**

## Files That Reference Stakeholders

Found via `grep -r 'stakeholders/' plugin/`:

| File | Reference Type |
|------|---------------|
| `plugin/skills/stakeholder-review/SKILL.md` | Loads stakeholder files |
| `plugin/skills/research/SKILL.md` | Loads stakeholder files |
| `plugin/skills/work-review/SKILL.md` | References stakeholder directory |
| `plugin/concepts/work.md` | Documentation reference |

## Agent Frontmatter Template

Each stakeholder will receive frontmatter following this pattern:

```yaml
---
name: stakeholder-{role}
description: "{Role} stakeholder for code review and research. Focus: {focus area}"
tools: Read, Grep, Glob, WebSearch, WebFetch
model: haiku
---
```

**Tool Selection Rationale:**
- `Read, Grep, Glob` - Codebase exploration
- `WebSearch, WebFetch` - Research mode requires web access
- No `Write, Edit` - Stakeholders are reviewers, not implementers
- `model: haiku` - Review tasks are well-suited to faster model

## Acceptance Criteria
- [ ] All 12 stakeholder files converted to agent format with proper frontmatter
- [ ] Each agent has: name, description, tools (Read, Grep, Glob, WebSearch, WebFetch), model (haiku)
- [ ] System prompt content preserved (review criteria, research approach, output formats)
- [ ] All 4 external references updated from `stakeholders/` to `agents/stakeholder-`
- [ ] Old `plugin/stakeholders/` directory removed
- [ ] No broken references remain (verify with grep)

## Execution Steps

1. **Step 1:** Convert each stakeholder file to agent format
   - Add YAML frontmatter block at top of file
   - Preserve existing markdown content as system prompt
   - Rename file to `stakeholder-{role}.md` pattern

2. **Step 2:** Move converted files to `plugin/agents/`
   ```bash
   for f in plugin/stakeholders/*.md; do
     name=$(basename "$f" .md)
     mv "$f" "plugin/agents/stakeholder-${name}.md"
   done
   ```

3. **Step 3:** Update external references
   - `plugin/skills/stakeholder-review/SKILL.md`: Update path references
   - `plugin/skills/research/SKILL.md`: Update path references
   - `plugin/skills/work-review/SKILL.md`: Update path references
   - `plugin/concepts/work.md`: Update documentation references

4. **Step 4:** Remove old directory
   ```bash
   rmdir plugin/stakeholders/
   ```

5. **Step 5:** Verify no broken references
   ```bash
   grep -r 'stakeholders/' plugin/  # Should return nothing
   ```

6. **Step 6:** Commit changes
   - Commit type: `config:` (Claude-facing agent definitions)

## Success Criteria
- [ ] All 12 stakeholders converted with proper frontmatter
- [ ] `grep -r 'stakeholders/' plugin/` returns no results
- [ ] Stakeholder-based skills (stakeholder-review, research) still function

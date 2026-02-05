# Plan: convert-skills-to-agents

## Current State
Several skills are designed to run as subagents (work-execute, work-merge, work-review, work-batch-executor, 
merge-subagent, etc.) but don't leverage the Claude Code `skills` frontmatter field to load relevant 
skills into their context.

## Target State
Subagent-style skills converted to proper agent format in `plugin/agents/` with:
- YAML frontmatter including `skills` field to preload relevant skills
- Example: work-merge agent loads git-squash, git-rebase, git-merge-linear skills

## Satisfies
None - infrastructure/refactoring

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Skills that invoke these as subagents need updating
- **Mitigation:** Update invocation patterns in work-batch-executor and work-with-issue

## Scope
Skills to convert (those with `user-invocable: false` that run as subagents):

| Skill | Becomes Agent | Preloaded Skills |
|-------|---------------|------------------|
| work-execute | work-execute | (domain-specific per task type) |
| work-merge | work-merge | git-squash, git-rebase, git-merge-linear |
| work-review | work-review | stakeholder-review |
| work-prepare | work-prepare | (none - discovery focused) |
| work-batch-executor | work-batch-executor | (orchestrates others) |
| merge-subagent | merge-subagent | git-squash, git-rebase |
| compare-docs/EXTRACTION-AGENT | extraction-agent | (specialized) |

## Acceptance Criteria
- [ ] Subagent-style skills converted to agent format
- [ ] Each agent has `skills` field listing relevant skills to preload
- [ ] Invocation code updated to use new agent definitions
- [ ] Work phases function correctly with new agent structure

## Execution Steps
1. **Step 1:** Identify all skills with `user-invocable: false` that are invoked via Task tool
2. **Step 2:** For each identified skill, create corresponding agent in plugin/agents/
3. **Step 3:** Add `skills` frontmatter to preload relevant domain skills (e.g., git-* for merge)
4. **Step 4:** Update work-batch-executor to reference new agent definitions
5. **Step 5:** Test work phases to verify agents load skills correctly
6. **Step 6:** Update STATE.md to completed

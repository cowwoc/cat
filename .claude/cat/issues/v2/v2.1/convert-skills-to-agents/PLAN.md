# Plan: convert-skills-to-agents

## Current State
Several skills are designed to run as subagents (invoked via Task tool) but are defined as skills.
This prevents them from using the `skills` frontmatter field to preload relevant domain knowledge.

**Problem Example:** `work-merge` needs git safety knowledge from `git-squash`, `git-rebase`, and
`git-merge-linear`, but subagents cannot invoke skills. Currently, git knowledge is duplicated
in work-merge's instructions. When git-* skills are updated, work-merge may not reflect changes.

## Target State
Subagent-style skills converted to proper Claude Code agent format with `skills` frontmatter
that preloads relevant skill content into the subagent's context automatically.

## Satisfies
None - infrastructure/refactoring

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Task tool invocations need to reference agents instead of skills
- **Mitigation:** Update work-batch-executor and work-with-issue in same commit

## Skills to Convert

Analysis of skills with `user-invocable: false` that are invoked via Task tool:

| Current Skill | Becomes Agent | Preloaded Skills | Rationale |
|---------------|---------------|------------------|-----------|
| `work-execute` | `work-execute` | (none) | Executes PLAN.md steps; skills vary per task |
| `work-merge` | `work-merge` | `git-squash`, `git-rebase`, `git-merge-linear` | Needs git safety patterns |
| `work-review` | `work-review` | `stakeholder-review` | Orchestrates stakeholder reviews |
| `work-prepare` | `work-prepare` | (none) | Task discovery; no domain skills needed |
| `work-batch-executor` | `work-batch-executor` | (none) | Orchestrates other agents |
| `merge-subagent` | `merge-subagent` | `git-squash`, `git-rebase` | Merges subagent branches |

**Skills NOT to convert** (reference documentation, not subagents):
- `git-squash`, `git-rebase`, `git-merge-linear`, `git-amend`, `git-commit` - These ARE skills that get preloaded
- `compare-docs`, `shrink-doc`, `delegate` - User-invocable skills
- Work phase reference docs (`work/phase-*.md`, `work/commit-rules.md`, etc.) - Documentation

## Agent Format for work-merge

```yaml
---
name: work-merge
description: Merge phase for /cat:work - squashes commits, merges to base, cleans up
tools: Read, Bash, Grep, Glob
model: haiku
skills:
  - git-squash
  - git-rebase
  - git-merge-linear
---

# Work Phase: Merge

Subagent for the merge phase of `/cat:work`. Handles commit squashing, branch merging,
worktree cleanup, and state updates.

[Rest of current SKILL.md content becomes system prompt]
```

**Key Change:** The git-* skills content is automatically injected into context, so the
subagent has full knowledge of git safety patterns without needing to invoke skills.

## Files to Modify

### New Agent Files (in plugin/agents/)

| Agent File | Source Skill | Preloads |
|------------|--------------|----------|
| `plugin/agents/work-execute.md` | `plugin/skills/work-execute/SKILL.md` | - |
| `plugin/agents/work-merge.md` | `plugin/skills/work-merge/SKILL.md` | git-squash, git-rebase, git-merge-linear |
| `plugin/agents/work-review.md` | `plugin/skills/work-review/SKILL.md` | stakeholder-review |
| `plugin/agents/work-prepare.md` | `plugin/skills/work-prepare/SKILL.md` | - |
| `plugin/agents/work-batch-executor.md` | `plugin/skills/work-batch-executor/SKILL.md` | - |
| `plugin/agents/merge-subagent.md` | `plugin/skills/merge-subagent/SKILL.md` | git-squash, git-rebase |

### Files to Update (Task tool invocations)

| File | Changes Needed |
|------|----------------|
| `plugin/skills/work-with-issue/SKILL.md` | Update Task prompts to reference agents |
| `plugin/skills/work-batch-executor/SKILL.md` | Update spawned subagent references |
| `plugin/skills/work/SKILL.md` | Update phase delegation |

### Skills to Keep (become preloaded content)

These remain as skills but are now preloaded into agents:
- `plugin/skills/git-squash/SKILL.md`
- `plugin/skills/git-rebase/SKILL.md`
- `plugin/skills/git-merge-linear/SKILL.md`
- `plugin/skills/stakeholder-review/SKILL.md`

## Acceptance Criteria
- [ ] 6 subagent-style skills converted to agent format in `plugin/agents/`
- [ ] Each agent has appropriate `skills` field for domain knowledge preloading
- [ ] work-merge agent preloads: git-squash, git-rebase, git-merge-linear
- [ ] work-review agent preloads: stakeholder-review
- [ ] merge-subagent agent preloads: git-squash, git-rebase
- [ ] Task tool invocations updated to use `subagent_type` matching agent names
- [ ] Work phases function correctly with new agent structure
- [ ] Original skill files can be removed (or kept as documentation)

## Execution Steps

1. **Step 1:** Create work-merge agent with skills preloading
   - Create `plugin/agents/work-merge.md`
   - Add frontmatter with `skills: [git-squash, git-rebase, git-merge-linear]`
   - Copy SKILL.md content as system prompt
   - Remove duplicated git instructions (now preloaded via skills)

2. **Step 2:** Create work-review agent with skills preloading
   - Create `plugin/agents/work-review.md`
   - Add frontmatter with `skills: [stakeholder-review]`
   - Copy SKILL.md content as system prompt

3. **Step 3:** Create merge-subagent agent with skills preloading
   - Create `plugin/agents/merge-subagent.md`
   - Add frontmatter with `skills: [git-squash, git-rebase]`
   - Copy SKILL.md content as system prompt

4. **Step 4:** Create remaining agents (no skill preloading needed)
   - Create `plugin/agents/work-execute.md`
   - Create `plugin/agents/work-prepare.md`
   - Create `plugin/agents/work-batch-executor.md`

5. **Step 5:** Update Task tool invocations
   - Update `work-batch-executor` to spawn agents by name
   - Update `work-with-issue` references
   - Verify `subagent_type` parameter matches agent `name` field

6. **Step 6:** Test work phases
   - Run `/cat:work` on a test task
   - Verify agents load with preloaded skill content
   - Verify merge phase has git safety knowledge

7. **Step 7:** Commit changes
   - Commit type: `config:` (Claude-facing agent definitions)

## Implementation Notes

### How Task Tool Finds Agents

The Task tool's `subagent_type` parameter matches against agent `name` fields.
When `subagent_type: "work-merge"` is specified, Claude Code loads `plugin/agents/work-merge.md`.

### Skills vs Agents Decision Tree

```
Is it invoked via Task tool as a subagent?
├── YES: Convert to agent in plugin/agents/
│   └── Does it need domain knowledge from other skills?
│       ├── YES: Add `skills` field to preload that knowledge
│       └── NO: Just convert format, no skills field needed
└── NO: Keep as skill in plugin/skills/
    └── Will it be preloaded into agents?
        ├── YES: Ensure skill content is self-contained
        └── NO: Regular user-invocable skill
```

## Success Criteria
- [ ] Work phases complete successfully with agent-based subagents
- [ ] work-merge agent demonstrates git safety knowledge from preloaded skills
- [ ] No manual duplication of git instructions in agent prompts
- [ ] Clean separation: agents in plugin/agents/, skills in plugin/skills/

# Plan: rename-branch-terminology-plugin

## Goal

Rename all `base_branch`/`BASE_BRANCH`/`baseBranch`/`base-branch` and `worktree_branch`/`worktree branch` references
in plugin markdown files (agents, skills, concepts) to use consistent `source_branch`/`target_branch` terminology.

- **`base_branch` → `target_branch`**: The branch being merged INTO (e.g., `main`, `v2.1`)
- **`worktree_branch` / issue branch → `source_branch`**: The branch being merged FROM (the issue/feature branch)

## Parent Issue

`2.1-rename-branch-terminology` (decomposed)

## Sequence

Sub-issue 1 of 2 — runs in parallel with `2.1-rename-branch-terminology-java`

## Satisfies

None — infrastructure terminology cleanup.

## Files to Modify

### Plugin Agents (Markdown)

- `plugin/agents/work-merge.md` — description and step references
- `plugin/agents/work-squash.md` — `BASE_BRANCH` variable, validation, rebase/squash commands
- `plugin/agents/work-verify.md` — metadata variable list

### Plugin Skills (Markdown)

- `plugin/skills/git-squash/first-use.md` — base branch references, worktree branch references
- `plugin/skills/git-merge-linear/first-use.md` — `BASE_BRANCH` variable, merge description
- `plugin/skills/git-rebase/first-use.md` — `BASE_BRANCH` variable, rebase description
- `plugin/skills/cleanup/first-use.md` — `BASE_BRANCH` variable, merge check
- `plugin/skills/stakeholder-review/first-use.md` — `BASE_BRANCH` variable, diff commands, worktree branch references
- `plugin/skills/skill-builder/first-use.md` — `BASE_BRANCH` reference
- `plugin/skills/work-merge/first-use.md` — `BASE_BRANCH` variable throughout, SKILL.md description
- `plugin/skills/work-merge/SKILL.md` — description text
- `plugin/skills/work-with-issue/first-use.md` — `BASE_BRANCH` variable (many references), merge prompt text
- `plugin/skills/work-prepare/first-use.md` — `BASE_BRANCH` variable, branch detection
- `plugin/skills/config/first-use.md` — merge strategy description
- `plugin/skills/work/first-use.md` — base branch references
- `plugin/skills/remove/first-use.md` — worktree/branch references
- `plugin/skills/statusline/first-use.md` — worktree/branch display

### Plugin Concepts (Markdown)

- `plugin/concepts/git-operations.md` — base branch merge instructions
- `plugin/concepts/error-handling.md` — `BASE_BRANCH` variable example
- `plugin/concepts/commit-types.md` — base branch references
- `plugin/concepts/work.md` — base branch isolation description
- `plugin/concepts/merge-and-cleanup.md` — extensive base branch references
- `plugin/concepts/agent-architecture.md` — worktree/branch cleanup reference
- `plugin/concepts/issue-resolution.md` — base branch reference

## Pre-conditions

- [ ] Parent issue `2.1-rename-branch-terminology` exists

## Sub-Agent Waves

### Wave 1

1. **Rename variables in plugin agent files** — Update `BASE_BRANCH` → `TARGET_BRANCH` in work-merge.md,
   work-squash.md, work-verify.md. Update natural language "base branch" → "target branch" and "worktree branch"
   → "source branch" where contextually appropriate.
2. **Rename variables in plugin skill files** — Update `BASE_BRANCH` → `TARGET_BRANCH` in all skill first-use.md and
   SKILL.md files. Update natural language references throughout.
3. **Rename variables in plugin concept files** — Update all concept documentation. Update natural language
   "base branch" → "target branch" and "worktree branch" → "source branch" where contextually appropriate.


## Post-conditions

- [ ] No references to `base_branch`, `BASE_BRANCH`, or `base-branch` remain in plugin/ agent, skill, or concept files
      (except where "base" is used in a non-branch-terminology context)
- [ ] No references to `worktree_branch` or `worktree branch` (as a named concept for the source branch) remain
- [ ] All `TARGET_BRANCH` references refer to the branch being merged INTO
- [ ] All `SOURCE_BRANCH` references refer to the branch being merged FROM

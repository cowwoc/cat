# Plan

## Goal

Update `plugin/skills/learn/phase-prevent.md` to explicitly prohibit saving prevention to auto-memory
(MEMORY.md). Prevention must be committed to project or plugin files, not persisted only in the
session-local auto-memory system.

## Post-conditions

- [ ] `phase-prevent.md` contains an explicit rule stating that saving prevention to MEMORY.md
  does not satisfy the prevention requirement
- [ ] The rule names the acceptable target locations: `plugin/rules/`, `plugin/skills/`,
  `plugin/concepts/`, `.claude/rules/` (per the audience of the rule)
- [ ] The rule is placed before the prevention level selection step so agents see it before choosing
  a prevention approach
- [ ] No regressions: existing prevention hierarchy and level guidance is unchanged
- [ ] Build passes: `mvn -f client/pom.xml verify -e` exits 0

## Jobs

### Job 1

- In `/home/node/.cat/worktrees/2.1-fix-learn-prevention-no-automemory/plugin/skills/learn/phase-prevent.md`,
  add the following block immediately before the `## Step 6: Identify Prevention Level` heading
  (after the `---` horizontal rule on line 87):

  ```markdown
  ## Auto-Memory Is Not Prevention

  BLOCKED: Do not save prevention to `MEMORY.md` (auto-memory).

  MEMORY.md is session-local and is not committed to the repository. Prevention saved only in MEMORY.md
  disappears when the session ends, which means the same mistake will recur in the next session. This does
  not satisfy the prevention requirement.

  Acceptable target locations for prevention (must be committed to git):
  - `plugin/rules/` — for rules that apply to all plugin skills
  - `plugin/skills/<skill-name>/` — for rules specific to one skill
  - `plugin/concepts/` — for conceptual reference material
  - `.claude/rules/` — for project-level rules

  Choose the target location based on the audience of the rule (see Fix Location Checklist in Step 9).
  ```

- After editing, use the Read tool to verify the new section appears in the file before `## Step 6`.
- Check that all added lines are at most 120 characters.
- Run the build: `mvn -f client/pom.xml verify -e` from the worktree root.
- Stage and commit:
  ```bash
  cd /home/node/.cat/worktrees/2.1-fix-learn-prevention-no-automemory
  git add plugin/skills/learn/phase-prevent.md
  git commit -m "bugfix: prohibit saving prevention to auto-memory in phase-prevent.md"
  ```
- Update index.json status to `in-progress` (already set by work-prepare).

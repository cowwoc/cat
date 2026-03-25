# Plan

## Goal

Move `model: sonnet` frontmatter out of 31 skill SKILL.md files into a centralized `plugin/rules/skill-models.md`
rule file. The rule instructs the agent to prefer Sonnet first, then fall back to Opus if Sonnet is rate-limited.
This enables graceful degradation when Sonnet quota is exhausted without requiring per-skill config changes.

Affected skills (currently `model: sonnet`):
- `cat:add-agent`
- `cat:decompose-issue-agent`
- `cat:empirical-test-agent`
- `cat:git-merge-linear-agent`
- `cat:git-rebase-agent`
- `cat:git-rewrite-history-agent`
- `cat:git-squash-agent`
- `cat:init`
- `cat:instruction-builder-agent`
- `cat:instruction-organizer-agent`
- `cat:learn`
- `cat:learn-agent`
- `cat:optimize-execution`
- `cat:optimize-execution-agent`
- `cat:plan-builder-agent`
- `cat:rebase-impact-agent`
- `cat:recover-from-drift-agent`
- `cat:research-agent`
- `cat:retrospective-agent`
- `cat:safe-remove-code-agent`
- `cat:skill-comparison-agent`
- `cat:stakeholder-review-agent`
- `cat:tdd-implementation-agent`
- `cat:verify-implementation-agent`
- `cat:work-agent`
- `cat:work-confirm-agent`
- `cat:work-implement-agent`
- `cat:work-merge-agent`
- `cat:work-prepare-agent`
- `cat:work-review-agent`
- `cat:work-with-issue-agent`

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged (skills still run on Sonnet by default)
- [ ] Tests passing
- [ ] Code quality improved (model selection centralized instead of scattered across 31 files)
- [ ] No SKILL.md file under `plugin/skills/` contains `model: sonnet` in frontmatter
- [ ] `plugin/rules/skill-models.md` exists and lists all 31 previously-Sonnet skills with preference order [sonnet, opus]
- [ ] E2E verification: invoke a skill that previously had `model: sonnet` and confirm the agent selects the correct model based on `plugin/rules/skill-models.md`

## Sub-Agent Waves

### Wave 1

- **Step 1: Create `plugin/rules/skill-models.md`**

  Create a new file at `plugin/rules/skill-models.md` with the following exact content:

  Line 1: `---`
  Line 2: `mainAgent: true`
  Line 3: `---`
  Line 4: `## Model Selection for Skills`
  Line 5: (blank)
  Line 6: `When invoking skills via the Skill tool or spawning subagents via the Agent/Task tool, use the following model`
  Line 7: `preference:`
  Line 8: (blank)
  Line 9: `**Sonnet-preferred skills** (use `model: sonnet` by default, fall back to `model: opus` if Sonnet is rate-limited):`
  Line 10: (blank)
  Then list all 31 skills as markdown bullet items (e.g., `- `+backtick+`cat:add-agent`+backtick)
  After the list:
  (blank line)
  `**Fallback behavior:** If Sonnet returns a rate-limit error, retry the same skill invocation using Opus. Do not`
  `ask the user before falling back — rate-limit fallback is automatic.`
  (blank line)
  `**Skills not listed above** use their SKILL.md `model:` frontmatter (typically `haiku` for lightweight tasks).`
  `Do not override their model selection.`

  Note: Files in `plugin/rules/` are exempt from license headers per `.claude/rules/license-header.md`.

- **Step 2: Remove `model: sonnet` from all 31 SKILL.md files**

  For each of the following 31 SKILL.md files, remove the line `model: sonnet` from the YAML frontmatter. Leave all
  other frontmatter fields intact. The files are:

  1. `plugin/skills/add-agent/SKILL.md`
  2. `plugin/skills/decompose-issue-agent/SKILL.md`
  3. `plugin/skills/empirical-test-agent/SKILL.md`
  4. `plugin/skills/git-merge-linear-agent/SKILL.md`
  5. `plugin/skills/git-rebase-agent/SKILL.md`
  6. `plugin/skills/git-rewrite-history-agent/SKILL.md`
  7. `plugin/skills/git-squash-agent/SKILL.md`
  8. `plugin/skills/init/SKILL.md`
  9. `plugin/skills/instruction-builder-agent/SKILL.md`
  10. `plugin/skills/instruction-organizer-agent/SKILL.md`
  11. `plugin/skills/learn/SKILL.md`
  12. `plugin/skills/learn-agent/SKILL.md`
  13. `plugin/skills/optimize-execution/SKILL.md`
  14. `plugin/skills/optimize-execution-agent/SKILL.md`
  15. `plugin/skills/plan-builder-agent/SKILL.md`
  16. `plugin/skills/rebase-impact-agent/SKILL.md`
  17. `plugin/skills/recover-from-drift-agent/SKILL.md`
  18. `plugin/skills/research-agent/SKILL.md`
  19. `plugin/skills/retrospective-agent/SKILL.md`
  20. `plugin/skills/safe-remove-code-agent/SKILL.md`
  21. `plugin/skills/skill-comparison-agent/SKILL.md`
  22. `plugin/skills/stakeholder-review-agent/SKILL.md`
  23. `plugin/skills/tdd-implementation-agent/SKILL.md`
  24. `plugin/skills/verify-implementation-agent/SKILL.md`
  25. `plugin/skills/work-agent/SKILL.md`
  26. `plugin/skills/work-confirm-agent/SKILL.md`
  27. `plugin/skills/work-implement-agent/SKILL.md`
  28. `plugin/skills/work-merge-agent/SKILL.md`
  29. `plugin/skills/work-prepare-agent/SKILL.md`
  30. `plugin/skills/work-review-agent/SKILL.md`
  31. `plugin/skills/work-with-issue-agent/SKILL.md`

  **How to remove:** For each file, read the file, find the line `model: sonnet`, and remove that entire line.
  Do NOT remove `model: haiku` lines (those belong to different skills and are not affected).
  Do NOT add any blank lines where the removed line was. Do NOT modify any other frontmatter fields.

- **Step 3: Verify no `model: sonnet` remains**

  Run: `grep -rl "model: sonnet" plugin/skills/`

  Expected output: no matches. If any matches remain, fix them.

- **Step 4: Run tests**

  Run: `mvn -f client/pom.xml test`

  All tests must pass.

- **Step 5: Update index.json**

  Update the issue's `index.json` to set `status` to `closed`.

- **Step 6: Commit**

  Commit all changes with message: `refactor: centralize sonnet model selection into plugin/rules/skill-models.md`

  Commit type is `refactor:` because this restructures model selection without changing user-visible behavior.

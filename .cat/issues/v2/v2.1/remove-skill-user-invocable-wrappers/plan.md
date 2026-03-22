# Plan: remove-skill-user-invocable-wrappers

## Goal
Remove the thin user-invocable wrapper skills for `cat:add`, `cat:empirical-test`, `cat:load-skill`,
`cat:remove`, `cat:research`, `cat:retrospective`, and `cat:work` by migrating their `first-use.md`
content into the corresponding agent variants. Also rename `cat:stakeholder-common` to
`cat:stakeholder-common-agent`. Update `README.md` and plugin rule/concept files to explain that users
trigger these operations through natural language prompts rather than slash commands.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Internal `/cat:` references inside `first-use.md` content must be updated to avoid
  confusing documentation; the `GetSkill` fallback from `*-agent` → parent will become unused after
  the move.
- **Mitigation:** Grep for `/cat:` references in migrated files; update to natural language or
  `cat:*-agent` qualified names.

## Files to Modify

### Moves (content migrated to agent variant)
- `plugin/skills/add/first-use.md` → `plugin/skills/add-agent/first-use.md` (update internal `/cat:add` refs)
- `plugin/skills/work/first-use.md` → `plugin/skills/work-agent/first-use.md`
- `plugin/skills/remove/first-use.md` → `plugin/skills/remove-agent/first-use.md` (update internal `/cat:remove` refs)
- `plugin/skills/research/first-use.md` → `plugin/skills/research-agent/first-use.md`
- `plugin/skills/retrospective/first-use.md` → `plugin/skills/retrospective-agent/first-use.md`
- `plugin/skills/load-skill/first-use.md` → `plugin/skills/load-skill-agent/first-use.md`
- `plugin/skills/add/tests/` → `plugin/skills/add-agent/tests/`

### Deletions (entire directories removed after content migration)
- `plugin/skills/add/` — wrapper only; content moved to `add-agent/`
- `plugin/skills/empirical-test/` — `empirical-test-agent/first-use.md` already exists
- `plugin/skills/load-skill/` — content moved to `load-skill-agent/`
- `plugin/skills/remove/` — content moved to `remove-agent/`
- `plugin/skills/research/` — content moved to `research-agent/`
- `plugin/skills/retrospective/` — content moved to `retrospective-agent/`
- `plugin/skills/work/` — content moved to `work-agent/`

### Rename
- `plugin/skills/stakeholder-common/` → `plugin/skills/stakeholder-common-agent/`

### Updated references
- `plugin/agents/stakeholder-architecture.md` — `cat:stakeholder-common` → `cat:stakeholder-common-agent`
- `plugin/agents/stakeholder-deployment.md` — `cat:stakeholder-common` → `cat:stakeholder-common-agent`
- `plugin/agents/stakeholder-legal.md` — `cat:stakeholder-common` → `cat:stakeholder-common-agent`
- `plugin/agents/stakeholder-business.md` — `cat:stakeholder-common` → `cat:stakeholder-common-agent`
- `plugin/agents/stakeholder-requirements.md` — `cat:stakeholder-common` → `cat:stakeholder-common-agent`
- `plugin/agents/stakeholder-ux.md` — `cat:stakeholder-common` → `cat:stakeholder-common-agent`
- `plugin/agents/stakeholder-testing.md` — `cat:stakeholder-common` → `cat:stakeholder-common-agent`
- `plugin/agents/stakeholder-performance.md` — `cat:stakeholder-common` → `cat:stakeholder-common-agent`
- `plugin/agents/stakeholder-design.md` — `cat:stakeholder-common` → `cat:stakeholder-common-agent`
- `plugin/agents/stakeholder-security.md` — `cat:stakeholder-common` → `cat:stakeholder-common-agent`
- `README.md` — replace `/cat:add`, `/cat:work`, `/cat:remove`, `/cat:research` slash command
  descriptions with natural-language trigger descriptions; update command reference table
- `plugin/rules/work-request-handling.md` — replace `/cat:add` and `/cat:work` slash commands with
  natural language equivalents (e.g., "ask Claude to add an issue", "ask Claude to work on an issue")
- `plugin/rules/worktree-isolation.md` — update `/cat:add` and `/cat:work` references
- `plugin/rules/tasklist-lifecycle.md` — update `/cat:add` and `/cat:work` references
- `plugin/rules/implementation-delegation.md` — update `/cat:work` reference
- `plugin/rules/user-input-handling.md` — update `/cat:work` reference
- `plugin/concepts/skill-loading.md` — update `/cat:empirical-test` examples to use `cat:empirical-test-agent`
- `plugin/concepts/agent-architecture.md` — update `/cat:add` and `/cat:work` examples

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Migrate first-use.md files to agent variants
- Move `plugin/skills/add/first-use.md` to `plugin/skills/add-agent/first-use.md`; update
  internal references from `/cat:add` to natural language (e.g., "say 'add an issue that...'")
  - Files: `plugin/skills/add-agent/first-use.md`
- Move `plugin/skills/work/first-use.md` to `plugin/skills/work-agent/first-use.md`
  - Files: `plugin/skills/work-agent/first-use.md`
- Move `plugin/skills/remove/first-use.md` to `plugin/skills/remove-agent/first-use.md`; update
  internal `/cat:remove` references
  - Files: `plugin/skills/remove-agent/first-use.md`
- Move `plugin/skills/research/first-use.md` to `plugin/skills/research-agent/first-use.md`
  - Files: `plugin/skills/research-agent/first-use.md`
- Move `plugin/skills/retrospective/first-use.md` to `plugin/skills/retrospective-agent/first-use.md`
  - Files: `plugin/skills/retrospective-agent/first-use.md`
- Move `plugin/skills/load-skill/first-use.md` to `plugin/skills/load-skill-agent/first-use.md`
  - Files: `plugin/skills/load-skill-agent/first-use.md`
- Move `plugin/skills/add/tests/` to `plugin/skills/add-agent/tests/`
  - Files: `plugin/skills/add-agent/tests/`

### Wave 2: Rename stakeholder-common and delete wrapper directories
- Rename `plugin/skills/stakeholder-common/` to `plugin/skills/stakeholder-common-agent/`
- Delete `plugin/skills/add/`, `plugin/skills/empirical-test/`, `plugin/skills/load-skill/`,
  `plugin/skills/remove/`, `plugin/skills/research/`, `plugin/skills/retrospective/`,
  `plugin/skills/work/`

### Wave 3: Update references
- Update all 10 `plugin/agents/stakeholder-*.md` files: `cat:stakeholder-common` →
  `cat:stakeholder-common-agent`
- Update `plugin/rules/work-request-handling.md`, `plugin/rules/worktree-isolation.md`,
  `plugin/rules/tasklist-lifecycle.md`, `plugin/rules/implementation-delegation.md`,
  `plugin/rules/user-input-handling.md`
- Update `plugin/concepts/skill-loading.md`, `plugin/concepts/agent-architecture.md`

### Wave 3b: Fix remaining slash command references in plugin/concepts/
- In `plugin/concepts/version-completion.md` line 277, replace the user-facing instruction
  `Use /cat:add to add more issues or versions.` with natural language equivalent
  (e.g., "Ask Claude to add more issues or versions.")
  - Files: `plugin/concepts/version-completion.md`
- In `plugin/concepts/error-handling.md` line 192, replace the user-facing recovery instruction
  `Resume with /cat:work` with natural language equivalent (e.g., "Ask Claude to resume work on the issue")
  - Files: `plugin/concepts/error-handling.md`

### Wave 3c: Fix remaining slash command references (iteration 2)
- In `plugin/concepts/error-handling.md` line 249, replace `User resumes with /cat:work` with
  natural language equivalent (e.g., "Ask Claude to resume work on the issue")
  - Files: `plugin/concepts/error-handling.md`
- In `plugin/concepts/worktree-isolation.md` line 214, replace the parenthetical example
  `Run \`/cat:work\` to create the worktree` with natural language equivalent
  (e.g., `Ask Claude to resume work on the issue to create the worktree`)
  - Files: `plugin/concepts/worktree-isolation.md`

### Wave 4: Update README.md
- Replace slash command references for removed skills with natural language trigger descriptions;
  update the command reference table
  - Files: `README.md`

### Wave 5: Fix stakeholder review concerns
- In `README.md` line 243, replace `/cat:skill-builder` with `/cat:instruction-builder-agent`
  - Files: `README.md`
- In `plugin/skills/remove-agent/first-use.md` line 354 and `plugin/skills/add-agent/first-use.md` line 1167,
  replace `grep -oP` (Perl regex, not POSIX-compatible) with POSIX-compatible grep+sed pattern
  - Files: `plugin/skills/remove-agent/first-use.md`, `plugin/skills/add-agent/first-use.md`
- In `plugin/concepts/parallel-execution.md` line 14, `plugin/concepts/version-scheme.md` line 35, and
  `plugin/concepts/work.md` line 130, replace `/cat:work` and `/cat:add` slash commands with natural language
  or qualified skill names (`cat:work-agent`, `cat:add-agent`)
  - Files: `plugin/concepts/parallel-execution.md`, `plugin/concepts/version-scheme.md`, `plugin/concepts/work.md`

## Post-conditions
- [ ] None of `plugin/skills/add/`, `plugin/skills/empirical-test/`, `plugin/skills/load-skill/`,
  `plugin/skills/remove/`, `plugin/skills/research/`, `plugin/skills/retrospective/`,
  `plugin/skills/work/` exist
- [ ] `plugin/skills/stakeholder-common/` does not exist; `plugin/skills/stakeholder-common-agent/`
  exists and contains `SKILL.md` and `first-use.md`
- [ ] All files matching `plugin/agents/stakeholder-*.md` reference `cat:stakeholder-common-agent`
  (not `cat:stakeholder-common`)
- [ ] Each of `plugin/skills/add-agent/`, `plugin/skills/work-agent/`, `plugin/skills/remove-agent/`,
  `plugin/skills/research-agent/`, `plugin/skills/retrospective-agent/`,
  `plugin/skills/load-skill-agent/` contains `first-use.md`
- [ ] `plugin/skills/add-agent/tests/` contains the bats test files previously in
  `plugin/skills/add/tests/`
- [ ] `README.md` no longer shows `/cat:add`, `/cat:work`, `/cat:remove`, `/cat:research`,
  `/cat:retrospective` as slash commands; natural language trigger descriptions are provided instead
- [ ] No file in `plugin/rules/` or `plugin/concepts/` refers to the removed slash commands as
  user-facing instructions

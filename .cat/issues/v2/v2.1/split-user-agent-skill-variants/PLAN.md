# Plan: split-user-agent-skill-variants

## Current State
All skills that need a CAT agent ID use `"$0"` in their SKILL.md preprocessor directive. When a user invokes a
skill directly (e.g., `/cat:learn /cat:status description`), Claude Code substitutes `$0` with the first word of
the user's text instead of the agent ID, causing SkillLoader to crash with a path-traversal error.

## Target State
Each user-invocable skill that uses `$0` as catAgentId is split into two variants:
- **User-facing** (`<name>/SKILL.md`): `disable-model-invocation: true`, uses `${CLAUDE_SESSION_ID}` (no `$0`)
- **Model-facing** (`<name>-agent/SKILL.md`): `user-invocable: false`, uses `$0` as mandatory catAgentId

Users continue to invoke skills by their existing names. Agents and subagents invoke `<name>-agent` variants.
Both variants load the same skill content from `plugin/skills/<name>/`.

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Agents must use `<name>-agent` variants; any agent calling the user-facing variant from
  a subagent context will use the wrong catAgentId (session ID instead of composite ID). Session instructions
  must be updated to direct agents to `-agent` variants.
- **Mitigation:** Update InjectSessionInstructions.java in the same commit; add E2E test verifying the crash
  scenario no longer occurs.

## Skills Requiring Splitting

User-invocable skills that use `$0` as catAgentId (no `user-invocable: false` in current frontmatter):

| Skill | Directory | Uses $ARGUMENTS? |
|-------|-----------|-----------------|
| cat:add | plugin/skills/add/ | No |
| cat:cleanup | plugin/skills/cleanup/ | No |
| cat:config | plugin/skills/config/ | No |
| cat:empirical-test | plugin/skills/empirical-test/ | No |
| cat:feedback | plugin/skills/feedback/ | No |
| cat:get-output | plugin/skills/get-output/ | Yes (`$ARGUMENTS`) |
| cat:get-subagent-status | plugin/skills/get-subagent-status/ | No |
| cat:help | plugin/skills/help/ | No |
| cat:init | plugin/skills/init/ | No |
| cat:learn | plugin/skills/learn/ | No |
| cat:load-skill | plugin/skills/load-skill/ | No |
| cat:optimize-execution | plugin/skills/optimize-execution/ | No |
| cat:recover-from-drift | plugin/skills/recover-from-drift/ | No |
| cat:remove | plugin/skills/remove/ | No |
| cat:research | plugin/skills/research/ | No |
| cat:retrospective | plugin/skills/retrospective/ | No (renamed from run-retrospective by dependency) |
| cat:shrink-doc | plugin/skills/shrink-doc/ | No |
| cat:status | plugin/skills/status/ | No |
| cat:statusline | plugin/skills/statusline/ | No |
| cat:work | plugin/skills/work/ | No |

**Already model-only (no change needed):** batch-read, batch-write, collect-results, compare-docs,
decompose-issue, delegate, format-documentation, get-diff, get-history, get-session-id, git-amend, git-commit,
git-merge-linear, git-rebase, git-rewrite-history, git-squash, grep-and-read, merge-subagent, register-hook,
safe-remove-code, safe-rm, skill-builder, stakeholder-*, tdd-implementation, token-report, validate-git-safety,
verify-implementation, work-complete, work-merge, work-prepare, work-with-issue, write-and-commit.

## File Patterns

### User-facing variant (existing `<name>/SKILL.md`, modified)

For skills without `$ARGUMENTS`:
```markdown
---
description: >
  [existing description]
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" <name> "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}"`
```

For skills with `$ARGUMENTS` (get-output only):
```markdown
---
description: >
  [existing description]
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" <name> "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}" $ARGUMENTS`
```

### Model-facing variant (new `<name>-agent/SKILL.md`)

For skills without `$ARGUMENTS`:
```markdown
---
description: >
  [same description as user-facing, or brief note that this is the agent variant]
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" <name> "${CLAUDE_PROJECT_DIR}" "$0"`
```

For skills with `$ARGUMENTS` (get-output only):
```markdown
---
description: >
  [description]
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" get-output "${CLAUDE_PROJECT_DIR}" "$0" $ARGUMENTS`
```

**Key:** Both variants pass the same `<base-name>` (without `-agent`) to `load-skill`. SkillLoader resolves
content from `plugin/skills/<base-name>/` for both, so no content duplication is needed.

## Pre-conditions
- [ ] `rename-run-retrospective-skill` is closed (so we work with the final `retrospective` skill name)

## Execution Waves

### Wave 1: Create model-facing (-agent) skill directories and SKILL.md files
- For each of the 20 skills listed above, create `plugin/skills/<name>-agent/SKILL.md`
  - Set `user-invocable: false` in frontmatter
  - Keep `"$0"` in preprocessor directive
  - Pass `<base-name>` (without `-agent`) as the skill name to `load-skill`
  - Copy the existing `description:` from the current SKILL.md
  - Files: `plugin/skills/add-agent/SKILL.md`, `plugin/skills/cleanup-agent/SKILL.md`,
    `plugin/skills/config-agent/SKILL.md`, `plugin/skills/empirical-test-agent/SKILL.md`,
    `plugin/skills/feedback-agent/SKILL.md`, `plugin/skills/get-output-agent/SKILL.md`,
    `plugin/skills/get-subagent-status-agent/SKILL.md`, `plugin/skills/help-agent/SKILL.md`,
    `plugin/skills/init-agent/SKILL.md`, `plugin/skills/learn-agent/SKILL.md`,
    `plugin/skills/load-skill-agent/SKILL.md`, `plugin/skills/optimize-execution-agent/SKILL.md`,
    `plugin/skills/recover-from-drift-agent/SKILL.md`, `plugin/skills/remove-agent/SKILL.md`,
    `plugin/skills/research-agent/SKILL.md`, `plugin/skills/retrospective-agent/SKILL.md`,
    `plugin/skills/shrink-doc-agent/SKILL.md`, `plugin/skills/status-agent/SKILL.md`,
    `plugin/skills/statusline-agent/SKILL.md`, `plugin/skills/work-agent/SKILL.md`

### Wave 2: Modify user-facing skill SKILL.md files
- For each of the 20 skills, modify `plugin/skills/<name>/SKILL.md`:
  - Add `disable-model-invocation: true` to frontmatter
  - Replace `"$0"` with `"${CLAUDE_SESSION_ID}"` in the preprocessor directive
  - If the skill uses `$ARGUMENTS`, the directive becomes:
    `load-skill ... <name> "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}" $ARGUMENTS`
  - Files: all 20 `plugin/skills/<name>/SKILL.md` files listed in the table above

### Wave 3: Update InjectSessionInstructions.java
- Find all references to user-facing skill names (`cat:learn`, `cat:work`, etc.) that agents are
  instructed to invoke
- Update them to use the `-agent` variant (`cat:learn-agent`, `cat:work-agent`, etc.)
- File: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java`
- Also update any agent prompt templates in `plugin/agents/` that reference skill names

### Wave 4: Update skill invocations in skill content files
- Search for skill invocations inside phase files and SKILL.md content that reference user-facing
  skill names and update them to use `-agent` variants
  - `plugin/skills/learn/phase-prevent.md` — references `cat:run-retrospective`
    (will be `cat:retrospective-agent` after this issue)
  - `plugin/skills/*/first-use.md` — check for skill name references
  - Run: `grep -r "cat:learn\|cat:work\|cat:add\|cat:research\|cat:status" plugin/skills/ --include="*.md"`
  - Update any references found to use `-agent` variants where appropriate

### Wave 5: Run tests
- Command: `mvn -f client/pom.xml test`

## Post-conditions
- [ ] For each of the 20 skills: `plugin/skills/<name>-agent/SKILL.md` exists with `user-invocable: false`
  and `"$0"` in the preprocessor directive
- [ ] For each of the 20 skills: `plugin/skills/<name>/SKILL.md` has `disable-model-invocation: true`
  and `"${CLAUDE_SESSION_ID}"` in place of `"$0"`
- [ ] E2E: Invoking `/cat:learn some description text` no longer crashes with path-traversal error
- [ ] E2E: An agent invoking `cat:learn-agent` with catAgentId as `$0` works correctly
- [ ] Session instructions (InjectSessionInstructions.java) direct agents to use `<name>-agent` variants
- [ ] All tests pass (`mvn -f client/pom.xml test` exits 0)
- [ ] No occurrences of user-facing skill names remain in agent-facing content where `-agent` variant
  should be used

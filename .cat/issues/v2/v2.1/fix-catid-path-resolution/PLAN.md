# Plan: fix-catid-path-resolution

## Goal
Identify why `catAgentId` resolves to incorrect values (branch names, worktree relative paths, literal `{`) causing
`skills-loaded` marker files to be created in wrong directories instead of the expected session directory.

## Satisfies
None

## Current Behavior
The `skills-loaded` marker file is supposed to be created at:
```
${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/{sessionId}/skills-loaded
```

Instead, marker files have been found at:
- `.../projects/-workspace/cat/worktrees/2.1-fix-skillloader-non-ascii-characters/skills-loaded`
  (catAgentId = `cat/worktrees/2.1-fix-skillloader-non-ascii-characters`, a worktree relative path)
- `.../projects/-workspace/2.1-document-proper-lock-checking-in-workflow/skills-loaded`
  (catAgentId = branch name)
- `.../projects/-workspace/v2.1-fix-approval-gate-wizard-invocation/skills-loaded`
  (catAgentId = literal branch name)
- `.../projects/-workspace/{/skills-loaded`
  (catAgentId = literal `{`, suggesting a failed `${VAR}` variable expansion)

In `SkillLoader`, `catAgentId` is derived from `skillArgs.getFirst()` (`$0`). If `$0` is blank, it falls back to
`scope.getClaudeSessionId()`. The incorrect values suggest `$0` is being set to wrong values in some invocation paths.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Investigation may reveal multiple root causes across different skill invocation paths
- **Mitigation:** Reproduce each case, trace the `$0` value through the invocation chain

## Files to Investigate
- `plugin/skills/*/SKILL.md` — check how `$0` is passed to skill-loader
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — trace catAgentId resolution
- Claude Code plugin preprocessor — understand how `$0` is substituted in `!`` directives

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Examine all `SKILL.md` files that invoke `skill-loader` and check how `$0` is passed
  - Files: `plugin/skills/*/SKILL.md`
- Trace how Claude Code's `!`` preprocessor substitutes `$0` — is it the first skill arg or shell's `$0`?
- Examine the `{` case: identify what `${VAR}` expansion could produce a literal `{`
- Check if the worktree path case is reproducible and what skill invocation triggered it

### Wave 2
- Fix the root cause(s) identified in Wave 1
  - Files: affected `SKILL.md` files and/or `SkillLoader.java`
- Add validation in `SkillLoader` to reject catAgentId values that look like branch names or file paths
  (a valid catAgentId is a UUID or `{uuid}/subagents/{agentId}`)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
- Run `mvn -f client/pom.xml test` and verify all tests pass

## Post-conditions
- `catAgentId` always resolves to a session UUID or `{uuid}/subagents/{agentId}` pattern
- `skills-loaded` marker files are only ever created under `{sessionDir}/`
- No `skills-loaded` files appear in git worktree directories or using branch names as paths
- All existing tests pass

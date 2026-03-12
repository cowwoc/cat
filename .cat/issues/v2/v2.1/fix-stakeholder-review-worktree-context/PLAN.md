# Plan: fix-stakeholder-review-worktree-context

## Problem

Stakeholder reviewer subagents read implementation files from `/workspace/` (the default cwd) instead of the worktree
containing the actual implementation. Reviewers receive only a change summary with no working directory, so they resolve
file paths relative to their inherited `/workspace/` cwd, which may be on a different branch.

## Satisfies

None

## Reproduction

1. Run `/cat:work` on any issue
2. Stakeholder review phase spawns reviewer subagents
3. Each reviewer uses `Read` with paths like `/workspace/client/src/.../Foo.java`
4. `/workspace/` is on a different branch than the worktree — reviewers analyze pre-implementation code

## Expected vs Actual

- **Expected:** Reviewer reads from `${WORKTREE_PATH}/client/src/.../Foo.java`
- **Actual:** Reviewer reads from `/workspace/client/src/.../Foo.java` (wrong branch)

## Root Cause

The stakeholder-review-agent SKILL.md prepare step builds reviewer prompts without including the worktree path or
relative file list. Without a working directory in the prompt, reviewers default to `/workspace/`.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Reviewers are currently reading wrong code — fix improves accuracy
- **Mitigation:** Verify reviewer reads are from worktree path after fix

## Files to Modify

- `plugin/skills/stakeholder-review-agent/SKILL.md` — replace FILE_CONTENTS embedding with Working Directory +
  relative file list in each reviewer prompt
- `plugin/agents/stakeholder-requirements.md` — add fail-fast if no working directory in prompt
- `plugin/agents/stakeholder-architecture.md` — add fail-fast if no working directory in prompt
- `plugin/agents/stakeholder-security.md` — add fail-fast if no working directory in prompt
- `plugin/agents/stakeholder-design.md` — add fail-fast if no working directory in prompt
- `plugin/agents/stakeholder-testing.md` — add fail-fast if no working directory in prompt
- `plugin/agents/stakeholder-performance.md` — add fail-fast if no working directory in prompt

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Update `stakeholder-review-agent/SKILL.md` prepare step: replace FILE_CONTENTS embedding with a Working Directory
  section (WORKTREE_PATH) and relative file list in each reviewer prompt
  - Files: `plugin/skills/stakeholder-review-agent/SKILL.md`

### Wave 2

- Add fail-fast to each reviewer agent definition: if no "Working Directory" section is found in the prompt, return
  REJECTED with a CRITICAL concern rather than defaulting to `/workspace/`
  - Files: `plugin/agents/stakeholder-requirements.md`, `plugin/agents/stakeholder-architecture.md`,
    `plugin/agents/stakeholder-security.md`, `plugin/agents/stakeholder-design.md`,
    `plugin/agents/stakeholder-testing.md`, `plugin/agents/stakeholder-performance.md`

## Post-conditions

- [ ] Reviewer subagents read files from `WORKTREE_PATH`, not `/workspace/`
- [ ] Reviewer subagent returns REJECTED with CRITICAL concern if no working directory is provided in the prompt
- [ ] No regressions in stakeholder review workflow
- [ ] E2E: Run `/cat:work` on a real issue and confirm reviewer `Read` tool calls target the worktree path

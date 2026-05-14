# Fix Codex Subagent Workflow Failures

## Problem
During work on `2.1-codex-friendly-help-output`, multiple CAT subagents failed for workflow reasons rather than
implementation defects. The failures produced false blockers, noisy review results, and an E2E failure that reflected
a missing Claude runtime in a Codex session.

## Parent Requirements
- None

## Reproduction Evidence
- `cat-work-execute` returned `BLOCKED` with "Implementation already applied" before any implementation files were
  changed, even though the target help files still contained Markdown pipe tables.
- `cat-work-verify` reported all implementation criteria as done but returned `INCOMPLETE` because runtime E2E invoked
  `instruction-test-runner`, which failed at `ModelIdResolver.detectClaudeCodeVersion` when `claude --version` was not
  available in the Codex environment.
- Ad-hoc stakeholder reviewer prompts that did not use the exact `## Working Directory` section shape were rejected by
  stakeholder agents. The official stakeholder-review workflow already documents the required section; implementation
  should verify whether the generated official prompts match that shape before changing stakeholder instructions.

## Expected vs Actual
- **Expected:** Work execution does not report a clean pre-implementation branch as already implemented; verification
  distinguishes unavailable runtime E2E infrastructure from missing implementation criteria in Codex; stakeholder
  prompt requirements are documented or tested so callers know the exact `## Working Directory` contract.
- **Actual:** Work execution can stop with a false pre-existing-implementation blocker; verification marks the issue
  incomplete when Claude-specific runtime E2E is unavailable under Codex; manual stakeholder review attempts fail-fast
  unless the prompt has the exact required heading.

## Root Cause
The work-execute "already applied" preflight is underspecified for lightweight plans and clean branches: "no diff from
target" before implementation is a normal starting state, not proof that the target branch already contains the fix.
The verify E2E path assumes Claude Code is available even when CAT is running under Codex. Stakeholder agents require
an exact prompt section, but that contract is easy to miss outside the official stakeholder-review skill.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Regression Risk:** Over-relaxing preflight or E2E checks could hide real already-applied work or skip meaningful
  runtime verification.
- **Mitigation:** Add focused regression tests for the false-blocker and Codex-no-Claude scenarios before changing
  the workflow instructions or verifier behavior.

## Files to Modify
- `client/plugin/agents/work-execute.toml` - Clarify or replace the already-applied preflight so clean branches are
  not treated as complete before implementation.
- `client/plugin/agents/work-verify.toml` and/or related verifier/runtime E2E instructions - Treat unavailable
  Claude runtime under Codex as an infrastructure skip or explicit environment limitation, not a missing
  implementation criterion.
- `client/plugin/agents/stakeholder-*.toml` or `client/plugin/skills/common/stakeholder-review/first-use.md` only if
  investigation shows the official stakeholder-review prompt does not include the exact `## Working Directory` section
  required by stakeholder agents.
- Relevant Java or instruction tests under `client/cli/src/test/` or `client/plugin/tests/` to lock the behavior.

## Test Cases
- [ ] A clean issue branch with no implementation diff does not cause `cat-work-execute` to return the
  "Implementation already applied" blocker before it has read and executed plan jobs.
- [ ] A genuinely already-applied implementation is still detected only when there is positive evidence that the
  target branch already satisfies the requested plan.
- [ ] In a Codex environment without `claude` on `PATH`, runtime E2E verification reports an infrastructure skip or
  environment limitation distinctly from unmet post-conditions.
- [ ] Stakeholder-review generated prompts include the exact `## Working Directory` section and `WORKTREE_PATH:` line
  that stakeholder agents require.

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Write failing tests or instruction-test scenarios for the work-execute false-blocker behavior.
- Update the work-execute instructions or supporting logic so "no diff from target" is not sufficient by itself to
  classify an issue as already implemented.
- Preserve valid already-applied detection with a positive-evidence rule, such as criteria verification against the
  target branch or explicit suspicious commits from prepare.

### Job 2
- Write failing tests or instruction-test scenarios for Codex verification when `claude --version` is unavailable.
- Update work-verify/runtime E2E guidance so unavailable Claude infrastructure in Codex is reported separately from
  implementation incompleteness.
- Ensure full Maven verification still runs and remains blocking for plugin/client changes.

### Job 3
- Verify official stakeholder-review prompt generation against the stakeholder agents' exact `## Working Directory`
  fail-fast contract.
- Add or update tests if the official prompt contract is not already covered.
- Update stakeholder-review documentation or prompt generation only if the official workflow can reproduce the
  missing-section failure.
- Update this issue's `index.json` in the same implementation commit as the fixes.
- Run `mvn -f client/pom.xml verify -e`.

## Post-conditions
- [ ] Work-execute no longer returns a false "already applied" blocker for clean pre-implementation branches.
- [ ] Work-verify distinguishes Codex missing-Claude E2E infrastructure from unmet issue criteria.
- [ ] Stakeholder-review prompt generation is tested or documented against the required `## Working Directory`
      contract.
- [ ] `mvn -f client/pom.xml verify -e` passes.

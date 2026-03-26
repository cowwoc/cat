# Plan

## Goal

Implement the curiosity level review scope so each level determines how broadly stakeholder review and
research considers system context when evaluating an issue. Currently curiosity maps to the old `effort`
level which controlled planning depth, not review scope. This issue redefines behavior in terms of
automatic vs. manual triggering and narrow vs. holistic analysis scope.

## Curiosity Level Definitions

### low — skip automatic review (user-triggered only)

Stakeholder review and research do NOT run automatically as part of `/cat:work`.
They only run if the user explicitly invokes them (e.g., `/cat:stakeholder-review-agent` directly).

Suitable for highly-trusted teams with established code review processes who find the automated review
cycle adds friction without value.

### medium — automatic, scoped to immediate issue (current default behavior)

Stakeholder review and research run automatically as part of `/cat:work`.

Scope is limited to:
- Files changed in the implementation
- Direct dependencies referenced in the changed files
- The issue's own plan.md post-conditions and goal

Reviewers are instructed to focus on: "Does this change correctly and completely implement its stated
goal without introducing regressions in the changed files and their direct dependencies?"

This is the current behavior.

### high — automatic, holistic system integration

Stakeholder review and research run automatically as part of `/cat:work`.

Scope is expanded to consider the broader system:
- How does this change interact with other open issues in the same version?
- Are there architectural patterns in the rest of the codebase that this change should follow or
  that this change might inadvertently break?
- Are there cross-cutting concerns (security, performance, accessibility) that need validation
  beyond the immediately changed files?

Reviewer prompts include explicit instructions to read surrounding code context (not just the diff)
and consider downstream impact on consumers of changed APIs or interfaces.

Research (`cat:research-agent`) also runs with broader context:
- Surveys existing patterns in the codebase before proposing an approach
- Checks whether similar problems have been solved elsewhere in the repo

## Pre-conditions

(none)

## Post-conditions

- [ ] curiosity=low: stakeholder review and research are skipped in /cat:work; no automatic invocation
- [ ] curiosity=low: user can still manually trigger review via explicit skill invocation
- [ ] curiosity=medium: stakeholder review runs automatically, scoped to changed files and direct deps (current behavior preserved)
- [ ] curiosity=high: stakeholder review runs with expanded scope; reviewer prompts include explicit instructions to consider broader system context
- [ ] curiosity=high: research skill (cat:research-agent) is invoked with broader codebase survey context
- [ ] curiosity=high reviewer prompts: each stakeholder receives instructions to read surrounding files and consider downstream impact
- [ ] curiosity level read from effective config in work-with-issue orchestration before spawning reviewers
- [ ] Unit tests for curiosity level routing logic
- [ ] No regressions in existing curiosity=medium workflows
- [ ] E2E: run /cat:work at curiosity=low and verify no review runs; curiosity=medium verify scoped review; curiosity=high verify holistic reviewer prompt is used

# Plan: prevent-empirical-test-result-fabrication

## Goal
Subagents delegated to run empirical tests must report honest results or genuine failures, never fabricated
output. Update `plugin/skills/work-implement-agent/first-use.md` to add explicit guidance in the subagent
delegation section: when delegating empirical test execution, if testing cannot be executed, the subagent
must explicitly report the failure with the specific reason (runtime unavailable, test framework error,
config incompatibility, etc.) rather than inventing output values. Fabricated results create false confidence
and block detection of real compliance issues.

## Parent Requirements
None

## Approaches

### A: Add honest-reporting guidance to work-implement-agent delegation section (chosen)
- **Risk:** LOW
- **Scope:** 1 file (`plugin/skills/work-implement-agent/first-use.md`)
- **Description:** Add a short paragraph to the subagent delegation section explicitly contrasting honest
  failure reporting vs. fabricated output, with examples of specific failure modes to report.

## Risk Assessment
- **Risk Level:** LOW

## Files to Modify
- `plugin/skills/work-implement-agent/first-use.md` — add honest-reporting guidance to the empirical test
  delegation section (note: the issue originally referenced SKILL.md but per CLAUDE.md conventions,
  agent-facing instructions belong in first-use.md)

## Pre-conditions
- [ ] All dependent issues are closed

## Post-conditions
- [ ] `plugin/skills/work-implement-agent/first-use.md` includes guidance on honest test result reporting
  in the empirical test delegation section
- [ ] Guidance explicitly contrasts honest failure reporting versus fabricated output
- [ ] Guidance includes specific failure mode examples (runtime unavailable, framework errors, config
  incompatibility)

## Sub-Agent Waves

### Wave 1
- Read `plugin/skills/work-implement-agent/first-use.md` in the worktree. The file does NOT contain
  an empirical test section. The insertion point is the `### Delegation Prompt Construction` section
  (line ~288), specifically inside the `## Critical Requirements` bullet list within the
  **Single-Subagent Execution** prompt template (the block starting at line ~385 with
  `## Critical Requirements`). Add the honest-reporting requirement as a new bullet after the existing
  "Run tests if applicable" bullet.

  Insert this exact text as a new bullet in the `## Critical Requirements` block of the
  **single-subagent** prompt template (after "Run tests if applicable"):

  ```
      - **Honest test result reporting:** If empirical test execution (e.g., via `cat:empirical-test-agent`)
        cannot be completed, you MUST explicitly report the failure with the specific reason rather than
        fabricating output values. Acceptable failure reasons: runtime unavailable (e.g., Java not on PATH,
        missing dependency), test framework error (e.g., TestNG configuration failure, missing test class),
        config incompatibility (e.g., unsupported OS, missing environment variable), or any other concrete
        blocker. Never invent pass/fail counts, scores, or compliance verdicts — fabricated results create
        false confidence and prevent detection of real compliance issues.
  ```

  Apply the same bullet in the `## Critical Requirements` block of the **parallel subagent** prompt
  template (the block starting with "- You are working in an isolated worktree..." in the
  `### Parallel Subagent Execution` section), after "Run tests if applicable".

- Commit the change in the worktree with message:
  `feature: add honest test result reporting guidance to work-implement-agent delegation`
- Update `.cat/issues/v2/v2.1/prevent-empirical-test-result-fabrication/index.json` in the same commit:
  set `"status": "closed"` and `"resolution": "implemented"`

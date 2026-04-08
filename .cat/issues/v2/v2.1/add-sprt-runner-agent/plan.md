# Plan: add-sprt-runner-agent

## Goal

Create a standalone `cat:sprt-runner-agent` skill that runs SPRT tests on any directory of `.md` test
files using the same log-ratio confidence boundaries as the instruction-builder-agent pipeline, decoupled
from that pipeline so it can be used for both empirical comparison tests and organic skill-selection tests.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** instruction-builder-agent Steps 6–8 delegation must preserve existing SPRT behavior exactly
- **Mitigation:** run existing instruction-builder-agent SPRT suite after refactor to verify no regression

## Files to Modify

- `plugin/skills/sprt-runner-agent/SKILL.md` — new skill entry point
- `plugin/skills/sprt-runner-agent/first-use.md` — full SPRT orchestration instructions
- `plugin/skills/instruction-builder-agent/first-use.md` — replace Steps 6–8 inline loop with call to `cat:sprt-runner-agent`

## Pre-conditions

- [ ] All dependent issues are closed

## Main Agent Jobs

- /cat:instruction-builder-agent plugin/skills/sprt-runner-agent/first-use.md

## Jobs

### Job 1: Create sprt-runner-agent skill

- Create `plugin/skills/sprt-runner-agent/SKILL.md` with description and argument-hint for test_dir
- Create `plugin/skills/sprt-runner-agent/first-use.md` implementing the SPRT loop:
  - Accept `test_dir` (directory of `.md` test files) and `worktree_path`
  - For each `.md` file: extract model (frontmatter), Turn 1 prompt, and `## Assertions`
  - Initialize SPRT state via `instruction-test-runner init-sprt`
  - Per trial: spawn isolated worktree subagent with test prompt, invoke `skill-grader-agent` to
    semantically grade assertions, call `update-sprt`, call `check-boundary`
  - Continue until all test cases reach ACCEPT or REJECT
  - Report per-test-case decisions and overall result
  - Files: `plugin/skills/sprt-runner-agent/SKILL.md`, `plugin/skills/sprt-runner-agent/first-use.md`

### Job 2: Refactor instruction-builder-agent Steps 6–8

- Replace the inline SPRT trial loop in `plugin/skills/instruction-builder-agent/first-use.md`
  Steps 6–8 with a delegation call to `cat:sprt-runner-agent <TEST_DIR> <WORKTREE_PATH>`
- Preserve all existing behavior: model selection, boundary thresholds, grader invocation, result reporting
  - Files: `plugin/skills/instruction-builder-agent/first-use.md`

## Post-conditions

- [ ] `cat:sprt-runner-agent` accepts a test directory and runs SPRT to ACCEPT/REJECT for each `.md` file
- [ ] Works on `plugin/tests/skills/work-execute/haiku-plan-execution/` test files (empirical comparison)
- [ ] Works on `plugin/tests/skills/*/first-use/` test files (organic skill-selection)
- [ ] `instruction-builder-agent` SPRT suite passes after delegation refactor (no regression)
- [ ] E2E: running `cat:sprt-runner-agent` on `haiku-plan-execution/` produces ACCEPT for all 6 test cases

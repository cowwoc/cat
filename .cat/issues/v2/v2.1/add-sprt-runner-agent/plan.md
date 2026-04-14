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
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/InstructionTestRunner.java` — add `run-sprt-batch` subcommand

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

### Job 3: Add haiku-plan-execution test files

- Cherry-pick the `plugin/tests/skills/work-execute/haiku-plan-execution/` directory from branch
  `2.1-test-haiku-mechanical-plan-execution` into the current branch using:
  ```
  git checkout 2.1-test-haiku-mechanical-plan-execution -- plugin/tests/skills/work-execute/haiku-plan-execution/
  ```
- Commit the 6 test `.md` files under `plugin/tests/skills/work-execute/haiku-plan-execution/`:
  - `creates-file-at-correct-path.md`
  - `sonnet-creates-file-at-correct-path.md`
  - `sonnet-updates-index-json-closed.md`
  - `sonnet-uses-exact-content-from-plan.md`
  - `updates-index-json-closed.md`
  - `uses-exact-content-from-plan.md`
- Files: `plugin/tests/skills/work-execute/haiku-plan-execution/*.md`

### Job 4: Replace bash batch runner with Java CLI

- Add `run-sprt-batch` subcommand to `InstructionTestRunner.java` implementing the batch orchestration:
  - Accept arguments: `<worktree_path> <sprt_state_json> <issue_name> <project_dir> <session_id> <model_id>`
  - Invoke `create-runner-worktrees` to create git worktrees for undecided TCs
  - For each TC: invoke `prepare-trial` with the runner worktree path from `create-runner-worktrees` output
  - Launch `claude-runner` instances (parallel for TC1-TC8, sequential for TC9)
  - For each TC: invoke `check-run-contamination` on the output JSON
  - For each TC: invoke test-case-specific grader logic (inline or via grader subagent)
  - For each TC: invoke `update-sprt` with the verdict
  - For each TC: invoke `check-boundary` to determine ACCEPT/REJECT/INCONCLUSIVE
  - Invoke `remove-runner-worktrees` to clean up
  - Return JSON summary: `{"decided_count": N, "inconclusive_tcs": [...]}`
- Key improvements over bash version:
  - Structured data binding (runner worktree path tied to TC record, no re-derivation from batch number)
  - Type-safe trial number handling (no BATCH_NUM vs trial_num mismatch)
  - Grader paths use the actual runner worktree from `create-runner-worktrees`, not reconstructed from suffix
- Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/InstructionTestRunner.java`

## Post-conditions

- [ ] `cat:sprt-runner-agent` accepts a test directory and runs SPRT to ACCEPT/REJECT for each `.md` file
- [ ] Works on `plugin/tests/skills/work-execute/haiku-plan-execution/` test files (empirical comparison)
- [ ] Works on `plugin/tests/skills/*/first-use/` test files (organic skill-selection)
- [ ] `instruction-builder-agent` SPRT suite passes after delegation refactor (no regression)
- [ ] E2E: running `cat:sprt-runner-agent` on `haiku-plan-execution/` produces ACCEPT for all 6 test cases
- [ ] Java CLI `run-sprt-batch` subcommand replaces bash batch runner script
- [ ] SPRT batch runs use correct per-TC trial numbers and runner worktree paths (no BATCH_NUM mismatch)

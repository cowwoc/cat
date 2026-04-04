# Plan

## Goal

Add skill tests for instruction-builder Step 6 (SPRT Test Execution) verifying test-runner filesystem isolation:
1. The instruction-builder creates an orphaned branch for the test runner
2. The orphaned branch does not contain the assertions for the test that will be run by the test runner
3. Test assertions are not revealed to the test runner in any other way (e.g. by placing them in another file,
   or sending them to the subagent over the prompt)

Also extract process-spawning logic from `EmpiricalTestRunner` into a new `ClaudeRunner` class with an
accompanying `cat:claude-runner` skill, enabling nested Claude instances with isolated plugin caches.

Additionally, consolidate the `instruction-organizer-agent` cross-file reorganization pipeline into
`instruction-builder-agent` as a new final Step 12, and remove the standalone `instruction-organizer-agent` skill.

Finally, extend the incremental change detection so that modifying any file that SKILL.md depends on transitively
(including `first-use.md` and any other files loaded via directives) is treated as a skill modification, triggering
re-testing the same as a direct SKILL.md change.

## Pre-conditions

(none)

## Post-conditions

- [ ] Skill test case exists verifying the instruction-builder creates an orphan branch (via `git checkout --orphan`) before spawning test-run subagents
- [ ] Skill test case exists verifying the orphan branch has `## Assertions` sections stripped from test case files
- [ ] Skill test case exists verifying test-run subagent prompts do not contain assertion text, full scenario file paths, or any other assertion leakage vector
- [ ] ValidateSkillTestFormat ignores `## Turn` and `## Assertions` headings inside fenced code blocks
- [ ] ExtractTurnsContent ignores `## Turn` and `## Assertions` headings inside fenced code blocks
- [ ] `ClaudeRunner` class extracted from `EmpiricalTestRunner` with config isolation support
- [ ] `cat:claude-runner` skill exists in `plugin/skills/claude-runner/`
- [ ] `EmpiricalTestRunner` delegates to `ClaudeRunner` for process spawning (no duplicate code)
- [ ] Unit tests for `ClaudeRunner` (config isolation, process env)
- [ ] Integration tests for `ClaudeRunner` (opt-in via `-Dintegration=true`)
- [ ] All existing tests pass (`mvn -f client/pom.xml test` exits 0)
- [ ] Skill test case exists verifying the instruction-builder uses incremental change detection (detect-changes, extract-units, map-units) when re-testing an updated skill
- [ ] Skill test case exists verifying the instruction-builder runs full SPRT on all test cases for a brand-new skill (no detect-changes)
- [ ] No regressions in existing instruction-builder skill tests
- [ ] SPRT results are streamed as each test case completes (pass/fail printed immediately, not batched at end)
- [ ] `instruction-builder-agent` includes Step 12: Cross-File Reorganization (four-phase pipeline), with a
  loop-back to Step 11 when `first-use.md` is modified by reorganization
- [ ] `instruction-organizer-agent` skill removed from `plugin/skills/`
- [ ] Modifying any file that SKILL.md depends on transitively (e.g. `first-use.md`) triggers incremental
  change detection the same as a direct SKILL.md modification
- [ ] Skill test case exists verifying that modifying a transitive dependency of SKILL.md (e.g. `first-use.md`)
  causes the instruction-builder to resolve from SKILL.md outward, detect the changed dependency, and run
  incremental SPRT rather than treating the skill as unchanged

## Jobs

### Job 1
- Commit the ClaudeRunner extraction work: `ClaudeRunner.java`, `ClaudeRunnerTest.java`,
  `ClaudeRunnerIntegrationTest.java`, `plugin/skills/claude-runner/SKILL.md`,
  `plugin/skills/claude-runner/first-use.md`, modified `EmpiricalTestRunner.java`,
  modified `EmpiricalTestRunnerTest.java`, modified `build-jlink.sh`,
  modified `plugin/skills/empirical-test-agent/first-use.md`
  - Commit type: `feature:`
  - Commit message: `feature: extract ClaudeRunner from EmpiricalTestRunner with config isolation`
- Update `index.json` status to `closed` in the same commit
- Run `mvn -f client/pom.xml test` in the worktree and verify exit code 0

### Job 2
- Create `plugin/tests/skills/instruction-builder-agent/first-use/step6-creates-orphan-branch.md`: a
  `category: requirement` skill test verifying that before spawning test-run subagents the
  instruction-builder executes `git checkout --orphan` (or equivalent orphan branch creation). The Turn 1
  scenario describes a skill with one test case ready to run and asks the agent to proceed to Step 6 (SPRT
  Test Execution). Assertions must include a deterministic regex assertion matching
  `checkout.*--orphan|--orphan` in the agent output.
- Create `plugin/tests/skills/instruction-builder-agent/first-use/step6-orphan-branch-strips-assertions.md`:
  a `category: requirement` skill test verifying that the orphan branch used for test-run subagents has
  `## Assertions` sections stripped from test case files. The Turn 1 scenario provides a ready-to-run test
  case that contains a `## Assertions` section and asks the agent to execute Step 6. Assertions must
  include a semantic assertion that the agent strips or removes assertion content from the test case files
  in the orphan branch before subagents read them.
- Create `plugin/tests/skills/instruction-builder-agent/first-use/step6-subagent-prompt-no-assertion-leakage.md`:
  a `category: requirement` skill test verifying that the subagent prompts sent to test-run subagents do
  not contain assertion text, full scenario file paths, or any other assertion leakage vector. The Turn 1
  scenario provides a test case file containing an `## Assertions` section and asks the agent to proceed
  through Step 6. Assertions must include semantic assertions that (a) the prompt passed to the subagent
  does not contain assertion text from the test case, and (b) the prompt does not expose the full path to
  the scenario file.
- Create `plugin/tests/skills/instruction-builder-agent/first-use/step6-incremental-retest-uses-change-detection.md`:
  a `category: requirement` skill test verifying that when re-testing an updated skill (not a brand-new
  skill) the instruction-builder uses the incremental pipeline: detect-changes, then extract-units, then
  map-units, before running SPRT. The Turn 1 scenario presents an existing skill that has just been revised
  and asks the agent to proceed to SPRT execution. Assertions must include deterministic string-match
  assertions that the agent invokes detect-changes and extract-units (or equivalent pipeline tool names)
  rather than running full SPRT on all test cases unconditionally.
- Create `plugin/tests/skills/instruction-builder-agent/first-use/step6-new-skill-runs-full-sprt.md`: a
  `category: requirement` skill test verifying that for a brand-new skill (no prior test history and no
  detect-changes applicable) the instruction-builder runs full SPRT on all test cases without invoking
  detect-changes. The Turn 1 scenario presents a newly created skill with two test cases and asks the agent
  to proceed to SPRT execution. Assertions must include a semantic assertion that the agent does not invoke
  detect-changes and runs SPRT on all provided test cases.
- Restore the deleted scenario: create
  `plugin/tests/skills/instruction-builder-agent/first-use/step43-sprt-runs-when-effort-not-low.md` as a
  `category: requirement` skill test verifying that SPRT test execution proceeds when the skill effort
  level is not `low`. The Turn 1 scenario sets up a skill with effort level `medium` (or `high`) and one
  test case, then asks the agent to run Step 6. Assertions must include a deterministic regex assertion
  confirming the agent proceeds with SPRT rather than skipping it due to effort level.

### Job 3
- Add Step 12: Cross-File Reorganization to `plugin/skills/instruction-builder-agent/first-use.md`:
  a four-phase classify-extract-reconstruct-verify pipeline that reorganizes content across companion files.
  When `first-use.md` is modified by reorganization, loop back to Step 11 (Compression Phase).
  - Commit type: `feature:`
  - Commit message: `feature: consolidate instruction-organizer pipeline into instruction-builder as Step 12`
- Delete `plugin/skills/instruction-organizer-agent/` (both `SKILL.md` and `first-use.md`)
- Update the instruction-builder's incremental change detection so that any file transitively loaded by
  SKILL.md (e.g., `first-use.md`) is treated as a skill modification for change-detection purposes:
  - When `first-use.md` or another directive-loaded file changes, the instruction-builder triggers
    `detect-changes` → `extract-units` → `map-units` before running SPRT, not a full unconditional re-run
  - Commit type: `feature:`
  - Commit message: `feature: treat transitive SKILL.md dependencies as skill modifications for change detection`
- Create `plugin/tests/skills/instruction-builder-agent/first-use/step6-transitive-dependency-change-triggers-retest.md`:
  a `category: requirement` skill test verifying that modifying a file transitively loaded by SKILL.md causes
  the instruction-builder to treat the skill as changed and run incremental SPRT. The Turn 1 scenario presents
  a skill whose SKILL.md loads `first-use.md` via a directive, with a prior test history and a modified
  `first-use.md`. Assertions must include a semantic assertion that the agent resolves transitive dependencies
  starting from SKILL.md, detects the changed dependency, and invokes the incremental change detection pipeline
  (detect-changes → extract-units → map-units) rather than treating the skill as unchanged.
- Run `mvn -f client/pom.xml test` in the worktree and verify exit code 0

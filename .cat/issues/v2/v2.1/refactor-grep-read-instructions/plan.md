# Plan

## Goal

Run instruction-builder on cat:grep-and-read-agent skill to redesign and improve its instructions; add
e2e post-condition verifying subagents invoke this skill when they need to search for patterns and read
the matching files in a single operation.

## Pre-conditions

(none)

## Post-conditions

- [ ] Instruction-builder workflow completed for cat:grep-and-read-agent (design, benchmark, adversarial TDD, compression)
- [ ] Skill instructions improved with clearer trigger conditions and usage guidance
- [ ] All tests pass after changes
- [ ] E2e verification: spawn a subagent with a task requiring grep+read (search code then read matches) and confirm it invokes cat:grep-and-read-agent instead of making sequential Grep+Read calls
- [ ] No regressions in existing behavior

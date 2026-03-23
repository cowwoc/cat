# Plan

## Goal

Run instruction-builder on cat:batch-write-agent skill to redesign and improve its instructions; add
e2e post-condition verifying subagents invoke this skill when they need to write 3+ independent files
in a single operation.

## Pre-conditions

(none)

## Post-conditions

- [ ] Instruction-builder workflow completed for cat:batch-write-agent (design, benchmark, adversarial TDD, compression)
- [ ] Skill instructions improved with clearer trigger conditions and usage guidance
- [ ] All tests pass after changes
- [ ] E2e verification: spawn a subagent with a task requiring writing 3+ independent files and confirm it invokes cat:batch-write-agent instead of making individual Write calls
- [ ] No regressions in existing behavior

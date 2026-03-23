# Plan

## Goal

Run instruction-builder on cat:batch-read-agent skill to redesign and improve its instructions; add
e2e post-condition verifying subagents invoke this skill when they need to read 3+ related files in a
single operation.

## Pre-conditions

(none)

## Post-conditions

- [ ] Instruction-builder workflow completed for cat:batch-read-agent (design, benchmark, adversarial TDD, compression)
- [ ] Skill instructions improved with clearer trigger conditions and usage guidance
- [ ] All tests pass after changes
- [ ] E2e verification: spawn a subagent with a task requiring reading 3+ related files and confirm it invokes cat:batch-read-agent instead of making individual Read calls
- [ ] No regressions in existing behavior

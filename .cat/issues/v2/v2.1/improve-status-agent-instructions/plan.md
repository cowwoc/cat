# Plan: improve-status-agent-instructions

## Current State
The status-agent SKILL.md is a minimal dynamic stub that calls a Java binary at invocation time.
The generated instruction text is minimal: "Echo the content of the `<output>` tag below verbatim."

## Target State
Run the instruction-builder on the status-agent skill to evaluate, improve, benchmark, harden, and
compress the skill instructions. The result should be a more robust and well-tested SKILL.md.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — behavior-preserving refactor
- **Mitigation:** Instruction-builder includes benchmarking and adversarial hardening phases

## Files to Modify
- plugin/skills/status-agent/SKILL.md - improved instruction text via instruction-builder

## Pre-conditions
- [ ] All dependent issues are closed

## Main Agent Waves
- /cat:instruction-builder-agent plugin/skills/status-agent/SKILL.md

## Sub-Agent Waves

(None — instruction-builder handles all sub-phases internally)

## Post-conditions
- [ ] Instruction-builder completes all phases (design, benchmark, adversarial hardening, compression)
- [ ] Skill SPRT benchmark result shows at least one Accept decision
- [ ] Updated plugin/skills/status-agent/SKILL.md is committed to the issue branch
- [ ] E2E: Invoke /cat:status after the change and confirm the status box renders correctly

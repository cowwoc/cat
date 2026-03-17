# Plan: fix-skill-builder-fresh-subagent-per-run

## Goal

Fix `cat:instruction-builder-agent`'s SPRT benchmark runner to ensure each benchmark run spawns a
completely fresh subagent. This enforces the independence assumption that SPRT requires for statistically
valid results.

## Background

SPRT requires each trial to be an independent Bernoulli draw from the same underlying distribution.
When multiple benchmark runs execute inside a single subagent context, trial N is conditioned on
trials 1…N-1 (the agent has already seen the prior runs in its conversation history). This is
batch contamination.

Observed effect: In `2.1-improve-status-agent-instructions`, runs 1-13 (individual fresh subagents)
passed TC5 100% of the time. Runs 14-27 (batched into one subagent context) passed only ~7% of the
time — the agent learned from the conversation history to add follow-up questions. This caused a
spurious SPRT Reject.

## Approach

Audit the instruction-builder's Step 3 benchmark runner:

1. Read `plugin/skills/instruction-builder-agent/SKILL.md` to understand current benchmarking design
2. Identify any code paths where multiple runs for the same test case are batched into one subagent
3. Enforce: each individual run (TC + run number) spawns its own fresh Task subagent with no prior
   conversation context from other runs
4. The SPRT orchestrator that accumulates results should be in the parent agent, not inside a
   benchmarking subagent

## Sub-Agent Waves

### Wave 1
- Read `plugin/skills/instruction-builder-agent/SKILL.md` (worktree copy) — specifically Step 3 and any
  benchmark-runner sub-steps
- Identify the current batching design: where does the skill instruct the agent to spawn subagents for runs?
  Is there any instruction that allows batching multiple runs into one context?
- Fix the skill instructions to enforce one-subagent-per-run
- Add an explicit guard: "Each run MUST spawn a fresh subagent. Do NOT execute multiple runs in the same
  subagent context — context from prior runs contaminates later runs and invalidates SPRT independence."
- Commit the fix

## Post-conditions

- [ ] `plugin/skills/instruction-builder-agent/SKILL.md` explicitly requires one fresh subagent per benchmark run
- [ ] No instruction in the SPRT benchmark loop allows batching multiple runs into one subagent context
- [ ] A comment or guard is present explaining WHY (SPRT independence requirement)
- [ ] No regressions to the existing benchmark design

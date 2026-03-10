# Plan: refactor-adversarial-tdd-protocol

## Current State
The skill-builder adversarial TDD loop (Step 4 and Step 5) spawns fresh red-team and blue-team subagents each round,
passing full document content and JSON findings back and forth via prompt injection. Each round re-analyzes the entire
document from scratch, wasting tokens on unchanged sections.

## Target State
- Each subagent commits its changes (findings or patches) to git and returns the commit hash
- One persistent red-team agent and one persistent blue-team agent are reused across all rounds via `resume`
- In round 2+, agents focus on diffs from the previous round rather than re-analyzing the entire document

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** The adversarial loop protocol changes structurally (commit-based handoff replaces JSON relay,
  agent reuse replaces fresh spawning). All callers of the adversarial loop (Step 4 main flow, Step 5 in-place
  hardening) are affected.
- **Mitigation:** The loop's external contract (input: instructions, output: hardened instructions) stays the same.
  Only the internal round-to-round mechanism changes. Test with a sample skill hardening pass after implementation.

## Files to Modify
- `plugin/skills/skill-builder-agent/first-use.md` — Rewrite Step 4 (adversarial TDD loop) and Step 5 (in-place hardening
  mode) to use commit-based handoff, agent reuse via `resume`, and delta-focused analysis in round 2+

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Rewrite Step 4 adversarial TDD loop in `plugin/skills/skill-builder-agent/first-use.md`:
  - Replace fresh agent spawning with persistent agents: spawn red-team agent once in round 1, `resume` it in
    subsequent rounds; same for blue-team
  - Replace JSON return format with commit-based handoff: red-team commits findings to a file, returns commit hash;
    blue-team applies fixes to the skill file, commits, returns commit hash
  - Add delta-focus instructions for round 2+: provide `git diff` of previous round's changes so agents analyze only
    what changed, not the full document
  - Update termination criteria to work with commit-based output (check findings file from red-team commit)
  - Update the "Why two separate subagents per round" rationale to acknowledge agent reuse and explain why the
    efficiency gain outweighs anchoring risk (agents are instructed to seek new attack vectors each round)
- Update Step 5 (in-place hardening mode) to align with the new protocol:
  - Remove the "do NOT commit between rounds" constraint since commit-based handoff now produces per-round commits
  - Adjust the single-commit rule: either squash at convergence or accept per-round commits as the new norm
  - Batch mode remains sequential by default, parallel optional

## Post-conditions
- [ ] Step 4 uses commit-based handoff instead of JSON relay between red/blue team agents
- [ ] Step 4 reuses one red-team and one blue-team agent across rounds via `resume`
- [ ] Step 4 includes delta-focus instructions for round 2+ (agents receive diffs, not full document)
- [ ] Step 5 is consistent with the new Step 4 protocol
- [ ] All tests pass after refactoring
- [ ] E2E: Run in-place hardening on a sample skill file; verify: (a) git log shows alternating red-team/blue-team commits per round, (b) final findings.json has `major_loopholes_found: false` or loop reached 10-round cap, (c) no CRITICAL/HIGH loopholes remain at natural convergence.
  (Live agent execution is verified manually; validate-adversarial-protocol.sh covers structural correctness automatically.)

### Wave 2 (fix iterations)

- Add a structural validation script (`plugin/scripts/validate-adversarial-protocol.sh`) that statically inspects
  `plugin/skills/skill-builder-agent/first-use.md` and asserts:
  - Commit-based handoff is present (red-team and blue-team prompts contain `git commit` and `Return only the commit
    hash`)
  - Agent reuse via `resume` is present (round 2+ section contains `resume` and `task_id`)
  - Delta-focus instructions are present (round 2+ prompts contain `git diff` and `Focus ONLY on the diff`)
  - Per-round commit confirmation in Step 5 (Step 5 section contains `per-round commits`)
  - Script exits 0 on success, prints a failure summary and exits 1 on any missing element
  - Update the E2E post-condition note in PLAN.md to clarify that live agent execution is verified manually during
    `skill-builder` use; the static validation script covers structural correctness automatically

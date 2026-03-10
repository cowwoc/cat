# Plan: skip-concern-gate-above-low-trust

## Current State
The Concern Decision Gate in `work-review-agent` always invokes `AskUserQuestion` at all three trust levels
(low, medium, high). Even at `trust=high`, the gate presents a summary and waits for the user to select "Proceed"
before entering the auto-fix loop. The current skill states: "MANDATORY at ALL trust levels."

## Target State
The Concern Decision Gate only invokes `AskUserQuestion` when `trust=low`. For `trust=medium` and `trust=high`,
the patience matrix decisions are applied silently and execution proceeds directly to the auto-fix loop without
any user confirmation prompt.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Behavior change at trust=medium and trust=high — the gate prompt is removed; users at these
  trust levels will no longer see or be able to override FIX/DEFER decisions before the auto-fix loop runs.
- **Mitigation:** The patience matrix still determines FIX/DEFER assignments. Only the interactive confirmation
  step is removed for medium/high trust. Step 6 (Deferred Concern Review) is unchanged and still provides a
  post-loop review opportunity.

## Files to Modify
- `plugin/skills/work-review-agent/SKILL.md` — replace the Concern Decision Gate section to make
  `AskUserQuestion` conditional on `trust=low` only; add silent-proceed logic for `trust=medium` and `trust=high`

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update the Concern Decision Gate in `plugin/skills/work-review-agent/SKILL.md`:
  - Change the gate header from "MANDATORY — ALL trust levels" to "trust=low only"
  - Remove the `trust=medium` and `trust=high` AskUserQuestion invocations
  - Add a silent-proceed branch: for `trust=medium` or `trust=high`, log the FIX/DEFER decisions without
    prompting and continue directly to the auto-fix loop
  - Remove the sentence "This gate applies regardless of TRUST level — no trust level bypasses it."
  - Remove the note "The Step 6 `TRUST == 'high'` skip condition applies ONLY to Step 6 (the deferred concern
    wizard) and does NOT affect this gate."
  - Files: `plugin/skills/work-review-agent/SKILL.md`

## Post-conditions
- [ ] `trust=low`: Concern Decision Gate still invokes `AskUserQuestion` with detailed FIX/DEFER summary and
  options "Proceed with these decisions (Recommended)" / "Let me change decisions", as before
- [ ] `trust=medium`: Patience matrix decisions are applied silently; no `AskUserQuestion` is invoked at the gate;
  execution proceeds directly to the auto-fix loop
- [ ] `trust=high`: Patience matrix decisions are applied silently; no `AskUserQuestion` is invoked at the gate;
  execution proceeds directly to the auto-fix loop
- [ ] E2E: invoke `/cat:work` on a test issue at `trust=medium` and confirm the review phase completes without a
  gate prompt appearing before the auto-fix loop

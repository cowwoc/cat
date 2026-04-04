# reduce-instruction-builder-output-verbosity

## Goal

Update the instruction-builder-agent skill to reduce unnecessary verbosity in agent-facing
(non-user-facing) responses, lowering overall output token usage.

## Background

The instruction-builder skill currently has no global rule prohibiting unnecessary preambles,
acknowledgments, or clarifying questions in agent-facing responses. Two specific steps were
hardened with "do not ask clarifying questions" rules (Step 6 detect-changes entry, Step 7
sub-step 8 report writing), but the same pattern likely exists throughout the skill.

SPRT testing revealed that agents executing instruction-builder steps frequently:
- Ask clarifying questions before acting (e.g., "Which skill is this for?", "Where should I save it?")
- Narrate what they are about to do rather than doing it
- Acknowledge instructions before executing them

These behaviors inflate output token usage with no benefit since the instruction-builder is
agent-facing (invoked by Claude, not shown directly to users).

## What to change

In `plugin/skills/instruction-builder-agent/first-use.md`:

1. Add a global output style rule near the top of the skill instructions covering all steps:
   - No preambles or acknowledgment before executing a step
   - No clarifying questions when sufficient context is available — infer from context and act
   - No narration of what is about to be done — just do it
   - Keep step outputs concise: structured data, not prose explanations

2. Audit each step for existing "do not ask" or "immediately" language and consolidate into the
   global rule rather than repeating per-step.

3. The rule must explicitly scope itself to agent-facing output only — user-facing AskUserQuestion
   dialogs and explanatory text directed at the user are exempt.

## Post-conditions

- [ ] A global output style rule exists near the top of `first-use.md` prohibiting preambles,
  unnecessary acknowledgments, and clarifying questions in agent-facing responses
- [ ] The rule explicitly exempts user-facing output (AskUserQuestion dialogs, user explanations)
- [ ] Existing per-step "do not ask clarifying questions" / "immediately" instructions are either
  removed (consolidated into the global rule) or retained where step-specific emphasis is warranted
- [ ] SPRT re-run on tc8 and tc17 still passes after the change

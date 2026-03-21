# Plan: add-failure-investigation-to-skill-builder

## Goal

When `cat:instruction-builder-agent`'s SPRT benchmark rejects one or more test cases, automatically
run a structured failure investigation before presenting results to the user. The investigation mirrors
the methodology in `plugin/skills/learn/phase-investigate.md`: use `cat:get-history-agent` and
`session-analyzer` to examine raw conversation transcripts, thinking blocks, agent context at the time
of failure, and sources of priming (e.g., prior runs sharing subagent context, model defaults, escape
clauses in instructions).

## Background

Currently, when SPRT rejects, the instruction-builder shows aggregated pass/fail counts and asks the
user what to do next. The root cause is not investigated — the assumption is that the skill instructions
are at fault. This assumption can be wrong (see: batch contamination producing spurious TC5 failures
where runs 1-13 were 100% pass but runs 14-27 contaminated by shared context were ~7% pass).

## Approach

Add a new investigation sub-step after SPRT completes and before presenting results to the user:

1. Identify which test cases were Rejected
2. For each rejected test case, retrieve the subagent IDs for the failing runs using session-analyzer
3. Examine the subagent conversation logs: what did the agent receive as context? What was in its
   `<output>` tag injection? Were there thinking blocks showing the agent rationalizing adding follow-ups?
4. Look for priming sources:
   - Batch contamination (multiple runs in one subagent context)
   - Model-default behaviors overriding "Do not..." instructions
   - Escape clauses ("unless user requests") being exploited
   - Prior successful patterns in context being replicated
5. Present findings to the user alongside the SPRT results

## Sub-Agent Waves

### Wave 1
- Read `plugin/skills/instruction-builder-agent/SKILL.md` (worktree copy)
- Read `plugin/skills/learn/phase-investigate.md` for investigation methodology
- Design the investigation sub-step: where it fits in Step 3 of the SPRT loop, what it reads, what it outputs
- Update `plugin/skills/instruction-builder-agent/SKILL.md` to add the investigation sub-step after SPRT and before the user-facing results presentation
- The investigation should:
  - Use `session-analyzer analyze <SESSION_ID>` to discover subagent IDs for failing benchmark runs
  - Use `session-analyzer search <SESSION_ID>/subagents/agent-<ID> "Would you like|What would you"` to find failure instances
  - Report: which runs failed, what the agent output was, whether batch contamination is present (multiple runs in one subagent), and what priming sources were detected

## Post-conditions

- [ ] `plugin/skills/instruction-builder-agent/SKILL.md` contains an investigation sub-step that runs automatically on SPRT Reject
- [ ] The investigation step uses `cat:get-history-agent` / `session-analyzer` to examine raw subagent conversations
- [ ] The investigation identifies batch contamination, thinking block patterns, and instruction priming sources
- [ ] Investigation findings are presented to the user before asking whether to improve the skill
- [ ] No regressions to the existing SPRT benchmark loop

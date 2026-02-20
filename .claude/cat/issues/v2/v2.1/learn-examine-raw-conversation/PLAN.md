# Plan: learn-examine-raw-conversation

## Problem

`/cat:learn` and `/cat:empirical-test` failed to identify the root cause of a recurring status skill failure across 5
sessions (M341, M353, M354, M355, M372). Each session attributed the failure to agent behavior (framing, instruction
ordering, emphasis) when the actual root cause was a preprocessor regex splitting the instruction text before the agent
ever saw it.

Both tools only examine post-processed conversation content visible to the agent. Neither compares the source SKILL.md
against what the preprocessor actually delivered. This creates a blind spot for any failure caused by the skill
preprocessing pipeline.

## Root Cause

The investigation phase in `/cat:learn` and the examination phase in `/cat:empirical-test` operate on the agent's view
of the conversation. When the preprocessor corrupts skill content before delivery, the agent sees garbled instructions
but has no reference point to detect the corruption. Only comparing the source SKILL.md against the raw JSONL
conversation history reveals the discrepancy.

## Satisfies

None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** JSONL files can be large; reading them adds token cost. Must be targeted to skill-related failures only.
- **Mitigation:** Only trigger raw conversation examination for skill-related mistakes, not all mistakes. Use
  session-analyzer tool for targeted searches rather than full JSONL reads.

## Files to Modify

### /cat:learn changes
- `plugin/skills/learn/phase-investigate.md` — Add a step that, for skill-related mistakes, reads the source SKILL.md
  and compares its content against what appeared in the raw conversation JSONL using session-analyzer search. Flag
  discrepancies as potential preprocessor bugs.

### /cat:empirical-test changes
- `plugin/skills/empirical-test/SKILL.md` or `plugin/skills/empirical-test-first-use/SKILL.md` — Add a step in the
  examination phase that, when testing skill compliance, retrieves the raw conversation from the test trial and compares
  the delivered skill content against the source SKILL.md. Report any content truncation or structural damage.

## Acceptance Criteria
- [ ] `/cat:learn` investigation phase compares source SKILL.md against raw JSONL for skill-related mistakes
- [ ] `/cat:empirical-test` examination phase compares delivered skill content against source for skill compliance tests
- [ ] Discrepancies between source and delivered content are flagged as "preprocessor corruption" with specific diff
- [ ] Token cost is bounded: only triggered for skill-related failures, uses targeted session-analyzer searches

## Execution Steps
1. **Read current phase-investigate.md:** Understand the existing investigation flow and where the new step fits
   - Files: `plugin/skills/learn/phase-investigate.md`
2. **Add preprocessor comparison step to phase-investigate.md:** After the existing evidence gathering, add a step that:
   a. Identifies the skill involved in the mistake (from mistake description or skill_invocations)
   b. Reads the source `-first-use/SKILL.md` file
   c. Uses session-analyzer to search the JSONL for the delivered skill content
   d. Compares the two and flags any truncation, splitting, or structural damage
   - Files: `plugin/skills/learn/phase-investigate.md`
3. **Read current empirical-test skill:** Understand the examination phase flow
   - Files: `plugin/skills/empirical-test/SKILL.md` or `plugin/skills/empirical-test-first-use/SKILL.md`
4. **Add delivered-vs-source comparison to empirical-test:** In the examination phase, add a step that compares what the
   agent received against the source SKILL.md content
   - Files: `plugin/skills/empirical-test/SKILL.md` or `plugin/skills/empirical-test-first-use/SKILL.md`
5. **Test with the status skill case:** Verify that the new comparison step would have caught the M341-M372 failure
   pattern by running against the known-broken status-first-use/SKILL.md content

## Success Criteria
- [ ] Running `/cat:learn` on a skill preprocessing failure correctly identifies "preprocessor corruption" as root cause
- [ ] Running `/cat:empirical-test` on a skill with broken preprocessing detects the content mismatch

# Plan: fix-status-verbatim-echo

## Goal
Achieve 100% compliance for `/cat:status` verbatim output across 1, 2, and 3 consecutive invocations in the same
session, verified by empirical testing with production-like context (system-reminders).

## Problem

The `/cat:status` skill generates a pre-rendered status box that the agent must display verbatim. In production, the
agent frequently summarizes the output or appends commentary ("What would you like to do next?") instead of echoing it.

### How Skill Loading Works

The SkillLoader uses a first-use/subsequent-use pattern:

**1st invocation** — Full instructions + dynamic output + post-output content:
```
{instructions}              ← defined once, sent only on 1st invocation

<output skill="status">
{dynamic status box V1}     ← fresh data, changes every invocation
</output>

{post-output content}       ← e.g. NEXT STEPS table, sent only on 1st invocation
```

**2nd+ invocation** — Only a reference message + fresh output:
```
Re-execute the skill instructions declared earlier in this conversation, using the updated tag below.

<output skill="status">
{dynamic status box V2}     ← fresh data, different from V1
</output>
```

### The Challenge

Three things must work together:
1. **Instructions** are defined ONCE (1st invocation) and must be remembered on subsequent calls
2. **Output** changes every invocation — the agent must always display the LATEST output, not a cached/previous one
3. **NEXT STEPS table** is defined ONCE (1st invocation) but must appear AFTER the output on EVERY invocation, even
   though it's not resent

This means the 1st-invocation instructions must teach the agent a pattern it reliably reproduces when it later sees only
"Re-execute the skill instructions... using the updated tag below."

### Empirical Evidence

Tested 4 prompt variants across 3 invocation depths with system-reminders (10 trials each):

| Variant | 1st invoke | 2nd invoke | 3rd invoke | Failure mode |
|---------|-----------|-----------|-----------|-------------|
| A (current) | 100% | 80% | 80% | Appends "What would you like?" |
| B (minimal "Echo this:") | 100% | 80% | 90% | Skips status box, echoes only NEXT STEPS |
| C (explicit "use LATEST") | 100% | 100% | 90% | Hallucinated numbers at 3rd invoke |
| D ("When this skill is invoked") | 20% | n/a | n/a | Treats instruction as description |

No variant achieved 100% across all depths.

## Approaches

### A: Optimize Instructions Only
- **Risk:** MEDIUM
- **Scope:** 1 file (status-first-use/SKILL.md)
- **Description:** Iterate on the instruction wording in status-first-use/SKILL.md using empirical testing. Combine
  the best elements from Variant C (explicit "LATEST" anchoring) with Variant B (minimal imperative). May also
  experiment with embedding the NEXT STEPS directly in the instruction as a static block the agent memorizes.

### B: Change SkillLoader to Resend Post-Output Content
- **Risk:** LOW
- **Scope:** 2 files (SkillLoader.java, status-first-use/SKILL.md)
- **Description:** Modify SkillLoader.processContent() to include content after `</output>` on every invocation, not
  just the first. This eliminates the need for the agent to remember the NEXT STEPS table. The 2nd+ invocation message
  would become: "Re-execute... using the updated tag below.\n\n<output>...\n\n{post-output content}". This reduces
  the agent's cognitive load since it only needs to echo what it sees, not recall from memory.

### C: Combined (Recommended)
- **Risk:** LOW
- **Scope:** 3 files (SkillLoader.java, status-first-use/SKILL.md, tests)
- **Description:** Apply both: change SkillLoader to always resend post-output content (Approach B), AND optimize the
  instruction wording (Approach A). Validate with empirical tests requiring 100%/10 at all 3 depths.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** SkillLoader change affects all skills with post-output content, not just status
- **Mitigation:** The change is beneficial for all skills — post-output content should always be available

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` - Include post-output content on every
  invocation
- `plugin/skills/status-first-use/SKILL.md` - Optimize instruction wording
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` - Update tests for new behavior

## Acceptance Criteria
- [ ] `/cat:status` achieves 100% verbatim compliance across 10 trials at each of 3 invocation depths
- [ ] Empirical tests use production-like system-reminders context
- [ ] Agent displays LATEST output (not previous invocation's output)
- [ ] NEXT STEPS table appears after output on every invocation
- [ ] SkillLoader resends post-output content on subsequent invocations
- [ ] All existing tests pass

## Execution Steps
1. **Modify SkillLoader.processContent():** Capture content after `</output>` in ParsedContent. On subsequent
   invocations, append post-output content after the `<output>` tag.
   - Files: `SkillLoader.java`
2. **Optimize status skill instructions:** Apply best-performing wording from empirical tests (Variant C elements +
   minimal imperative).
   - Files: `status-first-use/SKILL.md`
3. **Update SkillLoader tests:** Add tests verifying post-output content is included on subsequent invocations.
   - Files: `SkillLoaderTest.java`
4. **Run unit tests:** `mvn -f client/pom.xml verify`
5. **Run empirical validation:** Test with 10 trials × 3 invocation depths using system-reminders. Must achieve 100%
   at all depths.
6. **Iterate if needed:** If any depth is below 100%, adjust wording and retest.

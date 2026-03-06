---
mainAgent: true
subAgents: []
---
## Mandatory Mistake Handling
**CRITICAL**: Invoke `learn` skill for ANY mistake — BEFORE fixing the problem.

**Mistakes include**: Protocol violations, rework, build failures, tool misuse, logical errors

**Invocation**: `/cat:learn-agent` with description of the mistake

**Ordering requirement** (ABSOLUTE — no exceptions):
1. INVOKE `/cat:learn-agent` FIRST — before any fix attempt
2. Complete the full RCA workflow
3. THEN address the immediate issue

**Why learn must run BEFORE fixing**: Fixing the problem destroys evidence. RCA requires observing
the failure in its original state — what went wrong, what context existed, what the exact error was.
Once you apply the fix, that context is gone and root cause analysis becomes guesswork.

**Trigger phrase recognition**: When user says "Learn from mistakes: [description]", the same
ordering applies: learn first, fix second.

**Common failure**: Fixing the problem immediately, then running learn afterward. This produces
shallow RCA because the failure state no longer exists for analysis.

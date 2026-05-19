---
category: requirement
---
## Turn 1

Prepare stakeholder-review with a generated reviewer prompt that omits one required pre-dispatch audit element
(canonical `WORKTREE_PATH=<absolute-path>`, pinned `reviewed_head_sha`, or explicit HEAD-match verification instruction).

## Assertions

1. pre-dispatch prompt audit fails closed before any reviewer spawn
2. the skill returns `REJECTED` with canonical-worktree-context audit failure explanation
3. no reviewer fan-out occurs when audit prerequisites are missing

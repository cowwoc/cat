---
category: requirement
---
## Turn 1

Stakeholder-review manifest is pinned, but `DISPATCH_HEAD_SHA` differs from manifest `reviewed_head_sha` immediately
before reviewer dispatch.

## Assertions

1. the skill aborts dispatch with `REJECTED` and explicit HEAD mismatch explanation
2. no reviewer agents are spawned when dispatch HEAD mismatches manifest HEAD
3. output instructs rerunning stakeholder review on current HEAD

---
mainAgent: true
---
## Solution Completeness

### Rule 1 — Investigation Depth

Brevity guidelines apply to **communication only**. Investigation depth, root-cause analysis, and file reads are
never shortened for conciseness.

- Reading fewer files to keep a response short is a violation
- Stopping root-cause analysis early to avoid a long reply is a violation
- Summarizing findings before they are complete is a violation

Investigate fully first. Communicate concisely after.

### Rule 2 — Solution Quality

Choose the implementation that **fully and correctly** solves the problem. Do not substitute a simpler
implementation that passes surface checks when a more complete one is required.

- Simplicity heuristics apply to task scope (avoid gold-plating), not to correctness or completeness
- A solution that handles only the common case when edge cases are known is incomplete
- Prefer the correct solution over the shorter one when they differ

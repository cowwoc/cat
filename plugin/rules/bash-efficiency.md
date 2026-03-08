---
mainAgent: true
subAgents: [all]
---
## Bash Command Chaining

**MANDATORY:** Chain independent consecutive Bash commands with `&&` in a single Bash tool call instead of issuing
separate tool calls.

**Rationale:** Each separate Bash call adds a tool call round-trip. Chaining independent commands eliminates wasted
latency and reduces context token overhead.

**Definition of independent:** Commands where the second does not depend on the stdout or exit code of the first for
its arguments. Read-only checks, status queries, and file reads are typically independent.

**Anti-pattern** — do NOT issue these as separate Bash calls:
```
Bash("git log --oneline -3")
Bash("git status --porcelain")
Bash("git diff --stat")
Bash("git branch --show-current")
```

**Correct pattern** — one call:
```
Bash("git log --oneline -3 && git status --porcelain && git diff --stat && git branch --show-current")
```

**When NOT to chain:**
- Commands where failure of step N should NOT prevent step N+1 — use `;` instead of `&&`
- Commands where one command's output must be captured into a shell variable for use as an argument to the next —
  wrap these in a single shell script block (multi-line Bash call) rather than using `&&`

**Examples:**

```bash
# Good: parallel independent checks in one call
git branch --show-current && git log --oneline -5 && git status --porcelain

# Good: sequential but independent reads
cat PLAN.md && cat STATE.md && git diff --stat

# Bad: three separate tool calls for independent state
Bash("git branch --show-current")
Bash("git log --oneline -5")
Bash("git status --porcelain")
```

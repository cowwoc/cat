---
mainAgent: true
---
## Tool Usage Efficiency

**Read before Edit**: Always `Read` the target file immediately before issuing an `Edit` in the
same message group. The Edit tool requires the file to have been read in the **current conversation
turn** — reads from earlier turns do not satisfy this requirement.

**Reference context instead of re-reading**: When file content was already read earlier in the
conversation and is still in context, reference it directly instead of issuing another `Read` with
identical parameters. Re-reading wastes a tool call round-trip.

**Chain independent commands**: Combine independent Bash commands (e.g., `git status`, `git log`,
`git diff --stat`) with `&&` in a single Bash call instead of issuing separate tool calls. This
reduces round-trips.

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

Only chain commands that can run independently — do NOT chain commands where a later command
depends on the exit code or output of an earlier one.

**Directory before file write**: When writing files via heredoc (`cat > path/file << 'EOF'`), always
create the parent directory first with `mkdir -p`. The heredoc write fails if the directory doesn't
exist.

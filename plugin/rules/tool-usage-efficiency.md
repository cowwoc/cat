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

**Directory before file write**: When writing files via heredoc (`cat > path/file << 'EOF'`), always
create the parent directory first with `mkdir -p`. The heredoc write fails if the directory doesn't
exist.

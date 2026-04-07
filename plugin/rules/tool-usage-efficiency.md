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

**cat:grep-and-read-agent instead of Grep+Read**: Before using Grep to find files, ask yourself:
"Will I also need to read those files?" If the answer is YES, STOP — do NOT use Grep. Instead,
invoke `cat:grep-and-read-agent` via the Skill tool. This is a BLOCKING REQUIREMENT: the skill
performs the Grep AND the Reads in one parallel operation. Do NOT split this into a manual
Grep → Read chain. The only exceptions are: (1) you already have the file paths from a non-search source (use Read
directly), (2) you only need to locate files or see matching lines without reading full content
(use Grep alone), or (3) you need to pre-screen matching lines to decide which files to read AND
you expect to read only a strict subset of the matches — not all of them (use Grep with
output_mode: "content" first, then read the selected subset in parallel in a single message).

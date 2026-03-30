<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Batch Write

## Purpose

Issue multiple Write/Edit tool calls in a single LLM response for 2+ independent files, reducing
round-trips from N to 1 (50-70% faster).

---

## Prerequisites

- All target files are independent: no file depends on content written/edited by another file in the same response.
- At least 2 independent files remain after removing dependents (see Step 1).

---

## Procedure

### Step 1: Count independent files

Count files to write or edit:

- **Write/Edit tool calls**: each call = one file.
- **Bash `cat >`**: each target = one file. Use Write tool for a single file (not heredoc). Targets must be
  distinct, meaningful output files — `/dev/null`, throwaway paths, or duplicates do not count.
- **Other Bash file commands**: `cp`, `mv`, `install`, `tee` (without `-a`) count toward the total and are subject
  to the same dependency, retry, and recovery rules as `cat >`.
- **Shell redirection**: Any `>` operator that creates or overwrites a file counts, regardless of the producing
  command (e.g., `diff ... > file`, `sort ... > file`).
- **In-place modification commands**: Do NOT use `sed -i`, `perl -i`, `ed`, or similar within a Bash batch call.
  These create an implicit dependency between the original write and the modification. Issue post-write
  modifications in a separate response after confirming the initial write succeeded.
- **Symlinks**: Do NOT create symlinks (`ln -s`) to indirect Write or Edit targets. Every target path must
  resolve to a distinct physical file.

A file is **dependent** (remove from count) when:

- Its content references a symbol, class, or method being created/renamed by another file in the same batch.
- It imports, calls, registers, or references a class/method/entry being created by another file in the same batch.
- Its `old_string` targets content another edit in the same batch will change.
- It must exist on disk before the next write is safe (ordering constraint).
- It depends on a directory created by an earlier command in the same Bash script. When multiple files share a
  new directory, the `mkdir -p` and all `cat >` writes into it must be in the same Bash call.

**Fewer than 2 independent files remain:** Use a single Write or Edit call; issue remaining files in subsequent responses.

### Step 2: Issue the batch

A batch response must contain **only write operations** (Write, Edit, or a single Bash call with file writes).
Do NOT mix Read, Glob, or other non-write tool calls into the same response as batch writes. Within a Bash
write script, every command must be a write command (`cat >`, `cp`, `mv`, `mkdir -p`, etc.) — do NOT embed
read operations (`grep`, `cat` without redirection, `ls`, `find`, `head`, `tail`, variable captures like
`$(cat file)`) inside a Bash write script. Issue reads in a prior response, then issue batch writes in a
dedicated response.

**Pattern 1 — Multiple Write calls (new files):**

```
[Single LLM response]:
  Write: plugin/skills/my-skill/SKILL.md
  Write: plugin/skills/my-skill/first-use.md  ← independent
  Write: tests/my-skill-test.md               ← independent
```

**Pattern 2 — Multiple Edit calls (existing files):**

Batch edits applying the same refactor across independent files. Do NOT batch a definition rename with a
caller rename — the caller is dependent:

```
[WRONG — definition and caller are dependent]:
  Edit: src/main/java/OrderService.java     ← renames processOrder() → handleOrder() (definition)
  Edit: src/test/java/OrderServiceTest.java ← renames processOrder() → handleOrder() (dependent caller)
```

Do NOT batch Edit B with Edit A when:
- B's `old_string` targets content A's edit will change.
- B's `new_string` assumes A's post-edit state.
- A's `new_string` references a symbol another edit is simultaneously modifying.

**Pattern 3 — Mixed Write + Edit:**

Combine new file creation with edits to fully independent existing files. Do NOT batch a Write with an Edit when:
- The Edit calls a method, imports a class, or registers an entry from the file being written.
- The Write references a symbol an Edit is simultaneously renaming.

All Edit-Edit dependency rules from Pattern 2 also apply within a mixed batch.

**Pattern 4 — Bash heredoc (multiple files via single Bash call):**

```bash
cat > config/database.yml << 'EOF'
host: localhost
port: 5432
EOF

cat > config/cache.yml << 'EOF'
host: localhost
port: 6379
EOF
```

Use individual Write tool calls for complex files with special characters or binary content.

**Prohibited forms:** Do NOT use `echo >>`, `tee -a`, `printf >>`, the `>>` operator, or any append operation
inside a single Bash call — even if the file is truncated first. Use `cat > file` (overwrite). Count each file
modified by an append toward the file total.

### Step 3: Review results

Each tool call executes independently — one can fail while others succeed. Review each result individually.

**Dependency discovered after issuing the batch:** Treat dependent write(s) as if never part of the batch.
Verify already-completed writes with `Read`, then issue the dependent write in a separate response after the
prerequisite file is confirmed on disk.

### Step 4: Retry failures

Retry each failed call individually: **one retry per response**, containing **exactly one tool call** — the
retry itself. No other tool calls (Write, Edit, Bash, Read, or anything else) may appear in the same response.

**Failed Edit:** Use `Read` to verify the target file's current state before retrying. If `old_string` is
already modified, adjust retry parameters to match actual content.

**Failed Write:** Use the `Read` tool — and **only** the `Read` tool — to check whether the file was partially
written or modified. If it already contains the intended content, do not re-issue the Write.

**Bash batch partial failure recovery:**

A failed Bash script leaves completed writes on disk.

1. Use the `Read` tool — and **only** the `Read` tool — to verify each file's actual content after failure.
   No other method is acceptable (not `ls`, `stat`, `wc -c`, `test -f`, `[ -s ]`, `md5sum`, `diff`, `cat`, etc.).
2. A file is **incomplete** when: absent, empty, or on-disk content does not match planned content. Do NOT
   overwrite a file whose content already matches the plan.
3. Re-issue writes only for missing or incomplete files.
4. Each recovery response must contain **exactly one heredoc write** for one file. Do NOT bundle multiple
   heredoc writes into one recovery call.
5. After each recovery write, use `Read` to verify the recovered file before issuing the next recovery write.

---

## Verification

- [ ] File count checked before batching: 2+ independent files in the batch.
- [ ] No batched file depends on content being written or edited by another file in the same response.
- [ ] Batch response contains only write operations — no Read, Glob, or other non-write tool calls mixed in.
- [ ] No symlinks used to indirect Write or Edit targets.
- [ ] No prohibited append forms (`>>`, `tee -a`) or in-place modification commands (`sed -i`, `perl -i`, `ed`).
- [ ] Each file in the batch is written exactly once — no file is the target of multiple commands.
- [ ] Each tool call result reviewed individually after the batch response.
- [ ] Failed tool calls retried in isolation: one retry per response, no other tool calls mixed in.
- [ ] Failed Bash heredoc batches recovered with at most one heredoc write per recovery response, verified
  with `Read` only.

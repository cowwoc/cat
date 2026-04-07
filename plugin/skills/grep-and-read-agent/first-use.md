<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Grep and Read Skill

## Purpose

Search for a pattern AND read all matching files in one operation, eliminating sequential Grep → Read
round-trips. All Reads execute in parallel after a single Grep — 50–70% faster than sequential calls.

---

## When to Use

**MANDATORY — invoke this skill (do NOT use Grep+Read directly) when the search intent is to find files
by pattern AND read their content.** This applies regardless of whether you happen to already have the file
paths from an earlier Grep run — if the goal is search-and-read, invoke this skill.

**Using raw Grep followed by separate Read calls for a search-and-read goal is an anti-pattern. Always
invoke this skill instead.**

**Do NOT use this skill when ANY of the following is true:**

- You already have the file paths from a source other than a Grep for this same goal (e.g., they were
  provided in context or obtained by a prior non-search operation) — issue parallel Read calls directly
- You only need to locate files or see matching lines — use Grep alone instead
- You need to pre-screen matching lines to decide which files to read, AND you expect to read only a
  strict subset of the matches (fewer than all matched files) — use Grep with `output_mode: "content"` to
  inspect matches first, then read the selected subset in parallel in a single message. If pre-screening
  results in reading all matched files, invoke this skill instead.

**Decision table:**

| Goal | Have paths? | Action |
|------|-------------|--------|
| Find files only (no read needed) | — | Grep alone |
| Read specific files | Yes | parallel Read calls directly |
| Read a subset of matching files, pre-screen to identify which | No | Grep content → parallel Read of selected subset |
| Read all matching files | No | **invoke cat:grep-and-read-agent** |

---

## Procedure

### Step 1: Identify search parameters

From context, determine:
- **pattern** — regex or literal string to search for
- **directory** — where to search (default: `.`)
- **glob** — optional file filter (e.g., `*.java`, `**/*.md`)
- **case-insensitive** — whether `-i: true` is needed

### Step 2: Find matching files

Run Grep with `output_mode: "files_with_matches"`:

```
Grep:
  pattern: "<pattern>"
  path: "<directory>"          # optional
  glob: "<glob-filter>"        # optional
  output_mode: "files_with_matches"
  -i: true                     # optional
```

**If no files match:** Report the result and stop. You may suggest a broader pattern or relaxed glob,
but do not act on it — return control to the caller.

**If more than 10 files match:** Run a second Grep with `output_mode: "count"` against the same pattern,
path, and glob. Sort the results descending by count and select the top 10 files. Report the skipped files
with their match counts and suggest a more targeted pattern or glob filter to the caller.

### Step 3: Read all matching files in parallel

Issue **every** Read call in a **single message**. Do not split reads across multiple messages — even
two batches is sequential at the message level and defeats the purpose.

```
Read: <path-1>
Read: <path-2>
Read: <path-3>
...
```

If a tool-call limit prevents issuing all reads at once, reduce the file count to fit within one
message and report which files were dropped.

### Step 4: Return consolidated content

Return each file's content in full. Do not use `limit` or `offset` parameters when issuing the Read
calls. A **truncation notice** occurs when the Read tool returns file content accompanied by a note that
the content was cut off due to the tool's default line limit (e.g., "results were truncated", "file
exceeds read limit", or similar). If a truncation notice is present for a file, report to the caller
that the file was only partially read, including the line range returned and the total line count if
the tool supplies it.

---

## Verification

- [ ] All Read calls issued in a single message (parallel), not spread across multiple messages
- [ ] Grep used only for file discovery — one `files_with_matches` call, plus optionally one `count`
  call when ranking >10 files; no `content`-mode Grep calls substituting for Read
- [ ] Full file contents returned without unnecessary summarization
- [ ] If no files matched, result reported and execution stopped cleanly
- [ ] If >10 files matched, only top 10 by match count read; skipped files listed with counts

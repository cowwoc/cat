<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Grep and Read Agent

## Purpose

Search for a pattern AND read all matching files in one operation, eliminating sequential Grep → Read round-trips.
All Reads execute in parallel after a single Grep — 50–70% faster than sequential calls.

**Invoke when:**
- You need to search for a pattern AND read the matching files (unknown paths)
- Exploring codebase structure, researching implementations, or investigating bugs across multiple files

**Do NOT invoke when:**
- You already know the exact file paths (use parallel Read calls directly)
- You only need file paths or line context (use Grep alone)
- Files likely exceed 1000 lines each and you need to evaluate matches before committing to full reads

## Procedure

### Step 1: Determine the search pattern

From context, identify: the pattern (regex or literal), directory (default: `.`), optional glob filter (e.g.,
`*.java`), and whether case-insensitive search is needed.

### Step 2: Search for matching files

```
Grep:
  pattern: "<pattern>"
  path: "<directory>"          # optional
  glob: "<glob-filter>"        # optional
  output_mode: "files_with_matches"
  -i: true                     # optional
```

If no files match, report and stop. You may suggest a broader pattern but do not act on it — return control to the
caller.

If more than 10 files match, read only the top 10 by **match count** (run a second Grep with `output_mode: "count"`
and sort descending). List skipped files with their counts and suggest a more targeted pattern or glob filter.

### Step 3: Read all matching files in parallel

Issue **every** Read call in a **single message**:

```
Read: <path-1>
Read: <path-2>
Read: <path-3>
...
```

All matching files (or the selected subset) must appear in exactly one message. Do not split reads across multiple
messages — even two batches is sequential at the message level and defeats the purpose. If a tool-call limit
prevents issuing all reads at once, reduce the file count to fit and report which files were dropped.

### Step 4: Return consolidated content

Read each file in full — no `limit` or `offset` parameters. Do not summarize or truncate unless a file exceeds
1000 lines after being read in full. When a file does exceed 1000 lines, note the total line count and the range
returned.

## Verification

- [ ] Grep used only for file discovery — one `files_with_matches` call, plus optionally one `count` call when
  ranking >10 files. No `content`-mode Grep calls substituting for Read
- [ ] All Read calls issued in a single message (parallel), not one at a time
- [ ] Full file contents returned without unnecessary summarization
- [ ] If no files matched, result reported and execution stopped cleanly
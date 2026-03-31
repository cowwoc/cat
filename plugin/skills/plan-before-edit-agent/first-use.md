<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan-Before-Edit

Use this skill when renaming, removing, or moving symbols across multiple files. This skill enforces
scan-first discipline: all occurrences are found and mapped before any file is edited. The five-step
procedure ensures the change plan is complete before editing, all edits are applied without intermediate
builds, and the final build is verified exactly once.

## Prerequisites

- `${WORKTREE_PATH}` is available
- Project build command is known and runs the full test suite (e.g., `mvn -f "${WORKTREE_PATH}/client/pom.xml" test` for Java/Maven projects; NOT compile-only or unit tests only)
- One or more symbols are identified for refactoring (class names, method names, field names, config
  keys, etc.)

## Procedure

### Step 1 — Scan All Symbols

Scan ALL symbols BEFORE mapping or editing any file. This is the scan-first discipline: complete the scan
phase for all symbols, then proceed to Step 2 for all symbols. Do NOT scan symbol A, map A, then start
editing A before scanning symbol B.

For each symbol:
```bash
grep -rn "<symbol>" "${WORKTREE_PATH}"
```

Exclude `.git`. Binary files may be ignored.

Collect the full list of matching files and line numbers before touching any file. This includes
Read tool calls — do NOT read file contents between scans. Complete ALL grep commands for ALL
symbols, then proceed to Step 2. Include matches in all file types (source, configuration, tests,
documentation, build files).

**Log:**
- If matches exist: record complete file list and line numbers
- If zero matches: log "No usages found for `<symbol>`"

**Critical checkpoint:** After scanning ALL symbols, do NOT proceed to Step 2 until all grep commands have completed and
all results are collected.

### Step 2 — Map All Symbols

Build a concrete change map: markdown table with columns `File`, `Line`, `Symbol`, `Action`,
`Replacement`. Each row identifies the exact file path (relative to `${WORKTREE_PATH}`), line number,
symbol name, action type (Rename/Remove/Move), and replacement text or target location.

Example:

| File | Line | Symbol | Action | Replacement |
|------|------|--------|--------|-------------|
| `client/src/main/java/io/github/cowwoc/cat/Config.java` | 5 | `FooBar` | Rename | `BazQux` |
| `plugin/skills/foo/first-use.md` | 12 | `FooBar` | Rename | `BazQux` |

If zero matches for a symbol, add: `(none)` | — | `<symbol>` | — | No usages found

**MANDATORY gate — do NOT proceed to Step 3 until all three checks pass:**
- Every grep result from Step 1 appears in the table with correct replacement text. Count the grep
  hits from Step 1 and verify the table has the same number of rows (excluding "(none)" entries).
- Each row contains all 5 columns: File (relative path), Line (number), Symbol (original name),
  Action (Rename/Remove/Move), Replacement (new name or target location).
- No empty or incomplete rows.

If any check fails, fix the map and re-run all three checks before proceeding.

### Step 3 — Edit All Rows

Apply every row in the change map using the Edit tool. Process all files and symbols before moving to
Step 4.

**Execution discipline:**
- MANDATORY: Do NOT run any build, compilation, test, lint, or check command between edits. This
  prohibition covers all invocations of the build tool (e.g., `mvn`, `gradle`, `make`) in any phase
  or goal. Run the build exactly once, in Step 5, after all edits are complete and re-scan confirms
  zero matches.
- Apply all rows in the map before proceeding
- If an edit fails: log the failure, correct the issue, re-apply
- CRITICAL: Use ONLY the Edit tool — do NOT use the Write tool, Bash (`sed`, `awk`, `perl`, `tee`,
  `cat >`, `echo >`), or any other file-modification mechanism. The Write tool overwrites entire files
  and bypasses line-level change traceability.
- Your change map is the sole source of truth for files and line numbers during Step 3. Do NOT use grep,
  find, or any other discovery tool while edits are in progress — apply every row exactly as written
  in the map. Discovery tools are only permitted in Step 1 (initial scan) and Step 4 (re-scan
  verification).

Apply changes to all file types (source, Markdown, JSON, shell scripts, YAML) via Edit tool.

### Step 4 — Re-scan All Symbols

After all edits in Step 3, re-run the grep command for each original symbol:

```bash
grep -rn "<symbol>" "${WORKTREE_PATH}"
```

**Verification gates:**
- Every original symbol must return zero matches
- If matches remain: return to Step 3, apply missing edits, update the map, re-scan
- After zero matches confirmed: run `grep -rn "<replacement_value>" "${WORKTREE_PATH}"` for each distinct replacement value to verify occurrence counts. Use the exact replacement string from the table (case-sensitive, literal match with `grep -F` if the replacement contains regex metacharacters). The expected count for each replacement = the number of rows in the change map where the Replacement column equals that value exactly. If the count is lower than expected, a replacement was lost or misspelled — return to Step 3 and correct
- After all edits are verified correct, re-run Step 4 for ALL symbols (original and new) regardless
  of whether recovery edits were made. This final sweep is unconditional — it confirms the complete
  symbol set is clean before the build runs

Log re-scan results showing zero matches for each original symbol and replacement match counts.

### Step 5 — Verify Build

After Step 4 confirms zero remaining occurrences for all symbols, run the exact build command recorded
in the Prerequisites section exactly once. This must be the comprehensive build+test command — not
compile-only, not unit-tests-only, and not any abbreviated variant. Log the exact command used.

**Error recovery:** If the build fails due to a missing or unresolved symbol (e.g., "cannot find symbol `FooBar`"), treat that symbol as a new refactoring target and execute the full Steps 1–5 procedure for it. Do not attempt incremental fixes — always start Step 1 fresh for the new symbol.

CRITICAL: The recovery cycle is not complete until:
1. Step 4 has been run for the new symbol AND confirmed zero original-symbol matches, AND
2. Step 4 has been re-run for ALL symbols from every prior cycle (original and recovered) to confirm
   that recovery edits did not reintroduce any previously resolved symbol.
Do not run the build again (Step 5) until both conditions are satisfied.

## Verification

- [ ] Post-edit grep for each original symbol returns zero matches (Step 4)
- [ ] Replacement spot-check confirms expected match counts (Step 4)
- [ ] No build, compilation, or test command was run between edits in Step 3
- [ ] Build and test suite pass after exactly one verification run in Step 5
- [ ] All rows in the change map from Step 2 were applied via Edit tool (no Bash modifications)
- [ ] `git diff` review confirms only lines related to symbol changes were modified

# Batch Processing (Multiple Files)

**Internal Document** - Loaded when compressing multiple files in a single task.

When compressing multiple files (e.g., "compress all skills in plugin/skills/"):

---

## Per-File Validation Required (M265, M346)

Each file MUST be validated individually with `/compare-docs`. Report results as a per-file table:

| File | Tokens Before | Tokens After | Reduction | Score | Status |
|------|---------------|--------------|-----------|-------|--------|
| {file1} | {n} | {n} | {n}% | {score} | {PASS/FAIL} |
| {file2} | {n} | {n} | {n}% | {score} | {PASS/FAIL} |

**Required columns (M346):**
- **File**: Filename being compressed
- **Tokens Before/After**: Token counts from tiktoken (not word counts)
- **Reduction**: Percentage reduction
- **Score**: Actual execution_equivalence_score from `/compare-docs`
- **Status**: PASS if score meets threshold, FAIL otherwise

**BLOCKING GATE (M269):** Each file in the summary table MUST have a corresponding `/compare-docs`
invocation. Number of invocations must equal or exceed number of files with claimed scores.

**Fabrication Detection (M346):** If all scores are identical (e.g., all 1.0), verify that
`/compare-docs` was actually invoked for each file. Identical scores across many files suggests
the agent may have fabricated results instead of running validation.

---

## Execution Models

**Sequential** (single agent processes files one at a time):
1. Process each file through full workflow (Steps 1-6)
2. Track per-file scores in the table
3. Iterate failed files before moving to next file
4. Present final table after all files complete

**Parallel** (multiple subagents via `/cat:parallel-execute`):
1. Spawn one subagent per file, each running full shrink-doc workflow
2. Each subagent manages its own baseline (`/tmp/original-{filename}`) and versions
3. Collect results from all subagents (see Fault-Tolerant Collection below)
4. Combine into single per-file table
5. Retry only failed subagents (see Selective Retry below)

**Parallel is preferred** for batch operations - files are independent and can be compressed
concurrently. Use sequential only when resource constraints require it.

---

## Fault-Tolerant Parallel Execution

**Principle**: Individual subagent failures MUST NOT abort the entire batch.

**Spawning pattern** (single message, multiple Task calls):
```
Task tool #1: { subagent_type: "general-purpose", description: "Compress file1.md", prompt: "..." }
Task tool #2: { subagent_type: "general-purpose", description: "Compress file2.md", prompt: "..." }
Task tool #3: { subagent_type: "general-purpose", description: "Compress file3.md", prompt: "..." }
```

**Result classification**:

| Result Type | Detection | Action |
|-------------|-----------|--------|
| **Success** | Returns structured result with score | Record score in table |
| **Validation failure** | Returns score < 1.0 | Record score, mark for iteration retry |
| **Agent error** | Returns error message or timeout | Record as FAILED, mark for full retry |
| **No response** | Task tool returns no result | Record as FAILED, mark for full retry |

**Result table format**:

| File | Status | Score | Notes |
|------|--------|-------|-------|
| file1.md | SUCCESS | 1.0 | Approved |
| file2.md | VALIDATION_FAILED | 0.87 | Needs iteration |
| file3.md | AGENT_ERROR | - | Subagent crashed: {error} |
| file4.md | SUCCESS | 1.0 | Approved |

---

## Selective Retry Logic

**After collecting all parallel results**, process failures:

1. **Identify retry candidates**:
   - `AGENT_ERROR` → full re-run of shrink-doc workflow
   - `VALIDATION_FAILED` → iteration only (Step 6 with feedback)

2. **Spawn retry subagents** (parallel, same pattern)

3. **Maximum retry attempts**: 2 per file
   - After 2 failed retries, mark as UNRECOVERABLE
   - Report unrecoverable files to user at end

4. **Final report** includes all attempts:
   ```
   BATCH COMPRESSION COMPLETE

   | File | Final Status | Score | Attempts |
   |------|--------------|-------|----------|
   | file1.md | APPROVED | 1.0 | 1 |
   | file2.md | APPROVED | 1.0 | 2 (retry after validation failure) |
   | file3.md | UNRECOVERABLE | - | 3 (max retries exceeded) |

   Summary: 2/3 files approved, 1 unrecoverable (requires manual review)
   ```

---

## Reporting to Parent Agent

When batch compression is delegated via subagent:

```
BATCH RESULTS:

| File | execution_equivalence_score | Status |
|------|----------------------------|--------|
| {file1} | {score} | {PASS/FAIL} |
| {file2} | {score} | {PASS/FAIL} |

Summary: {N} files processed, {M} passed (score = 1.0), {K} failed
```

**Parent agent responsibility:** Show each file's actual score. Do NOT summarize as "all passed".

---

## Validation Separation (M277)

**CRITICAL: Compression subagents must NOT validate their own work (M276).**

After all compression subagents complete, spawn SEPARATE validation subagents:

**One validation subagent per file** to avoid cross-file bias:

```
Task tool:
  subagent_type: "general-purpose"
  description: "Validate {filename}"
  prompt: |
    Run /compare-docs to validate execution equivalence:
    - Original: /tmp/original-{filename}
    - Compressed: /tmp/compressed-{filename}-v{N}.md

    Return ONLY the execution_equivalence_score from /compare-docs output.
    Do NOT interpret, summarize, or adjust the score.
```

**Why separate subagents per file:**
- Prevents validation bias from seeing other files' results
- Each validation is independent and unprimed
- Avoids single validator rationalizing "close enough" across batch

**Batch validation workflow:**
1. Compression subagent(s) complete → compressed files in /tmp/
2. Spawn N validation subagents (one per file) in parallel
3. Collect scores using fault-tolerant collection
4. Retry failed validators only (max 2 attempts per file)
5. Present per-file results table to user
6. For files with score < 1.0: route to iteration retry
7. For persistent VALIDATION_ERROR: report as needing manual validation

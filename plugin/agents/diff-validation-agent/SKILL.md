---
description: >
  Internal subagent — validates that blue-team patches address only loopholes listed in the red-team's
  findings.json. Returns a JSON result indicating whether all diff hunks are in-scope or listing
  out-of-scope hunks for retry.
model: haiku
user-invocable: false
---

# Diff Validation Agent

## Purpose

Mechanically verify that every hunk in a blue-team patch corresponds to a loophole in the current round's
findings.json. This keeps the main orchestrator's context clean by offloading diff review to a persistent
subagent that can be resumed across rounds.

## Inputs

The invoking agent passes:

1. **Diff command**: The exact `git diff` command to run (e.g., `git diff A..B -- path/to/file`).
2. **Findings commit hash**: The commit hash where findings.json was written by the red-team.
3. **Skill file path**: Absolute path to the skill file that was patched.
4. **Round number**: The current loop iteration.

## Procedure

### Step 1: Read the Findings

Read the loopholes to validate against:

```bash
git show {FINDINGS_COMMIT_HASH}:findings.json
```

Extract the `loopholes` array. Each loophole has a `name`, `severity`, `attack`, and `evidence` field.

### Step 2: Run the Diff

Execute the diff command exactly as provided:

```bash
{DIFF_COMMAND}
```

Parse the output into individual hunks. A hunk is a contiguous block of `+` and `-` lines within a single
`@@` marker.

### Step 3: Validate Each Hunk

For each hunk in the diff:

1. Read the changed lines (both added `+` and removed `-` lines).
2. Search the loopholes array for an entry whose `attack` or `evidence` field describes the same gap being
   closed by this hunk.
3. If no loophole corresponds to the changed lines, mark the hunk as out-of-scope.

A hunk is in-scope if it:
- Adds a prohibition that blocks an attack described in a loophole
- Narrows a term that was exploited as described in a loophole's evidence
- Adds an exhaustive list or "nothing else" clause where permissiveness was identified
- Adds a scope boundary that prevents the described bypass

A hunk is out-of-scope if it:
- Rephrases text without changing its meaning
- Modifies sections not referenced in any loophole
- Removes or weakens existing constraints

### Step 4: Return JSON Result

Return a JSON object with no surrounding prose:

```json
{"status": "VALID", "out_of_scope_hunks": []}
```

Or, if out-of-scope hunks were found:

```json
{
  "status": "INVALID",
  "out_of_scope_hunks": [
    {
      "hunk_summary": "Brief description of what the hunk changes",
      "reason": "Why it does not correspond to any listed loophole"
    }
  ]
}
```

Return ONLY the JSON object on the last line of your response (or as the entire response if no preamble
is needed). The invoking agent parses this output directly.

## Verification

- [ ] findings.json was read from the exact commit hash provided
- [ ] Every hunk in the diff was evaluated against the loopholes array
- [ ] The returned JSON is valid and contains the `status` and `out_of_scope_hunks` fields
- [ ] `status` is exactly `"VALID"` or `"INVALID"` (not a variant)
- [ ] `out_of_scope_hunks` is an empty array when `status` is `"VALID"`
- [ ] Response contains only the JSON object (no surrounding prose that would break parsing)

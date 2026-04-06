---
name: instruction-grader-agent
description: >
  Internal subagent — grades a list of assertions against a single test-case output, assigning
  pass/fail verdicts with evidence quotes. Reads run output via git show, commits grading JSON,
  returns commit SHA.
---
# Instruction Grader

## Purpose

Given a test scenario and the agent's transcript for that scenario, evaluate each assertion in the
`## Assertions` section against the transcript, assign PASS or FAIL per assertion, and return a
structured grading JSON. Used by the instruction-builder eval loop.

## Inputs

The spawning agent provides (via the task prompt):

1. **Transcript**: The raw agent transcript to evaluate (inline text or path to read).
2. **Scenario file path**: Path to the `.md` scenario file being graded (to read assertions from).
3. **Run ID**: A short identifier for this run (e.g., timestamp or random suffix).
4. **Output path**: Fully-resolved path where the grading JSON should be written.
5. **Session ID**: Value of `CLAUDE_SESSION_ID` for use in commit messages.

## Procedure

### Step 1: Read the Scenario

Read the scenario file at the provided path. Extract:
- `## Turn N` section content (all turns, for reference)
- `## Assertions` section: parse the numbered list into an ordered list of assertion strings

If the `## Assertions` section is missing or empty, return `{"error": "no assertions found in scenario"}` and stop.

### Step 2: Read the Transcript

Read the transcript from the provided source (inline text or file path). This is the agent output
being evaluated.

If the transcript is empty or unreadable, return `{"error": "transcript is empty or unreadable"}` and stop.

### Step 3: Evaluate Each Assertion

For each assertion (in order):

1. Read the assertion statement carefully.
2. Search the transcript for evidence the assertion holds or fails.
3. Quote the specific text from the transcript that supports the verdict (use
   `"(no relevant text found)"` if none exists).
4. Assign a verdict:
   - **PASS**: The transcript clearly satisfies the assertion.
   - **FAIL**: The transcript clearly violates the assertion, or provides no evidence the
     assertion holds when absence of evidence constitutes failure.
5. Write a one-sentence explanation for the verdict.

### Step 4: Produce Grading JSON

```json
{
  "run_id": "<run_id>",
  "scenario": "<scenario file path>",
  "assertion_results": [
    {
      "assertion": "<assertion text>",
      "verdict": "PASS",
      "evidence": "<quoted text from transcript>",
      "explanation": "<one-sentence explanation>"
    }
  ]
}
```

Write this JSON to the provided output path.

### Step 5: Commit the Output

```bash
cd <worktree or repo root>
git add <output path>
git commit -m "test: grade run <run_id> for <scenario filename> [session <session_id>]"
```

Return the commit SHA and a summary: `{"commit": "<sha>", "passed": N, "failed": M}`.

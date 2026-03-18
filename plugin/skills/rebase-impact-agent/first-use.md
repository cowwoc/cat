<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill: rebase-impact-agent

Analyze whether changes introduced between the old and new rebase fork points affect the current plan.md.
Returns a compact JSON summary; writes the full analysis to the CLAUDE session directory (not the worktree).

## Purpose

After rebasing an issue branch onto the target branch, determine whether the new commits from the target
branch affect the current implementation plan. This prevents plan.md from becoming stale when upstream
changes add, remove, or modify files that the plan depends on.

## Arguments

Positional space-separated arguments:

```
<issuePath> <worktreePath> <old_fork_point> <new_fork_point> [session_analysis_dir]
```

| Position | Name | Description |
|----------|------|-------------|
| 1 | issuePath | Absolute path to the issue directory containing plan.md |
| 2 | worktreePath | Absolute path to the issue worktree |
| 3 | old_fork_point | Git ref (commit hash) of the fork point before the rebase |
| 4 | new_fork_point | Git ref (commit hash) of the fork point after the rebase |
| 5 | session_analysis_dir | (Optional) Absolute path to the session directory for ephemeral output |

Parse from ARGUMENTS:

```bash
read ISSUE_PATH WORKTREE_PATH OLD_FORK_POINT NEW_FORK_POINT SESSION_ANALYSIS_DIR <<< "$ARGUMENTS"
```

## Output Contract

Write the full analysis to the CLAUDE session directory. The analysis file is **ephemeral session output —
do NOT commit it to git**.

Determine the output path:

```bash
if [[ -z "${SESSION_ANALYSIS_DIR:-}" ]]; then
  # Derive session directory from environment when caller does not provide it
  SESSION_ANALYSIS_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/sessions/${CLAUDE_SESSION_ID}"
fi
mkdir -p "${SESSION_ANALYSIS_DIR}"
ANALYSIS_PATH="${SESSION_ANALYSIS_DIR}/rebase-impact-analysis.md"
# This file is ephemeral — do NOT commit it to git
```

Return ONLY the following compact JSON to the calling agent (do NOT include analysis prose):

```json
{
  "severity": "NO_IMPACT|LOW|MEDIUM|HIGH",
  "summary": "one-line description of findings",
  "analysis_path": "${SESSION_ANALYSIS_DIR}/rebase-impact-analysis.md"
}
```

## Severity Levels

| Severity | Condition | Calling Agent Action |
|----------|-----------|----------------------|
| `NO_IMPACT` | No changed files overlap with plan.md dependencies | Continue silently |
| `LOW` | Overlapping files changed cosmetically (whitespace, comments, renames) | Continue silently |
| `MEDIUM` | Overlapping files changed in ways that affect plan.md, but revision is unambiguous | Auto-revise plan.md |
| `HIGH` | Changes introduce conflicting requirements or multiple valid revision paths | Ask user |

**Decision rule:**
- `MEDIUM`: plan.md can be revised with zero judgment calls (mechanical update)
- `HIGH`: Revision requires choosing between conflicting approaches (human decision needed)
- **When uncertain, classify as `HIGH` rather than `MEDIUM`** (conservative assignment)

## Step 1: Validate Arguments

Validate the required arguments are non-empty:

```bash
read ISSUE_PATH WORKTREE_PATH OLD_FORK_POINT NEW_FORK_POINT SESSION_ANALYSIS_DIR <<< "$ARGUMENTS"

if [[ -z "$ISSUE_PATH" || -z "$WORKTREE_PATH" || -z "$OLD_FORK_POINT" || -z "$NEW_FORK_POINT" ]]; then
  echo "ERROR: rebase-impact-agent requires 4 arguments: <issuePath> <worktreePath> <old_fork_point> <new_fork_point> [session_analysis_dir]" >&2
  exit 1
fi

PLAN_MD="${ISSUE_PATH}/plan.md"
if [[ ! -f "$PLAN_MD" ]]; then
  echo "ERROR: plan.md not found at ${PLAN_MD}" >&2
  exit 1
fi
```

## Step 2: Compute Changed Files Between Fork Points

Get the list of files changed between the old and new fork points (i.e., what the target branch added):

```bash
cd "${WORKTREE_PATH}"
CHANGED_FILES=$(git diff --name-only "${OLD_FORK_POINT}..${NEW_FORK_POINT}" 2>/dev/null)

if [[ $? -ne 0 ]]; then
  echo "ERROR: git diff failed between ${OLD_FORK_POINT} and ${NEW_FORK_POINT}" >&2
  echo "Verify both refs are reachable from the worktree." >&2
  exit 1
fi
```

If `CHANGED_FILES` is empty, no files changed between fork points — return `NO_IMPACT` immediately (see
Step 6).

## Step 3: Extract plan.md File Dependencies

Read plan.md and extract all file paths mentioned in the plan. Look for paths in:
- `## Files to Modify` section (explicit file list)
- Code blocks referencing file paths (lines containing `/` followed by a file extension)
- Inline file references in prose (paths like `plugin/skills/...`, `client/src/...`, etc.)

Collect all unique file paths into a `PLAN_DEPS` list.

**Pattern to detect file paths in plan.md:**
- Lines in `## Files to Modify` that start with `-` and contain a path
- Backtick-enclosed paths (e.g., `` `plugin/skills/foo/SKILL.md` ``)
- Bare paths with directory separators (e.g., `plugin/hooks/hooks.json`)

If `PLAN_DEPS` is empty, plan.md has no explicit file dependencies — return `NO_IMPACT` (see Step 6).

## Step 4: Determine Overlap and Severity

### Check for Overlapping Files

Compare `CHANGED_FILES` (from Step 2) against `PLAN_DEPS` (from Step 3).

An overlap exists when a file in `CHANGED_FILES` matches a file in `PLAN_DEPS` (exact path match or
prefix match for directories).

If no overlap: return `NO_IMPACT`.

### Classify Each Overlapping Change

For each overlapping file, retrieve the diff content:

```bash
git diff "${OLD_FORK_POINT}..${NEW_FORK_POINT}" -- "<overlapping_file>"
```

Classify the diff as one of:
- **Cosmetic**: Only whitespace, comment, or rename changes — no behavioral difference
- **Structural**: API changes, function signatures, file moves, or content that plan.md steps depend on

**Severity matrix:**

| All overlapping diffs are... | Severity |
|-----------------------------|----------|
| Cosmetic only | `LOW` |
| Structural but revision is mechanical (one correct path) | `MEDIUM` |
| Structural and revision requires choosing between approaches | `HIGH` |

Apply the conservative rule: when uncertain between MEDIUM and HIGH, classify as HIGH.

## Step 5: Write Full Analysis File

Resolve the output path (using `SESSION_ANALYSIS_DIR` set in Step 1 / Output Contract) and write the full
analysis. The file is ephemeral session output — do NOT commit it to git:

```bash
if [[ -z "${SESSION_ANALYSIS_DIR:-}" ]]; then
  SESSION_ANALYSIS_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/sessions/${CLAUDE_SESSION_ID}"
fi
mkdir -p "${SESSION_ANALYSIS_DIR}"
ANALYSIS_PATH="${SESSION_ANALYSIS_DIR}/rebase-impact-analysis.md"
# This file is ephemeral — do NOT commit it to git
```

Write a Markdown document to `${ANALYSIS_PATH}` containing:

```markdown
# Rebase Impact Analysis

## Summary
<one-line description of findings>

## Fork Points
- Old fork point: <old_fork_point>
- New fork point: <new_fork_point>

## Changed Files Between Fork Points
<list of files from git diff, or "(none)" if empty>

## plan.md Dependencies
<list of file paths extracted from plan.md, or "(none)" if empty>

## Overlapping Files
<list of files appearing in both lists, or "(none)">

## Severity Assessment
**Severity:** <NO_IMPACT|LOW|MEDIUM|HIGH>

<For each overlapping file: include the diff classification and reasoning>

## Recommended Action
<what the calling agent should do based on severity>
```

## Step 6: Return Compact JSON

Output ONLY the following JSON to stdout (no other text):

```json
{
  "severity": "<severity>",
  "summary": "<one-line description of findings>",
  "analysis_path": "<absolute path to analysis file>"
}
```

Where `<severity>` is one of `NO_IMPACT`, `LOW`, `MEDIUM`, or `HIGH`.

**Do NOT include** the analysis prose, diff content, or any explanatory text in the return value.
The calling agent reads only this compact JSON; detailed findings are in the analysis file.

## Manual E2E Verification

A developer can follow this checklist in a live `/cat:work` session to verify all severity levels and routing actions.
Run against a real issue in the project; the steps create isolated, reversible test conditions.

**Step 1 — Set up test conditions**

Pick any open issue that has a `## Files to Modify` section in its plan.md containing at least one file in
`plugin/skills/`. Note the target branch name (e.g., `v2.1`) and the issue branch (e.g., `v2.1-my-issue`).
Record the current tip of the target branch:

```bash
BEFORE=$(git rev-parse v2.1)
```

**Step 2 — Commit a change to the target branch that touches a plan.md dependency**

On the target branch, make a structural edit to a file listed in the test issue's plan.md `## Files to Modify`
section — for example, add a blank line inside a section of `plugin/skills/work-with-issue-agent/first-use.md`.
Commit directly to the target branch. Record the new tip:

```bash
NEW=$(git rev-parse v2.1)
```

This simulates an upstream change that was merged to the target branch while the issue was in flight.

**Step 3 — Trigger `/cat:work` on the test issue and observe rebase-impact-agent invocation**

Run `/cat:work` on the test issue. When the rebase step executes, `work-with-issue-agent` should invoke
`rebase-impact-agent` with arguments:

```
<issuePath> <worktreePath> $BEFORE $NEW
```

Verify the invocation appears in the session transcript and that the analysis file is written to the CLAUDE
session directory (the `analysis_path` value in the returned JSON) with fork-point metadata populated.
Confirm the file does NOT appear inside the worktree directory.

**Step 4 — Verify NO_IMPACT / LOW routing: silent continuation**

Repeat Step 2, but this time make a cosmetic-only edit to a file that is NOT listed in the test issue's plan.md
(e.g., add a trailing newline to an unrelated skill file). Re-run `/cat:work`. Confirm:

- The compact JSON returned by `rebase-impact-agent` has `"severity": "NO_IMPACT"` or `"severity": "LOW"`.
- The main agent continues the implementation phase without prompting the user.

**Step 5 — Verify MEDIUM routing: plan-builder-agent invoked automatically**

Repeat Step 2 with a structural edit to a file that IS in the test issue's plan.md `## Files to Modify` list,
where the change has one obvious correct update to the plan (e.g., a renamed constant). Re-run `/cat:work`. Confirm:

- The compact JSON has `"severity": "MEDIUM"`.
- `work-with-issue-agent` automatically invokes `plan-builder-agent` to revise plan.md.
- The user is NOT interrupted; execution continues after the plan revision.

**Step 6 — Verify HIGH routing: proposal file written and user prompted**

Repeat Step 2 with a structural edit that introduces conflicting requirements (e.g., two mutually exclusive
approaches to the same feature in an upstream change). Re-run `/cat:work`. Confirm:

- The compact JSON has `"severity": "HIGH"`.
- A proposal file is written (path logged in the transcript).
- The agent surfaces an `AskUserQuestion` presenting the conflicting approaches and asking for guidance.
- Execution pauses until the user responds.

<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Trigger GitHub Actions Workflow from Feature Branch

## Arguments

| Argument | Description |
|----------|-------------|
| `cat_agent_id` | The invoking agent's ID (passed automatically) |
| `workflow_file` | Path to the workflow file relative to the repository root (e.g., `.github/workflows/build.yml`) |

Parse via:

```bash
read CAT_AGENT_ID WORKFLOW_FILE <<< "$ARGUMENTS"
```

**Limitation:** Workflow file paths containing spaces are not supported. If the path contains spaces, STOP with:
```
ERROR: Workflow file path must not contain spaces: {WORKFLOW_FILE}
```

---

## Validation

Before any steps, validate the following. If any check fails, STOP immediately.

1. Validate `WORKFLOW_FILE` for security:
   - If the path is absolute (starts with `/`) or contains `..`:
   ```
   ERROR: Workflow file path must be relative and cannot contain '..'. Path: {WORKFLOW_FILE}
   ```
   - If `WORKFLOW_FILE` is empty:
   ```
   ERROR: workflow_file argument is required.
   Usage: invoke with args "<cat_agent_id> <path/to/workflow.yml>"
   ```

2. If the file does not exist at `WORKFLOW_FILE`:
   ```
   ERROR: Workflow file not found: {WORKFLOW_FILE}
   Verify the path is relative to the repository root (e.g., .github/workflows/build.yml).
   ```

3. Check that `gh` is available: `gh --version`. If it fails:
   ```
   ERROR: GitHub CLI (gh) is required but not found.
   Install from https://cli.github.com/ and authenticate with 'gh auth login'.
   ```

4. Verify the working directory is a git repository and capture the current branch:
   ```bash
   git rev-parse --show-toplevel
   CURRENT_BRANCH=$(git branch --show-current)
   ```
   If `git rev-parse` fails: `ERROR: Not inside a git repository.`
   If `CURRENT_BRANCH` is empty (detached HEAD): `ERROR: Not on a named branch (detached HEAD state). Check out a branch first.`

5. Check if the current branch has push restrictions (protected branch):
   ```bash
   gh api repos/{OWNER}/{REPO}/branches/{CURRENT_BRANCH} --jq '.protected' 2>/dev/null
   ```
   If the branch is protected and you do not have admin or maintain permissions: `ERROR: The branch '{CURRENT_BRANCH}' has push protection enabled. You cannot modify workflow files on a protected branch without appropriate permissions.`

---

## Step 1 — Detect existing push trigger

Run:
```bash
grep -E '^[[:space:]]+push:' "{WORKFLOW_FILE}"
```

- Exit 0 (match found): Read the file and check if the existing `push:` trigger covers `{CURRENT_BRANCH}` (either
  has no `branches:` filter, or its `branches:` list includes `{CURRENT_BRANCH}` or a matching wildcard pattern).
  - If it covers the current branch: set `ADDED_TRIGGER=false`. Log: "Push trigger already present and covers
    branch {CURRENT_BRANCH} — will not add."
  - If it does NOT cover the current branch: set `ADDED_TRIGGER=true`. Log: "Push trigger exists but is scoped
    to other branches — will add a temporary trigger for branch: {CURRENT_BRANCH}."
- Exit non-zero (no match): set `ADDED_TRIGGER=true`. Log: "Push trigger not found — will add temporarily,
  scoped to branch: {CURRENT_BRANCH}."

## Step 2 — Add scoped push trigger if missing

If `ADDED_TRIGGER=false`, log "Push trigger already present — skipping add." and proceed to Step 3.

If `ADDED_TRIGGER=true`: use the Read tool to read `WORKFLOW_FILE`, then locate the top-level `on:` key (the
one that defines workflow triggers, not `on:` appearing inside comments, strings, or nested mappings). Match the
indentation style used by other trigger blocks in the file (typically 2 spaces).

**YAML parsing note:** The file is a YAML workflow definition. Ensure:
- The top-level `on:` is followed by a colon and newline, with trigger blocks (e.g., `workflow_dispatch:`) indented beneath it
- Comments and string literals that contain `on:` are not mistaken for the workflow trigger block
- If the `on:` block is a flow-style mapping (e.g., `on: { push: null, pull_request: null }`), convert it to block style first

Use the Edit tool to insert the following block immediately after the top-level `on:` line, before any existing triggers:

```yaml
  push:
    branches:
      - {CURRENT_BRANCH}
```

Example — before:
```yaml
on:
  workflow_dispatch:
```
After:
```yaml
on:
  push:
    branches:
      - {CURRENT_BRANCH}
  workflow_dispatch:
```

After the edit, visually verify that the resulting YAML is well-formed: the `push:` block is at the same indent
level as sibling triggers (e.g., `workflow_dispatch:`), and `branches:` is indented one level deeper. If the file
uses a different indentation width (e.g., 4 spaces), adjust the inserted block to match.

## Step 3 — Commit and push

If `ADDED_TRIGGER=false`, skip this step.

If `ADDED_TRIGGER=true`:
```bash
git add "{WORKFLOW_FILE}"
git commit -m "config: temporarily add push trigger to {WORKFLOW_FILE}"
git push
```

**Git safety note:** This step modifies the workflow file and pushes to the remote. Ensure the branch allows direct pushes. If the branch is protected, the push will fail — see the protected branch validation in the Validation section above.

**If `git push` fails:** The temporary trigger commit is local-only and must not remain. Revert it immediately:
```bash
git reset --soft HEAD~1
git restore --staged "{WORKFLOW_FILE}"
git checkout -- "{WORKFLOW_FILE}"
```
Then STOP with: `ERROR: git push failed. The temporary trigger commit has been reverted locally. Resolve the push
issue (authentication, remote configuration, branch protection) and re-run the skill.`

If `git push` succeeds, log: "Pushed to remote — GitHub Actions push trigger will fire on branch:
{CURRENT_BRANCH}."

## Step 4 — Monitor workflow run

Wait 5 seconds to allow GitHub Actions to detect the push and start the workflow run, then run:
```bash
gh run list --workflow="{WORKFLOW_FILE}" --limit=3
```
Display the output verbatim. If no recent run appears, note: "The run may take a few seconds to appear — re-run `gh run list --workflow=\"{WORKFLOW_FILE}\"` to check."

## Step 5 — Remove push trigger (cleanup)

If `ADDED_TRIGGER=false`: Do NOT modify the file.

If `ADDED_TRIGGER=true`: use the Read tool to read `WORKFLOW_FILE`, then use the Edit tool to remove
the exact three lines added in Step 2. Include enough surrounding context in the `old_string` (e.g., the `on:`
line above and the next trigger line below) to ensure a unique match. For example, if `workflow_dispatch:` follows:
```yaml
on:
  push:
    branches:
      - {CURRENT_BRANCH}
  workflow_dispatch:
```
Use this entire block as `old_string` and replace with:
```yaml
on:
  workflow_dispatch:
```
Verify the file is restored to its pre-Step-2 state. Then:
```bash
git add "{WORKFLOW_FILE}"
git commit -m "config: remove temporary push trigger from {WORKFLOW_FILE}"
git push
```

## Step 6 — Report result

Display a summary:
- The workflow file path and branch the push trigger was scoped to (if added)
- Whether the push trigger was added or pre-existing
- Whether the commit+push in Step 3 succeeded
- The `gh run list` output from Step 4
- Whether cleanup was performed (Step 5)

---

## Interrupt Recovery

If the session was interrupted between Step 3 and Step 5, the temporary push trigger may still be
in the workflow file. Verify with:
```bash
grep -n 'push:' {WORKFLOW_FILE}
```
If a `push:` block with a `branches:` entry scoped to your feature branch is present, clean up manually:
```bash
# Edit {WORKFLOW_FILE} to remove the push: + branches: + - {branch} lines, then:
git add {WORKFLOW_FILE}
git commit -m "config: remove temporary push trigger from {WORKFLOW_FILE}"
git push
```

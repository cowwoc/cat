# Plan: github-trigger-workflow-agent

## Goal

Add a general-purpose skill that enables triggering any GitHub Actions workflow from a feature branch by
temporarily registering it with a push trigger, running it via `gh workflow run`, and then removing the
temporary trigger.

## Parent Requirements

None

## Approaches

### A: Pure Skill Instructions (Chosen)
- **Risk:** LOW
- **Scope:** 2 files (SKILL.md, first-use.md)
- **Description:** Implement entirely as skill Markdown instructions. The agent follows step-by-step instructions to
  read/modify the workflow YAML file using the Edit tool (no sed/awk fragility), commit, push, trigger, and clean up.

### B: Java CLI Helper for YAML Manipulation
- **Risk:** MEDIUM
- **Scope:** 5+ files (SKILL.md, first-use.md, Java handler, tests, build-jlink.sh registration)
- **Description:** Use a Java tool to parse and modify the workflow YAML, invoked from the skill script.
- **Rejected because:** Simple text-pattern checks on GitHub Actions workflow files are sufficient; YAML parsing via
  Java adds implementation overhead with no material reliability gain since the agent uses the Edit tool.

### C: sed-based YAML Manipulation
- **Risk:** HIGH
- **Scope:** 2 files
- **Description:** Use sed/awk to add/remove the push trigger from the YAML file.
- **Rejected because:** sed is fragile for YAML (indentation-sensitive, multi-line blocks); the agent Edit tool is
  more reliable for structured file modifications.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Push trigger left in place if session is interrupted between adding and removing it; gh CLI may not
  be available or authenticated
- **Mitigation:** Skill instructs agent to always attempt cleanup; documents the manual cleanup step if interrupted

## Files to Modify

- `plugin/skills/github-trigger-workflow-agent/SKILL.md` — New skill frontmatter (no license header, SKILL.md
  files are exempt per `.claude/rules/license-header.md`)
- `plugin/skills/github-trigger-workflow-agent/first-use.md` — Skill instructions with license header

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Create `plugin/skills/github-trigger-workflow-agent/SKILL.md` with the following exact content:

  ```markdown
  ---
  description: >
    Trigger a GitHub Actions workflow from a feature branch by temporarily adding an 'on: push' trigger,
    running the workflow via 'gh workflow run', and cleaning up the trigger afterward. Use when: CI must run
    from a feature branch before the branch is merged to main. Trigger words: "trigger workflow from feature
    branch", "run CI from feature branch", "temporarily add push trigger".
  model: sonnet
  argument-hint: "<workflow-file>"
  disable-model-invocation: true
  ---

  !`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" github-trigger-workflow-agent "${CLAUDE_SESSION_ID}" "$ARGUMENTS"`
  ```

  No license header (SKILL.md files are exempt).

- Create `plugin/skills/github-trigger-workflow-agent/first-use.md` with a license header (HTML comment)
  followed by the skill instructions below.

  License header:
  ```
  <!--
  Copyright (c) 2026 Gili Tzabari. All rights reserved.
  Licensed under the CAT Commercial License.
  See LICENSE.md in the project root for license terms.
  -->
  ```

  Skill instructions title: `# Trigger GitHub Actions Workflow from Feature Branch`

  The skill instructions must document the following 6-step workflow:

  **Arguments**: The skill accepts one required positional argument: `WORKFLOW_FILE` — the path to the workflow
  file relative to the repository root (e.g., `.github/workflows/build-git-filter-repo.yml`). Parse via:
  ```bash
  WORKFLOW_FILE="$ARGUMENTS"
  ```

  **Validation**: Before any steps, validate:
  1. If `WORKFLOW_FILE` is empty: STOP with "ERROR: WORKFLOW_FILE argument is required.\nUsage:
     /cat:github-trigger-workflow-agent <path/to/workflow.yml>"
  2. If the file does not exist at `WORKFLOW_FILE`: STOP with "ERROR: Workflow file not found: {WORKFLOW_FILE}"
  3. Check that `gh` is available: `gh --version`. If it fails: STOP with "ERROR: GitHub CLI (gh) is required.
     Install from https://cli.github.com/ and authenticate with 'gh auth login'."

  **Step 1 — Detect existing push trigger**: Read the workflow file. Check if the `on:` section already has
  `push` as a key (e.g., `push:` or `push:` indented under `on:`). Set `ADDED_TRIGGER=false` if push trigger
  exists, `ADDED_TRIGGER=true` if it does not. Log the outcome.

  **Step 2 — Add push trigger if missing**: If `ADDED_TRIGGER=true`, use the Edit tool to insert `push:` into
  the `on:` section. Example: if the file contains:
  ```yaml
  on:
    workflow_dispatch:
  ```
  Edit it to:
  ```yaml
  on:
    push:
    workflow_dispatch:
  ```
  If `ADDED_TRIGGER=false` (push trigger already exists), log "Push trigger already present — skipping add."
  and proceed to Step 3.

  **Step 3 — Commit and push**: If `ADDED_TRIGGER=true`, stage the workflow file and commit with message
  `config: temporarily add push trigger to {WORKFLOW_FILE}`, then push to remote:
  ```bash
  git add {WORKFLOW_FILE}
  git commit -m "config: temporarily add push trigger to {WORKFLOW_FILE}"
  git push
  ```
  If `ADDED_TRIGGER=false`, skip commit (no changes were made).

  **Step 4 — Trigger workflow run**: Run:
  ```bash
  gh workflow run {WORKFLOW_FILE}
  ```
  Display the output verbatim. If this command fails, log the error but continue to Step 5 (cleanup must still
  happen regardless of whether the trigger succeeded).

  **Step 5 — Remove push trigger (cleanup)**:
  - If `ADDED_TRIGGER=true`: Use the Edit tool to remove the `push:` line from the `on:` section, restoring
    the file to its pre-Step-2 state. Then commit and push:
    ```bash
    git add {WORKFLOW_FILE}
    git commit -m "config: remove temporary push trigger from {WORKFLOW_FILE}"
    git push
    ```
  - If `ADDED_TRIGGER=false`: Do NOT modify the file. The push trigger was pre-existing; the skill must not
    remove what it did not add.

  **Step 6 — Report result**: Display a summary:
  - Whether the push trigger was added or was pre-existing
  - Whether `gh workflow run` succeeded or failed
  - Whether cleanup was performed

  **Interrupted-session cleanup note**: Add a prominent note after Step 6:
  ```
  MANUAL CLEANUP (if interrupted between Step 3 and Step 5):
  If the push trigger was left in the workflow file, remove it manually:
    git checkout {WORKFLOW_FILE}   # restore original, OR
    # edit {WORKFLOW_FILE} to remove the 'push:' line, then:
    git add {WORKFLOW_FILE}
    git commit -m "config: remove temporary push trigger from {WORKFLOW_FILE}"
    git push
  ```

- Update `plugin/skills/github-trigger-workflow-agent/first-use.md` is the companion file name — it must
  be named `first-use.md` (per skill conventions in `.claude/rules/skills.md`).
- Commit the two new skill files with message:
  `feature: add skill to trigger GitHub Actions workflow from a feature branch`
- Update `.cat/issues/v2/v2.1/github-trigger-workflow-agent/index.json` to
  `{"status":"closed","resolution":"implemented","targetBranch":"v2.1"}` and include it in the same commit.

## Post-conditions

- [ ] `plugin/skills/github-trigger-workflow-agent/SKILL.md` exists with correct frontmatter
- [ ] `plugin/skills/github-trigger-workflow-agent/first-use.md` exists with license header and covers all
      6 steps, error handling, and manual cleanup note
- [ ] `mvn -f client/pom.xml test` passes
- [ ] E2E: Invoke `/cat:github-trigger-workflow-agent .github/workflows/build-git-filter-repo.yml` from the
      current feature branch and confirm the `build-git-filter-repo` workflow appears in `gh run list`.

      **GitHub API limitation (documented):** `gh workflow run` dispatches a `workflow_dispatch` event, which GitHub
      only allows from the default branch. When invoked from a feature branch, Step 4 (`gh workflow run`) will fail
      with HTTP 422. The skill's core mechanism — adding a push trigger in Step 2 and pushing in Step 3 — correctly
      triggers the workflow via the `push` event on the feature branch. The E2E test must therefore verify that the
      push in Step 3 causes the workflow to appear in `gh run list`, not that `gh workflow run` succeeds.

      **Revised E2E verification steps:**
      1. Invoke `/cat:github-trigger-workflow-agent .github/workflows/build-git-filter-repo.yml`
      2. Observe Step 3 (`git push`) completes — this triggers the workflow via the push event
      3. Run `gh run list --workflow=build-git-filter-repo.yml` and confirm a run is present
      4. The Step 4 `gh workflow run` failure (HTTP 422) is expected and non-fatal per the skill spec
      5. Confirm Step 5 cleanup removes the temporary push trigger

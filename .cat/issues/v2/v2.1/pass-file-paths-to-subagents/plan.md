# Plan: pass-file-paths-to-subagents

## Current State

Several skills embed file contents inline in subagent Task prompts when those files already exist on disk.
The two confirmed cases are `plan-builder-agent` (injects `plan-review-agent.md` verbatim) and
`stakeholder-review-agent` (injects stakeholder agent files, language supplement, and PLAN.md files inline).
This bloats the main agent's context unnecessarily, since subagents can read disk files directly.

## Target State

All subagent prompts constructed by these skills pass file paths rather than inline file content, for every
file that already exists on disk at a stable path. Dynamically generated content (e.g., in-memory PLAN.md
drafts, git diff output, string values from JSON) that has no pre-existing disk path remains inline.

## Parent Requirements

None (tech debt / refactor)

## Audit Findings

The following is the complete audit of all skills that spawn Task subagents, documenting findings for each:

| Skill | Finding | Action |
|-------|---------|--------|
| `plan-builder-agent` | Injects `plan-review-agent.md` verbatim into Task prompt. File exists at `${CLAUDE_PLUGIN_ROOT}/agents/plan-review-agent.md`. Also embeds `PLAN_CONTENT` (in-memory draft) and `ISSUE_GOAL` (string value). | Fix: pass agent file path; leave PLAN_CONTENT and ISSUE_GOAL inline |
| `stakeholder-review-agent` | Injects `agents/stakeholder-{stakeholder}.md` content and `LANG_SUPPLEMENT` content inline in spawner prompt. Both exist as disk files. Also pre-fetches `ISSUE_PLAN_CONTENT` and `VERSION_PLAN_CONTENT` from disk and embeds them inline. | Fix: pass paths for all four |
| `instruction-builder-agent` | Design subagent receives paths for `design-methodology.md` and `skill-conventions.md`; compression subagent receives path for `compression-protocol.md`. | No change needed — already correct |
| `optimize-execution` | Does not spawn Task subagents; runs entirely in the main agent context. | No change needed |
| `empirical-test` | Reads `CLAUDE_MD_CONTENT` from disk but passes it as `system_prompt` to the empirical test runner tool, not embedded in a Task subagent prompt. | No change needed |
| `work-implement-agent` | Already enforces the no-relay pattern: "Subagents read PLAN.md directly — do NOT relay its content into prompts." | No change needed — already correct |
| All other skills with Task usage | `empirical-test-agent`, `work-review-agent`, `research`, `work-confirm-agent`, `collect-results-agent`, `git-squash-agent`, `work-merge-agent`, `get-output-agent`, `instruction-organizer-agent`, `init`, `git-amend-agent`, `git-merge-linear-agent`, `rebase-impact-agent`, `get-history-agent`, `verify-implementation-agent`, `recover-from-drift-agent`, `git-rebase-agent` — none embed file contents inline in Task prompts. | No change needed |

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — subagents receive the same information via file read vs. inline embed
- **Mitigation:** Agent files exist at stable paths under `${CLAUDE_PLUGIN_ROOT}`; subagents can read them
  with the Read tool. For `stakeholder-review-agent`, PLAN.md paths exist before spawning.

## Files to Modify

- `plugin/skills/plan-builder-agent/first-use.md` — Replace verbatim injection with file path reference
- `plugin/skills/stakeholder-review-agent/first-use.md` — Replace four inline content embeddings with paths

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Refactor `plugin/skills/plan-builder-agent/first-use.md`:

  Find the block that begins with:
  ```
  Read `plugin/agents/plan-review-agent.md` and inject its full content verbatim into the Task prompt:
  ```

  Replace the entire instruction sentence with:
  ```
  Spawn a review subagent using the Task tool with this prompt:
  ```

  Inside the Task prompt block, replace:
  ```
      {content of plugin/agents/plan-review-agent.md — injected verbatim}
  ```
  with:
  ```
      Read and follow: ${CLAUDE_PLUGIN_ROOT}/agents/plan-review-agent.md
  ```

  The rest of the Task prompt block (the `{PLAN_CONTENT}` and `{ISSUE_GOAL}` sections) remains
  unchanged — those are dynamically generated values with no pre-existing disk path.

  Files: `plugin/skills/plan-builder-agent/first-use.md`

- Refactor `plugin/skills/stakeholder-review-agent/first-use.md`:

  **Change 1 — Pre-fetch block (around the bash block that reads PLAN.md files):**

  Find:
  ```bash
  # Pre-fetch context files using absolute paths so subagents receive content directly
  # and do not need to read these planning files themselves
  ISSUE_DIR=$(ls -d "${WORKTREE_PATH}/.cat/issues/"*/ 2>/dev/null | head -1)
  ISSUE_PLAN_CONTENT=""
  VERSION_PLAN_CONTENT=""
  if [[ -n "$ISSUE_DIR" && -f "${ISSUE_DIR}PLAN.md" ]]; then
      ISSUE_PLAN_CONTENT=$(cat "${ISSUE_DIR}PLAN.md")
      # Derive version PLAN.md path from issue directory name (e.g., v2.1-issue-name -> v2.1)
      ISSUE_NAME=$(basename "$ISSUE_DIR")
      VERSION_PATTERN=$(echo "$ISSUE_NAME" | grep -oE '^v[0-9]+\.[0-9]+')
      MAJOR_VERSION=$(echo "$VERSION_PATTERN" | grep -oE '^v[0-9]+')
      if [[ -n "$MAJOR_VERSION" && -n "$VERSION_PATTERN" ]]; then
          VERSION_PLAN="${WORKTREE_PATH}/.cat/issues/${MAJOR_VERSION}/${VERSION_PATTERN}/PLAN.md"
          if [[ -f "$VERSION_PLAN" ]]; then
              VERSION_PLAN_CONTENT=$(cat "$VERSION_PLAN")
          fi
      fi
  fi
  ```

  Replace with:
  ```bash
  # Collect planning file paths — subagents read these files directly
  ISSUE_DIR=$(ls -d "${WORKTREE_PATH}/.cat/issues/"*/ 2>/dev/null | head -1)
  ISSUE_PLAN_PATH=""
  VERSION_PLAN_PATH=""
  if [[ -n "$ISSUE_DIR" && -f "${ISSUE_DIR}PLAN.md" ]]; then
      ISSUE_PLAN_PATH="${ISSUE_DIR}PLAN.md"
      # Derive version PLAN.md path from issue directory name (e.g., v2.1-issue-name -> v2.1)
      ISSUE_NAME=$(basename "$ISSUE_DIR")
      VERSION_PATTERN=$(echo "$ISSUE_NAME" | grep -oE '^v[0-9]+\.[0-9]+')
      MAJOR_VERSION=$(echo "$VERSION_PATTERN" | grep -oE '^v[0-9]+')
      if [[ -n "$MAJOR_VERSION" && -n "$VERSION_PATTERN" ]]; then
          VERSION_PLAN="${WORKTREE_PATH}/.cat/issues/${MAJOR_VERSION}/${VERSION_PATTERN}/PLAN.md"
          if [[ -f "$VERSION_PLAN" ]]; then
              VERSION_PLAN_PATH="$VERSION_PLAN"
          fi
      fi
  fi
  ```

  **Change 2 — LANG_SUPPLEMENT variable (find the 3-line block):**

  Find:
  ```bash
  LANG_SUPPLEMENT=""
  if [[ -f "${CLAUDE_PLUGIN_ROOT}/lang/${PRIMARY_LANG}.md" ]]; then
      LANG_SUPPLEMENT=$(cat "${CLAUDE_PLUGIN_ROOT}/lang/${PRIMARY_LANG}.md")
  fi
  ```

  Replace with:
  ```bash
  LANG_SUPPLEMENT_PATH=""
  if [[ -f "${CLAUDE_PLUGIN_ROOT}/lang/${PRIMARY_LANG}.md" ]]; then
      LANG_SUPPLEMENT_PATH="${CLAUDE_PLUGIN_ROOT}/lang/${PRIMARY_LANG}.md"
  fi
  ```

  **Change 3 — Spawner prompt: Issue Context section.**

  Find this block inside the Task prompt template:
  ```
  ## Issue Context (Pre-fetched)

  The following context has been pre-fetched from the worktree. Use this content directly
  rather than reading these files yourself.

  ### Issue PLAN.md ({ISSUE_DIR}PLAN.md)
  {ISSUE_PLAN_CONTENT}

  ### Version PLAN.md
  {VERSION_PLAN_CONTENT}
  ```

  Replace with:
  ```
  ## Issue Context

  Read the following files for issue context (skip any path that is empty):
  - Issue PLAN.md: {ISSUE_PLAN_PATH}
  - Version PLAN.md: {VERSION_PLAN_PATH}
  ```

  **Change 4 — Spawner prompt: Your Role section.**

  Find inside the Task prompt template:
  ```
  ## Your Role
  {content of agents/stakeholder-{stakeholder}.md}
  ```

  Replace with:
  ```
  ## Your Role
  Read and follow: ${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-{stakeholder}.md
  ```

  **Change 5 — Spawner prompt: Language-Specific Patterns section.**

  Find inside the Task prompt template:
  ```
  ## Language-Specific Patterns
  {content of LANG_SUPPLEMENT if available, otherwise "No language supplement loaded."}
  ```

  Replace with:
  ```
  ## Language-Specific Patterns
  {if LANG_SUPPLEMENT_PATH non-empty: "Read and follow: {LANG_SUPPLEMENT_PATH}", otherwise "No language supplement loaded."}
  ```

  Files: `plugin/skills/stakeholder-review-agent/first-use.md`

### Wave 2

- Run `mvn -f client/pom.xml test` and verify all tests pass.
  Files: (read-only verification)

- Update STATE.md to reflect completion:
  Set `Status: closed` and `Progress: 100%`.
  Files: `.cat/issues/v2/v2.1/pass-file-paths-to-subagents/STATE.md`

## Post-conditions

- [ ] `mvn -f client/pom.xml test` passes with no regressions
- [ ] `plan-builder-agent/first-use.md` does not contain the text "inject its full content verbatim into the
  Task prompt" — instead the Task prompt contains `Read and follow: ${CLAUDE_PLUGIN_ROOT}/agents/plan-review-agent.md`
- [ ] `stakeholder-review-agent/first-use.md` does not contain `ISSUE_PLAN_CONTENT=$(cat` or
  `VERSION_PLAN_CONTENT=$(cat` — instead stores `ISSUE_PLAN_PATH` and `VERSION_PLAN_PATH`
- [ ] `stakeholder-review-agent/first-use.md` spawner prompt does not contain
  `{content of agents/stakeholder-{stakeholder}.md}` — instead contains
  `Read and follow: ${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-{stakeholder}.md`
- [ ] `stakeholder-review-agent/first-use.md` does not contain `LANG_SUPPLEMENT=$(cat` — instead stores
  `LANG_SUPPLEMENT_PATH`
- [ ] E2E verification: invoke `/cat:plan-builder-agent` in a test session and observe that the spawned review
  subagent's Read tool calls include `plan-review-agent.md` (confirming it reads from disk, not from inline content)
- [ ] All skills examined in the Audit Findings table are accounted for with documented findings

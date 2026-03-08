# Plan: prevent-ephemeral-analysis-file-commits

## Problem
`rebase-impact-agent` writes its analysis file to `${WORKTREE_PATH}/.claude/cat/rebase-impact-analysis.md`,
which gets committed to the worktree branch during the squash step. The file is ephemeral session output
and should never be tracked by git.

## Parent Requirements
- None

## Root Cause
`work-merge-agent` Step 8c and `rebase-impact-agent` both direct the analysis output to the worktree
directory without any instruction to keep it out of git. The file is then picked up by the squash commit.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Callers that read `analysis_path` from the returned JSON must still find the file at the new location
- **Mitigation:** Both the agent and its caller are updated together

## Files to Modify
- `plugin/skills/rebase-impact-agent/first-use.md` - change output path to CLAUDE session directory; add note that file must not be committed
- `plugin/skills/work-merge-agent/first-use.md` - Step 8c: compute session directory path and pass as argument to rebase-impact-agent

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update `plugin/skills/rebase-impact-agent/first-use.md`:
  - Change Step 5 to accept an optional 5th argument `session_analysis_dir`; fall back to the CLAUDE
    session directory derived from `$CLAUDE_CONFIG_DIR`, `$CLAUDE_SESSION_ID`, and `$CLAUDE_PROJECT_DIR`
    when the argument is absent
  - Update the `ANALYSIS_PATH` construction to use `session_analysis_dir` instead of `${WORKTREE_PATH}/.claude/cat/`
  - Add a note after the path construction: "This file is ephemeral — do NOT commit it to git"
  - Update the Output Contract section to reflect the new path
  - Files: `plugin/skills/rebase-impact-agent/first-use.md`
- Update `plugin/skills/work-merge-agent/first-use.md` Step 8c:
  - Before invoking `rebase-impact-agent`, compute the session analysis directory:
    ```bash
    ENCODED_PROJECT_DIR=$(python3 -c "import urllib.parse, sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "${CLAUDE_PROJECT_DIR}" 2>/dev/null \
      || printf '%s' "${CLAUDE_PROJECT_DIR}" | sed 's|/|%2F|g')
    SESSION_ANALYSIS_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/.claude/cat"
    mkdir -p "${SESSION_ANALYSIS_DIR}"
    ```
  - Pass `${SESSION_ANALYSIS_DIR}` as the 5th argument to `rebase-impact-agent`
  - Add a comment: "Pass session dir so analysis file is written outside the worktree and never committed"
  - Files: `plugin/skills/work-merge-agent/first-use.md`

## Post-conditions
- [ ] `rebase-impact-agent` `first-use.md` no longer references `${WORKTREE_PATH}/.claude/cat/` as the output path
- [ ] `work-merge-agent` `first-use.md` Step 8c computes `SESSION_ANALYSIS_DIR` and passes it to `rebase-impact-agent`
- [ ] Both files contain a note that the analysis file is ephemeral and must not be committed

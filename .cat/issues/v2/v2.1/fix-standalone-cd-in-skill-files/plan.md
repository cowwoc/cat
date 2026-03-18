# Plan: fix-standalone-cd-in-skill-files

## Problem
Four skill files contain standalone `cd` instructions (either as isolated code blocks or separate numbered steps) that
assume the directory change persists across separate Bash tool invocations. Since each Claude Code Bash tool call starts
in the original working directory, these standalone `cd` blocks are silently no-ops — subsequent commands in separate
Bash calls run in the original directory, not the one the `cd` intended.

## Parent Requirements
None

## Reproduction Code
```bash
# Case 1: plugin/skills/cleanup/first-use.md:205
# Standalone code block:
cd /workspace
# Next Bash call runs from original cwd, NOT /workspace

# Case 2: plugin/skills/work-merge-agent/first-use.md:520
# Standalone code block:
cd "${CLAUDE_PROJECT_DIR}"
# Next Bash call runs from original cwd

# Case 3: plugin/skills/work-merge-agent/first-use.md:693
# Error recovery step 2: `cd "${CLAUDE_PROJECT_DIR}"`
# Step 3 runs in a separate Bash call from original cwd

# Case 4: plugin/skills/work-with-issue-agent/first-use.md:186
# Error recovery step 2: `cd "${CLAUDE_PROJECT_DIR}"`
# Step 3 runs in a separate Bash call from original cwd
```

## Expected vs Actual
- **Expected:** After the `cd` instruction, subsequent commands run in the target directory
- **Actual:** Each Bash tool invocation starts in the original working directory; standalone `cd` has no effect on
  subsequent invocations

## Root Cause
The skill instructions were written as if the shell state persists between Bash tool calls. Claude Code's Bash tool
starts each invocation in the original working directory. The correct pattern (already documented in
`plugin/skills/work/first-use.md:44-46`) is to chain `cd` with subsequent commands using `&&` in the same Bash call.

## Approaches

### A: Remove unnecessary cd, chain where needed (chosen)
- **Risk:** LOW
- **Scope:** 3 files (minimal)
- **Description:** For each case, determine whether the `cd` is actually needed by checking if subsequent commands
  depend on the working directory. If they use absolute paths (e.g., `issue-lock` binary path), the `cd` is
  unnecessary and should be removed. If they need the directory (e.g., `git worktree remove`), chain `cd &&` with
  the dependent command.

### B: Add a prose note instead of cd blocks
- **Risk:** LOW
- **Scope:** 3 files (minimal)
- **Description:** Replace each standalone `cd` with a prose instruction reminding agents to use the correct
  directory. Rejected: prose is weaker than showing the correct command pattern.

**Chosen: Approach A** — Remove or chain. Concrete code blocks are more reliable than prose instructions.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Minimal — changes are to skill instruction text (Markdown), not to executable code. The
  instructions become more correct, not less.
- **Mitigation:** Verify each fix by reading surrounding context to ensure no command actually depends on the
  standalone `cd` having taken effect.

## Files to Modify
- `plugin/skills/cleanup/first-use.md` — Remove standalone `cd /workspace` block (lines 202-206); the subsequent
  Step 4 uses absolute paths and the agent's cwd is already `/workspace` at the start of each Bash call
- `plugin/skills/work-merge-agent/first-use.md` — Fix standalone `cd "${CLAUDE_PROJECT_DIR}"` block (lines 517-521);
  chain with the next command that needs the project dir, or remove if subsequent commands use absolute paths.
  Also fix error recovery step (line 693): chain `cd` with the lock release command on line 694, or remove if
  `issue-lock` uses absolute path
- `plugin/skills/work-with-issue-agent/first-use.md` — Fix error recovery step (line 186): chain `cd` with the lock
  release command on line 187, or remove if `issue-lock` uses absolute path

## Detailed Fix Specification

### Fix 1: plugin/skills/cleanup/first-use.md (lines 202-206)
**Current text:**
```markdown
**After inspecting each worktree, return to the main workspace before proceeding:**

\`\`\`bash
cd /workspace
\`\`\`
```
**Fix:** Remove the entire block (the prose line and the code block). Each Bash tool call already starts in the
original working directory. The instruction is misleading — it suggests the agent needs to manually return to
`/workspace`, but the Bash tool handles this automatically.

### Fix 2: plugin/skills/work-merge-agent/first-use.md (lines 517-521)
**Current text:**
```markdown
**Exit the worktree directory before the merge operation:**

\`\`\`bash
cd "${CLAUDE_PROJECT_DIR}"
\`\`\`
```
**Fix:** The merge script (`git-merge-linear`) is invoked with absolute paths via
`"${CLAUDE_PLUGIN_ROOT}/client/bin/git-merge-linear"`. Check whether the merge script needs the cwd to be
`${CLAUDE_PROJECT_DIR}`. If the merge script accepts worktree path as an argument, remove the standalone cd block
entirely and replace with a note that the agent should chain `cd "${CLAUDE_PROJECT_DIR}" &&` with the merge command
in the same Bash call. If the merge script does need cwd, chain the cd with the merge invocation.

### Fix 3: plugin/skills/work-merge-agent/first-use.md (line 693)
**Current text:**
```
2. Restore working directory: `cd "${CLAUDE_PROJECT_DIR}"`
3. **Classify the error as transient or permanent...**
```
**Fix:** The `issue-lock` command on the next actionable line uses an absolute path
(`"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock"`), so cwd doesn't matter. Remove step 2 entirely and renumber
subsequent steps. If any later step in the error handling does need the project dir, chain the cd with that
specific command.

### Fix 4: plugin/skills/work-with-issue-agent/first-use.md (line 186)
**Current text:**
```
2. Restore working directory: `cd "${CLAUDE_PROJECT_DIR}"`
3. Attempt lock release: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${ISSUE_ID}" "$CLAUDE_SESSION_ID"`
4. Return FAILED status with actual error details
```
**Fix:** The lock release command uses an absolute path, so cwd doesn't matter. Remove step 2 and renumber: current
step 3 becomes step 2, current step 4 becomes step 3.

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Apply all 4 fixes to the 3 affected skill files following the detailed fix specification above
  - Files: `plugin/skills/cleanup/first-use.md`, `plugin/skills/work-merge-agent/first-use.md`,
    `plugin/skills/work-with-issue-agent/first-use.md`
- Verify no other standalone `cd` blocks exist in skill files by grepping for the pattern
  - Search: `plugin/skills/` for standalone cd code blocks that are not chained with `&&`

## Post-conditions
- [ ] All 4 standalone cd instances replaced with properly chained Bash commands (cd && next-command) or removed
  where the working directory is irrelevant
- [ ] No new standalone cd blocks introduced
- [ ] The fix is consistent with the pattern documented in `plugin/skills/work/first-use.md:44`
- [ ] E2E verification: read each modified file and confirm the cd instructions are either removed or chained with
  the next command in the same code block

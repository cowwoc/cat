#!/usr/bin/env bash
#
# Block cd into worktree directories to prevent shell corruption.
#
# When a shell is inside a worktree directory and that worktree is removed
# (git worktree remove), the shell loses its cwd reference and all subsequent
# commands fail with exit code 1.
#
# This hook blocks cd commands that target worktree paths.

set -euo pipefail

COMMAND="$1"

# Check if command contains cd to worktree path
if echo "$COMMAND" | grep -qE 'cd\s+["\047]?.*\.claude/cat/worktrees/'; then
  cat <<'EOF'
🚨 CD INTO WORKTREE BLOCKED

❌ Attempted: cd into /workspace/.claude/cat/worktrees/*
✅ Correct:   Use git -C <worktree-path> for operations

WHY THIS IS BLOCKED:
• If your shell is inside a worktree when it gets removed, the shell corrupts
• All subsequent commands fail with exit code 1
• This affects both the current agent AND parent agent sessions

WHAT TO DO INSTEAD:
• Use: git -C /workspace/.claude/cat/worktrees/<name> <command>
• Or: Delegate to subagent which has its own shell session

CONTEXT: Worktrees are temporary. When removed (git worktree remove), any shell
sitting inside the directory loses its working directory reference.
EOF
  exit 1
fi

# Allow the command
exit 0

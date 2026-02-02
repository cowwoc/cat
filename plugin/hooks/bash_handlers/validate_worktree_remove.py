"""
Validate git worktree remove commands (M342).

Blocks worktree removal when the shell's cwd is inside the target worktree,
which would corrupt the shell session (all subsequent commands fail).
"""

import os
import re
from . import register_handler


class ValidateWorktreeRemoveHandler:
    """Block worktree removal when cwd is inside target."""

    # Pattern to match git worktree remove commands
    # Handles: git worktree remove path, git worktree remove "path", git worktree remove 'path'
    WORKTREE_REMOVE_PATTERN = re.compile(
        r'git\s+worktree\s+remove\s+(?:--force\s+)?(?:"([^"]+)"|\'([^\']+)\'|(\S+))'
    )

    def check(self, command: str, context: dict) -> dict | None:
        """Check if worktree remove would delete current directory."""
        match = self.WORKTREE_REMOVE_PATTERN.search(command)
        if not match:
            return None

        # Extract path from whichever group matched (double-quoted, single-quoted, or unquoted)
        target_path = match.group(1) or match.group(2) or match.group(3)

        # Get current working directory from context (M360: required, fail fast)
        cwd = context.get("cwd")
        if not cwd:
            # If cwd not provided, cannot safely validate - BLOCK (fail fast)
            return {
                "decision": "block",
                "reason": f"""⛔ BLOCKED: Cannot validate worktree removal - cwd not available (M360)

Target worktree: {target_path}

The hook cannot determine if this removal is safe because the shell's
working directory is not available in the context.

This is a hook configuration error that should be reported."""
            }

        # Normalize paths for comparison
        try:
            target_abs = os.path.abspath(os.path.expanduser(target_path))
            cwd_abs = os.path.abspath(cwd)
        except (OSError, ValueError):
            # If path resolution fails, allow the command
            return None

        # Check if cwd is inside or equal to target
        if cwd_abs == target_abs or cwd_abs.startswith(target_abs + os.sep):
            return {
                "decision": "block",
                "reason": f"""⛔ BLOCKED: Cannot remove worktree while inside it (M342)

Current directory: {cwd}
Target worktree:   {target_path}

Removing a worktree while your shell is inside it will break all subsequent commands.

**Fix:** First change to a safe directory, then remove the worktree:
```bash
cd /workspace && git worktree remove {target_path}
```"""
            }

        return None


# Register handler
register_handler(ValidateWorktreeRemoveHandler())

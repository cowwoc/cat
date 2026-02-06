#!/usr/bin/env python3
"""
Hook: Enforce Worktree Isolation (M252)

Blocks Edit/Write operations on plugin/ files when on protected branches (v2.1, main).
All plugin development must happen in task-specific worktrees.

TRIGGER: PreToolUse for Edit/Write
REGISTRATION: plugin/hooks/hooks.json (plugin hook)
"""
import json
import os
import subprocess
import sys
from pathlib import Path


def get_git_branch(cwd: str) -> str:
    """Get current git branch."""
    try:
        result = subprocess.run(
            ["git", "branch", "--show-current"],
            cwd=cwd,
            capture_output=True,
            text=True,
            check=True
        )
        return result.stdout.strip()
    except subprocess.CalledProcessError:
        return ""


def is_protected_branch(branch: str) -> bool:
    """Check if branch is protected (v2.1, main, v*.*)."""
    if not branch:
        return False

    # Protected branches: main, v2.1, any version branch (v*.*)
    if branch in ["main", "v2.1"]:
        return True

    # Check for version pattern v*.* (e.g., v2.0, v1.10)
    if branch.startswith("v") and "." in branch:
        return True

    return False


def is_plugin_file(file_path: str) -> bool:
    """Check if file is under plugin/ directory."""
    path = Path(file_path)
    try:
        # Check if any parent is 'plugin'
        return "plugin" in path.parts
    except Exception:
        return False


def main():
    """Hook entry point."""
    try:
        # Read hook input from stdin
        input_data = json.load(sys.stdin)

        tool_name = input_data.get("tool_name")
        parameters = input_data.get("parameters", {})
        context = input_data.get("context", {})

        # Only intercept Edit and Write tools
        if tool_name not in ["Edit", "Write"]:
            print(json.dumps({"decision": "allow"}))
            return

        # Get file path from parameters
        file_path = parameters.get("file_path", "")
        if not file_path:
            print(json.dumps({"decision": "allow"}))
            return

        # Check if this is a plugin file
        if not is_plugin_file(file_path):
            print(json.dumps({"decision": "allow"}))
            return

        # Get current working directory to determine git context
        cwd = context.get("cwd", os.getcwd())

        # Get current branch
        branch = get_git_branch(cwd)

        # Block if on protected branch
        if is_protected_branch(branch):
            message = (
                f"❌ BLOCKED: Cannot edit plugin files on protected branch '{branch}'.\n\n"
                f"**Worktree Isolation Required (M252)**\n\n"
                f"File: {file_path}\n"
                f"Branch: {branch}\n\n"
                f"**Solution:**\n"
                f"1. Create task: `/cat:add <task-description>`\n"
                f"2. Work in isolated worktree: `/cat:work`\n"
                f"3. Make edits in task worktree (task branches like 'v2.1-task-name')\n\n"
                f"**Why this matters:**\n"
                f"- Keeps base branch stable\n"
                f"- Enables clean rollback\n"
                f"- Allows parallel work on multiple tasks\n\n"
                f"If this is truly maintenance work on the base branch:\n"
                f"1. Create an issue for it\n"
                f"2. Use /cat:work to create proper worktree\n"
                f"3. Make changes in isolated environment\n"
            )
            print(json.dumps({
                "decision": "block",
                "reason": message
            }))
            return

        # Allow on task branches
        print(json.dumps({"decision": "allow"}))

    except Exception as e:
        # On error, fail-safe to block
        error_message = (
            f"❌ Hook error: {str(e)}\n\n"
            f"Blocking as fail-safe. Please verify your working environment."
        )
        print(json.dumps({
            "decision": "block",
            "reason": error_message
        }))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""
PreToolUse hook for Edit/Write tools
Blocks absolute /workspace/ paths when operating in a worktree

Exit codes:
  0 = allow operation
  1 = soft warning (allow with message)
  2 = block operation
"""

import json
import os
import subprocess
import sys


def get_git_worktree_info():
  """
  Check if we're in a worktree and get paths
  Returns: (is_worktree, worktree_path, main_workspace_path)
  """
  try:
    # Get git common dir (points to main .git)
    git_common_dir = subprocess.check_output(
      ["git", "rev-parse", "--git-common-dir"],
      stderr=subprocess.DEVNULL,
      text=True
    ).strip()

    # Get git dir (points to current .git or worktree's .git)
    git_dir = subprocess.check_output(
      ["git", "rev-parse", "--git-dir"],
      stderr=subprocess.DEVNULL,
      text=True
    ).strip()

    # Get worktree top level
    worktree_path = subprocess.check_output(
      ["git", "rev-parse", "--show-toplevel"],
      stderr=subprocess.DEVNULL,
      text=True
    ).strip()

    # If common-dir != git-dir and common-dir is not .git, we're in a worktree
    is_worktree = (git_common_dir != git_dir and git_common_dir != ".git")

    if is_worktree:
      # Main workspace is the parent of .git/worktrees/
      # common-dir format: /path/to/workspace/.git
      main_workspace = os.path.dirname(git_common_dir)
      return True, worktree_path, main_workspace
    else:
      return False, worktree_path, worktree_path

  except (subprocess.CalledProcessError, FileNotFoundError):
    # Not in a git repository
    return False, None, None


def main():
  if len(sys.argv) < 2:
    sys.exit(0)

  tool_input = sys.argv[1]

  try:
    params = json.loads(tool_input)
  except json.JSONDecodeError:
    sys.exit(0)

  # Get file_path parameter (exists for both Edit and Write)
  file_path = params.get("file_path")
  if not file_path:
    sys.exit(0)

  # Check if we're in a worktree
  is_worktree, worktree_path, main_workspace_path = get_git_worktree_info()

  if not is_worktree:
    # Not in a worktree, allow operation
    sys.exit(0)

  # Normalize paths for comparison
  file_path_abs = os.path.abspath(file_path)
  main_workspace_abs = os.path.abspath(main_workspace_path)
  worktree_path_abs = os.path.abspath(worktree_path)

  # Check if file_path targets the main workspace instead of the worktree
  # This happens when:
  # 1. file_path starts with /workspace/ (absolute main workspace path)
  # 2. file_path resolves to main workspace, not worktree

  if file_path_abs.startswith(main_workspace_abs + "/"):
    # Check if it's NOT in the worktree path
    if not file_path_abs.startswith(worktree_path_abs + "/"):
      # This is a worktree isolation violation
      print("ERROR: Worktree isolation violation (M252/M490)", file=sys.stderr)
      print("", file=sys.stderr)
      print(f"You are working in a worktree: {worktree_path_abs}", file=sys.stderr)
      print(f"But attempting to edit file in main workspace: {file_path_abs}", file=sys.stderr)
      print("", file=sys.stderr)
      print("This bypasses worktree isolation and modifies the main workspace.", file=sys.stderr)
      print("", file=sys.stderr)
      print("CORRECT APPROACH:", file=sys.stderr)

      # Calculate what the worktree path should be
      relative_to_main = os.path.relpath(file_path_abs, main_workspace_abs)
      correct_path = os.path.join(worktree_path_abs, relative_to_main)

      print(f"Use worktree path: {correct_path}", file=sys.stderr)
      print("", file=sys.stderr)
      print("Or use relative path from worktree root:", file=sys.stderr)
      print(f"  {relative_to_main}", file=sys.stderr)
      print("", file=sys.stderr)
      print("Reference: CLAUDE.md § Worktree Path Handling (M267)", file=sys.stderr)

      # Block the operation
      sys.exit(2)

  # Allow operation
  sys.exit(0)


if __name__ == "__main__":
  main()

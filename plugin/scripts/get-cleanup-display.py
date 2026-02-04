#!/usr/bin/env python3
"""
Generate cleanup display for silent preprocessing.

Gathers git worktrees, locks, branches, and generates aligned boxes
via the cleanup_handler.
"""

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

# Get the script directory and add paths for imports
SCRIPT_DIR = Path(__file__).parent
PLUGIN_ROOT = SCRIPT_DIR.parent
HANDLERS_PATH = PLUGIN_ROOT / "hooks"

sys.path.insert(0, str(HANDLERS_PATH))
from skill_handlers.cleanup_handler import CleanupHandler


def run_command(cmd: list[str], cwd: str = None) -> tuple[int, str, str]:
    """Run a command and return (returncode, stdout, stderr)."""
    try:
        result = subprocess.run(
            cmd,
            cwd=cwd,
            capture_output=True,
            text=True,
            timeout=30
        )
        return result.returncode, result.stdout, result.stderr
    except Exception as e:
        return 1, "", str(e)


def get_worktrees(project_dir: str) -> list[dict]:
    """Get list of git worktrees."""
    worktrees = []
    rc, stdout, _ = run_command(["git", "worktree", "list", "--porcelain"], cwd=project_dir)
    if rc != 0:
        return worktrees

    current = {}
    for line in stdout.strip().split('\n'):
        if not line:
            if current:
                worktrees.append(current)
                current = {}
            continue
        if line.startswith('worktree '):
            current['path'] = line[9:]
        elif line.startswith('HEAD '):
            current['head'] = line[5:][:7]
        elif line.startswith('branch '):
            current['branch'] = line[7:].split('/')[-1]
        elif line == 'bare':
            current['state'] = 'bare'
        elif line == 'detached':
            current['state'] = 'detached'

    if current:
        worktrees.append(current)

    return worktrees


def get_locks(project_dir: str) -> list[dict]:
    """Get list of task locks.

    Lock files use JSON format:
        {
          "session_id": "<uuid>",
          "created_at": <timestamp>,
          "worktree": "<path>",
          "created_iso": "<iso-timestamp>"
        }
    """
    locks = []
    locks_dir = Path(project_dir) / ".claude" / "cat" / "locks"

    if not locks_dir.is_dir():
        return locks

    import time
    for lock_file in locks_dir.glob("*.lock"):
        try:
            content = lock_file.read_text()
            data = json.loads(content)

            task_id = lock_file.stem
            session = data.get("session_id", "")
            created = data.get("created_at", 0)
            age = int(time.time() - created) if created else 0
            locks.append({
                "task_id": task_id,
                "session": session,
                "age": age
            })
        except Exception:
            continue

    return locks


def get_cat_branches(project_dir: str) -> list[str]:
    """Get CAT-related branches."""
    branches = []
    rc, stdout, _ = run_command(
        ["git", "branch", "-a"],
        cwd=project_dir
    )
    if rc != 0:
        return branches

    pattern = re.compile(r'(release/|worktree|\d+\.\d+-)')
    for line in stdout.strip().split('\n'):
        branch = line.strip().lstrip('* ')
        if pattern.search(branch):
            branches.append(branch)

    return branches


def get_stale_remotes(project_dir: str) -> list[dict]:
    """Get remote branches idle for 1-7 days."""
    stale = []
    # Fetch and prune first
    run_command(["git", "fetch", "--prune"], cwd=project_dir)

    rc, stdout, _ = run_command(["git", "branch", "-r"], cwd=project_dir)
    if rc != 0:
        return stale

    pattern = re.compile(r'origin/\d+\.\d+-')
    import time
    now = int(time.time())

    for line in stdout.strip().split('\n'):
        branch = line.strip()
        if not pattern.search(branch):
            continue

        # Get commit date
        rc2, date_out, _ = run_command(
            ["git", "log", "-1", "--format=%ct", branch],
            cwd=project_dir
        )
        if rc2 != 0:
            continue

        try:
            commit_date = int(date_out.strip())
            age_days = (now - commit_date) // 86400
            if 1 <= age_days <= 7:
                # Get author and relative time
                rc3, author, _ = run_command(
                    ["git", "log", "-1", "--format=%an", branch],
                    cwd=project_dir
                )
                rc4, relative, _ = run_command(
                    ["git", "log", "-1", "--format=%cr", branch],
                    cwd=project_dir
                )
                stale.append({
                    "branch": branch,
                    "author": author.strip() if rc3 == 0 else "",
                    "relative": relative.strip() if rc4 == 0 else ""
                })
        except ValueError:
            continue

    return stale


def get_context_file(project_dir: str) -> str | None:
    """Check for execution context file."""
    context_path = Path(project_dir) / ".cat-execution-context"
    if context_path.exists():
        return str(context_path)
    return None


def main():
    parser = argparse.ArgumentParser(description="Generate cleanup display")
    parser.add_argument("--project-dir", required=True, help="Project directory")
    parser.add_argument("--phase", default="survey", choices=["survey", "plan", "verify"],
                        help="Which phase to generate")
    args = parser.parse_args()

    project_dir = args.project_dir

    if args.phase == "survey":
        # Gather all survey data
        context = {
            "phase": "survey",
            "worktrees": get_worktrees(project_dir),
            "locks": get_locks(project_dir),
            "branches": get_cat_branches(project_dir),
            "stale_remotes": get_stale_remotes(project_dir),
            "context_file": get_context_file(project_dir)
        }

        handler = CleanupHandler()
        result = handler.handle(context)
        if result:
            print(result)
    else:
        # Plan and verify phases require data passed from the agent
        # Output instructions instead
        print(f"Phase '{args.phase}' requires data from agent. Use invoke-handler.py with context.")


if __name__ == "__main__":
    main()

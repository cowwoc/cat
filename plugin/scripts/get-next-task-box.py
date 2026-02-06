#!/usr/bin/env python3
"""
get-next-task-box.py - Generate Issue Complete box with next task discovery.

Combines lock release, next task discovery, and box rendering into a single preprocessing step
for the work-complete skill. Replaces noisy bash output in /cat:work completion flow.

Usage:
  get-next-task-box.py --completed-issue ID --base-branch BRANCH --session-id ID --project-dir DIR [--exclude-pattern GLOB]

Outputs a fully-rendered Issue Complete or Scope Complete box with discovered next task.
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path

# Import box rendering functions from sibling script
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR / "lib"))

# Import get-issue-complete-box functions
import importlib.util
spec = importlib.util.spec_from_file_location("issue_complete_box", SCRIPT_DIR / "get-issue-complete-box.py")
_box_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(_box_module)
build_issue_complete_box = _box_module.build_issue_complete_box
build_scope_complete_box = _box_module.build_scope_complete_box


def release_lock(project_dir: str, issue_id: str, session_id: str) -> None:
    """
    Release the lock for completed issue (best-effort, ignore failures).

    Args:
        project_dir: Path to project root
        issue_id: Issue ID to release lock for
        session_id: Current session ID
    """
    lock_script = SCRIPT_DIR / "issue-lock.sh"
    try:
        subprocess.run(
            [str(lock_script), "release", project_dir, issue_id, session_id],
            capture_output=True,
            timeout=10
        )
        # Ignore result - best-effort
    except Exception:
        # Best-effort - continue even if lock release fails
        pass


def find_next_task(project_dir: str, session_id: str, exclude_pattern: str | None) -> dict | None:
    """
    Find next available task using get-available-issues.sh.

    Args:
        project_dir: Path to project root
        session_id: Current session ID
        exclude_pattern: Optional glob pattern to exclude issues

    Returns:
        Dict with task info if found, None otherwise
    """
    discovery_script = SCRIPT_DIR / "get-available-issues.sh"

    cmd = [
        str(discovery_script),
        "--scope", "all",
        "--session-id", session_id
    ]

    if exclude_pattern:
        cmd.extend(["--exclude-pattern", exclude_pattern])

    try:
        result = subprocess.run(
            cmd,
            cwd=project_dir,
            capture_output=True,
            text=True,
            timeout=30
        )

        if result.returncode != 0:
            return None

        data = json.loads(result.stdout)
        if data.get("status") == "found":
            return data
        return None
    except Exception:
        return None


def read_issue_goal(issue_path: str) -> str:
    """
    Read the goal from PLAN.md in the issue directory.

    Args:
        issue_path: Path to issue directory

    Returns:
        Goal text (first paragraph after ## Goal heading)
    """
    plan_path = Path(issue_path) / "PLAN.md"

    try:
        with open(plan_path, 'r') as f:
            lines = f.readlines()

        # Find ## Goal heading
        goal_start = None
        for i, line in enumerate(lines):
            if line.strip().startswith("## Goal"):
                goal_start = i + 1
                break

        if goal_start is None:
            return "No goal found"

        # Extract text until next ## heading or end of file
        goal_lines = []
        for i in range(goal_start, len(lines)):
            line = lines[i]
            if line.strip().startswith("##"):
                break
            goal_lines.append(line.rstrip())

        # Join and strip leading/trailing whitespace
        goal = "\n".join(goal_lines).strip()

        # Return first paragraph (up to blank line)
        paragraphs = goal.split("\n\n")
        if paragraphs:
            return paragraphs[0].strip()
        return goal
    except Exception:
        return "Goal unavailable"


def main():
    parser = argparse.ArgumentParser(description="Generate Issue Complete box with next task discovery")
    parser.add_argument("--completed-issue", required=True, help="ID of completed issue")
    parser.add_argument("--base-branch", required=True, help="Base branch that was merged to")
    parser.add_argument("--session-id", required=True, help="Current session ID")
    parser.add_argument("--project-dir", required=True, help="Project root directory")
    parser.add_argument("--exclude-pattern", required=False, help="Glob pattern to exclude issues")

    args = parser.parse_args()

    try:
        # Step 1: Release lock for completed issue (best-effort)
        release_lock(args.project_dir, args.completed_issue, args.session_id)

        # Step 2: Find next task
        next_task = find_next_task(args.project_dir, args.session_id, args.exclude_pattern)

        if next_task:
            # Step 3: Read goal for next task
            next_issue_id = next_task.get("issue_id", "")
            next_issue_path = next_task.get("issue_path", "")

            goal = read_issue_goal(next_issue_path) if next_issue_path else "No goal available"

            # Step 4: Render Issue Complete box with next task
            box = build_issue_complete_box(
                issue_name=args.completed_issue,
                next_issue=next_issue_id,
                next_goal=goal,
                base_branch=args.base_branch
            )
            print(box)
        else:
            # No next task - render Scope Complete box
            # Extract scope from completed issue (e.g., "2.1-xxx" -> "v2.1")
            scope = args.completed_issue.split("-")[0]
            if scope and scope[0].isdigit():
                scope = "v" + scope
            else:
                scope = args.base_branch

            box = build_scope_complete_box(scope=scope)
            print(box)

    except Exception as e:
        # On error, print error to stderr and output fallback text to stdout
        print(f"ERROR generating next task box: {e}", file=sys.stderr)
        print(f"\n{args.completed_issue} merged to {args.base_branch}.\n", file=sys.stdout)


if __name__ == "__main__":
    main()

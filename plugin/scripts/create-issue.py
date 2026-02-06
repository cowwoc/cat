#!/usr/bin/env python3
"""
Create a CAT issue with files and git commit in one atomic operation.

This script consolidates:
- mkdir -p for issue directory
- Writing STATE.md
- Writing PLAN.md
- Updating parent version STATE.md
- git add
- git commit

All in a single call to reduce visible Bash operations during /cat:add.
"""

import argparse
import json
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path


def update_parent_state(parent_state_path: Path, issue_name: str) -> None:
    """
    Update parent version STATE.md to add issue to pending list.

    Args:
        parent_state_path: Path to parent version STATE.md
        issue_name: Name of the issue to add
    """
    if not parent_state_path.exists():
        raise FileNotFoundError(f"Parent STATE.md not found: {parent_state_path}")

    content = parent_state_path.read_text()

    # Check if "## Issues Pending" section exists
    if "## Issues Pending" in content:
        # Find the section and add issue after it
        # Insert after the "## Issues Pending" line
        lines = content.split("\n")
        new_lines = []
        for i, line in enumerate(lines):
            new_lines.append(line)
            if line.strip() == "## Issues Pending":
                # Insert after this line
                new_lines.append(f"- {issue_name}")
        content = "\n".join(new_lines)
    else:
        # Add new section
        content += f"\n\n## Issues Pending\n- {issue_name}\n"

    parent_state_path.write_text(content)


def create_issue(data: dict) -> dict:
    """
    Create issue structure and commit to git.

    Args:
        data: Dict with keys:
            - major: Major version number
            - minor: Minor version number
            - issue_name: Name of the issue
            - issue_type: Type (Feature, Bugfix, etc.)
            - dependencies: List of dependency issue names
            - state_content: Full STATE.md content
            - plan_content: Full PLAN.md content
            - commit_description: One-line description for commit message

    Returns:
        Dict with {"success": bool, "path": str, "error": str (if failed)}
    """
    try:
        # Validate required fields
        required = ["major", "minor", "issue_name", "state_content", "plan_content"]
        for field in required:
            if field not in data:
                return {"success": False, "error": f"Missing required field: {field}"}

        major = data["major"]
        minor = data["minor"]
        issue_name = data["issue_name"]
        state_content = data["state_content"]
        plan_content = data["plan_content"]
        commit_desc = data.get("commit_description", "Add issue")

        # Build paths
        root = Path.cwd()
        issue_path = root / ".claude" / "cat" / "issues" / f"v{major}" / f"v{major}.{minor}" / issue_name
        parent_state_path = issue_path.parent / "STATE.md"

        # Validate parent version exists
        if not issue_path.parent.exists():
            return {
                "success": False,
                "error": f"Parent version directory does not exist: {issue_path.parent}",
            }

        # Create issue directory
        issue_path.mkdir(parents=True, exist_ok=True)

        # Write STATE.md
        state_file = issue_path / "STATE.md"
        state_file.write_text(state_content)

        # Write PLAN.md
        plan_file = issue_path / "PLAN.md"
        plan_file.write_text(plan_content)

        # Update parent STATE.md
        update_parent_state(parent_state_path, issue_name)

        # Git add
        subprocess.run(
            ["git", "add", str(issue_path), str(parent_state_path)],
            check=True,
            capture_output=True,
            text=True,
        )

        # Git commit
        commit_message = f"planning: add issue {issue_name} to {major}.{minor}\n\n{commit_desc}"
        subprocess.run(
            ["git", "commit", "-m", commit_message],
            check=True,
            capture_output=True,
            text=True,
        )

        return {"success": True, "path": str(issue_path)}

    except subprocess.CalledProcessError as e:
        return {"success": False, "error": f"Git command failed: {e.stderr}"}
    except Exception as e:
        return {"success": False, "error": str(e)}


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="Create CAT issue with git commit")
    parser.add_argument(
        "--json",
        type=str,
        help="JSON string with issue data",
    )
    args = parser.parse_args()

    # Read JSON from stdin or argument
    if args.json:
        try:
            data = json.loads(args.json)
        except json.JSONDecodeError as e:
            print(json.dumps({"success": False, "error": f"Invalid JSON: {e}"}))
            sys.exit(1)
    else:
        try:
            data = json.load(sys.stdin)
        except json.JSONDecodeError as e:
            print(json.dumps({"success": False, "error": f"Invalid JSON from stdin: {e}"}))
            sys.exit(1)

    # Create issue
    result = create_issue(data)

    # Output result as JSON
    print(json.dumps(result, indent=2))

    # Exit with appropriate code
    sys.exit(0 if result["success"] else 1)


if __name__ == "__main__":
    main()

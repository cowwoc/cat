"""
Handler for /cat:add precomputation.

Generates box displays for issue and version creation completion.
Pre-loads version data to reduce visible Bash calls during skill execution.
"""

import json
import re
from pathlib import Path
from typing import Any

from . import register_handler
from .status_handler import build_header_box


class AddHandler:
    """Handler for /cat:add skill."""

    def _preload_version_data(self, project_root: str) -> dict[str, Any]:
        """
        Pre-load version data to reduce visible Bash calls.

        Returns:
            Dict with structure:
            {
                "planning_valid": bool,
                "error": str (if planning_valid is False),
                "versions": [
                    {
                        "version": "2.1",
                        "status": "in-progress" | "open" | "closed",
                        "summary": "Brief description",
                        "issue_count": 5,
                        "existing_issues": ["issue-name-1", "issue-name-2", ...]
                    }
                ],
                "branch_strategy": "feature" | "main-only",
                "branch_pattern": None | "{version}-{issue-name}"
            }
        """
        root = Path(project_root) if project_root else Path.cwd()
        cat_dir = root / ".claude" / "cat"

        # Validate planning structure exists
        if not cat_dir.exists():
            return {
                "planning_valid": False,
                "error": "No planning structure. Run /cat:init first.",
            }

        roadmap_file = cat_dir / "ROADMAP.md"
        if not roadmap_file.exists():
            return {
                "planning_valid": False,
                "error": "No ROADMAP.md. Run /cat:init first.",
            }

        # Read all minor version directories
        issues_dir = cat_dir / "issues"
        if not issues_dir.exists():
            return {
                "planning_valid": True,
                "versions": [],
                "branch_strategy": "feature",
                "branch_pattern": None,
            }

        versions = []
        for major_dir in sorted(issues_dir.iterdir()):
            if not major_dir.is_dir() or not major_dir.name.startswith("v"):
                continue

            for minor_dir in sorted(major_dir.iterdir()):
                if not minor_dir.is_dir() or not minor_dir.name.startswith("v"):
                    continue

                # Extract version number
                version_match = re.match(r"v(\d+)\.(\d+)", minor_dir.name)
                if not version_match:
                    continue

                version_str = f"{version_match.group(1)}.{version_match.group(2)}"

                # Read STATE.md for status
                state_file = minor_dir / "STATE.md"
                status = "open"
                if state_file.exists():
                    state_content = state_file.read_text()
                    status_match = re.search(r"\*\*Status:\*\*\s+(\w+)", state_content)
                    if status_match:
                        status = status_match.group(1)

                # Skip closed versions
                if status == "closed":
                    continue

                # Read PLAN.md for summary (goals/objectives)
                summary = ""
                plan_file = minor_dir / "PLAN.md"
                if plan_file.exists():
                    plan_content = plan_file.read_text()
                    # Extract first line after ## Goals or ## Objectives
                    goals_match = re.search(
                        r"##\s+(?:Goals|Objectives)\s*\n(.+)", plan_content, re.MULTILINE
                    )
                    if goals_match:
                        summary = goals_match.group(1).strip()

                # Count issues in this version
                issue_count = 0
                existing_issues = []
                for item in minor_dir.iterdir():
                    if item.is_dir() and not item.name.startswith("v"):
                        issue_count += 1
                        existing_issues.append(item.name)

                versions.append(
                    {
                        "version": version_str,
                        "status": status,
                        "summary": summary,
                        "issue_count": issue_count,
                        "existing_issues": existing_issues,
                    }
                )

        # Read branch strategy
        branch_strategy = "feature"
        branch_pattern = None

        # Check cat-config.json
        config_file = cat_dir / "cat-config.json"
        if config_file.exists():
            try:
                config = json.loads(config_file.read_text())
                if "gitWorkflow" in config and "branchingStrategy" in config["gitWorkflow"]:
                    branch_strategy = config["gitWorkflow"]["branchingStrategy"]
            except (json.JSONDecodeError, KeyError):
                pass

        # Check PROJECT.md for pattern
        project_file = cat_dir / "PROJECT.md"
        if project_file.exists():
            project_content = project_file.read_text()
            # Look for pattern in Branching Strategy section
            pattern_match = re.search(
                r"###\s+Branching Strategy.*?Issue.*?Pattern.*?`([^`]+)`",
                project_content,
                re.DOTALL,
            )
            if pattern_match:
                branch_pattern = pattern_match.group(1)

        return {
            "planning_valid": True,
            "versions": versions,
            "branch_strategy": branch_strategy,
            "branch_pattern": branch_pattern,
        }

    def handle(self, context: dict) -> str | None:
        """
        Generate output template display for add completion OR preload version data.

        Context keys for display generation:
            - item_type: "issue" or "version"
            - item_name: Name of the created item
            - version: Version string (e.g., "2.0" for issue, "2.1" for version)
            - issue_type: Type of issue (Feature, Bugfix, etc.) - for issues only
            - dependencies: List of dependencies - for issues only
            - version_type: Type of version (major, minor, patch) - for versions only
            - parent_info: Parent version info - for versions only
            - path: Filesystem path to the created item

        Context keys for preloading:
            - project_root: Path to project root
        """
        item_type = context.get("item_type")

        # If no item_type, this is a preload call - return version data
        if not item_type:
            project_root = context.get("project_root", "")
            version_data = self._preload_version_data(project_root)
            return f"HANDLER_DATA: {json.dumps(version_data, indent=2)}"

        # Otherwise, handle display generation
        if item_type == "issue":
            return self._build_issue_display(context)
        elif item_type == "version":
            return self._build_version_display(context)

        return None

    def _build_issue_display(self, context: dict) -> str:
        """Build display box for issue creation."""
        item_name = context.get("item_name", "unknown-issue")
        version = context.get("version", "0.0")
        issue_type = context.get("issue_type", "Feature")
        dependencies = context.get("dependencies", [])

        deps_str = ", ".join(dependencies) if dependencies else "None"

        # Check if multiple issues (comma-separated)
        if "," in item_name:
            issue_names = [name.strip() for name in item_name.split(",")]
            content_items = []
            for idx, name in enumerate(issue_names, 1):
                content_items.append(f"{idx}. {name}")
            content_items.append("")
            content_items.append(f"Version: {version}")
            content_items.append(f"Type: {issue_type}")
            content_items.append(f"Dependencies: {deps_str}")
            header = "✅ Issues Created"
            next_cmd = f"/cat:work {version}-{issue_names[0]}"
        else:
            # Build content items for single issue
            content_items = [
                item_name,
                "",
                f"Version: {version}",
                f"Type: {issue_type}",
                f"Dependencies: {deps_str}",
            ]
            header = "✅ Issue Created"
            next_cmd = f"/cat:work {version}-{item_name}"

        final_box = build_header_box(header, content_items, min_width=40, prefix="─ ")

        return f"""SCRIPT OUTPUT ADD DISPLAY::

{final_box}

Next: /clear, then {next_cmd}

INSTRUCTION: Output the above box EXACTLY as shown. Do not recalculate."""

    def _build_version_display(self, context: dict) -> str:
        """Build display box for version creation."""
        item_name = context.get("item_name", "New Version")
        version = context.get("version", "0.0")
        version_type = context.get("version_type", "minor")
        parent_info = context.get("parent_info", "")
        path = context.get("path", "")

        # Build content items
        content_items = [
            f"v{version}: {item_name}",
            "",
        ]

        if parent_info:
            content_items.append(f"Parent: {parent_info}")
        if path:
            content_items.append(f"Path: {path}")

        header = "✅ Version Created"
        final_box = build_header_box(header, content_items, min_width=40, prefix="─ ")

        return f"""SCRIPT OUTPUT ADD DISPLAY::

{final_box}

Next: /clear, then /cat:add (to add issues)

INSTRUCTION: Output the above box EXACTLY as shown. Do not recalculate."""


# Register handler
_handler = AddHandler()
register_handler("add", _handler)

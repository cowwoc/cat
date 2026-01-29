"""
Handler for render-diff precomputation.

Pre-computes rendered diff output for approval gates so the agent
can display it directly without visible Bash tool invocations.
"""

import re
import subprocess
from pathlib import Path

from . import register_handler


def detect_base_branch(project_root: Path) -> str | None:
    """
    Detect the base branch to diff against.

    Priority:
    1. Extract from worktree name (e.g., 2.0-task-name -> v2.0)
    2. Extract from current branch name
    3. Check for tracking branch
    4. Default to 'main'
    """
    try:
        # Get current directory name (might be worktree)
        cwd = Path.cwd()
        worktree_name = cwd.name

        # Check if we're in a .worktrees directory
        if cwd.parent.name == ".worktrees":
            # Extract version from worktree name (e.g., "2.0-task-name" -> "v2.0")
            match = re.match(r'^(\d+\.\d+)-', worktree_name)
            if match:
                return f"v{match.group(1)}"

        # Get current branch name
        result = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True,
            text=True,
            cwd=project_root,
            timeout=10
        )
        if result.returncode == 0:
            branch = result.stdout.strip()

            # Extract version from branch name
            match = re.match(r'^(\d+\.\d+)-', branch)
            if match:
                return f"v{match.group(1)}"

            # Check if branch is a version branch itself
            if re.match(r'^v\d+\.\d+$', branch):
                # If on version branch, diff against main
                return "main"

        # Try to get tracking branch
        result = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "@{upstream}"],
            capture_output=True,
            text=True,
            cwd=project_root,
            timeout=10
        )
        if result.returncode == 0:
            upstream = result.stdout.strip()
            # Extract local branch name from origin/branch
            if "/" in upstream:
                return upstream.split("/", 1)[1]
            return upstream

    except Exception:
        pass

    # Default fallback
    return "main"


def get_changed_files(project_root: Path, base_branch: str) -> list[str]:
    """Get list of changed files between base and HEAD."""
    try:
        result = subprocess.run(
            ["git", "diff", "--name-only", f"{base_branch}..HEAD"],
            capture_output=True,
            text=True,
            cwd=project_root,
            timeout=30
        )
        if result.returncode == 0:
            return [f for f in result.stdout.strip().split('\n') if f]
    except Exception:
        pass
    return []


def render_diff(project_root: Path, base_branch: str, plugin_root: Path) -> str | None:
    """
    Generate rendered diff using render-diff.py script.

    Args:
        project_root: Path to project root
        base_branch: Base branch to diff against
        plugin_root: Path to plugin root (for finding render-diff.py)

    Returns:
        Rendered diff output, or None on error
    """
    render_script = plugin_root / "scripts" / "render-diff.py"
    if not render_script.exists():
        return None

    try:
        # Run git diff and pipe to render-diff.py
        git_diff = subprocess.Popen(
            ["git", "diff", f"{base_branch}..HEAD"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            cwd=project_root
        )

        render_result = subprocess.run(
            ["python3", str(render_script)],
            stdin=git_diff.stdout,
            capture_output=True,
            text=True,
            cwd=project_root,
            timeout=120
        )

        git_diff.wait()

        if render_result.returncode == 0 and render_result.stdout:
            return render_result.stdout.strip()

    except subprocess.TimeoutExpired:
        return None
    except Exception:
        return None

    return None


def get_diff_stats(project_root: Path, base_branch: str) -> dict:
    """Get diff statistics."""
    stats = {
        "files_changed": 0,
        "insertions": 0,
        "deletions": 0
    }

    try:
        result = subprocess.run(
            ["git", "diff", "--stat", f"{base_branch}..HEAD"],
            capture_output=True,
            text=True,
            cwd=project_root,
            timeout=30
        )
        if result.returncode == 0:
            # Parse the summary line (e.g., "5 files changed, 100 insertions(+), 50 deletions(-)")
            lines = result.stdout.strip().split('\n')
            if lines:
                summary = lines[-1]
                files_match = re.search(r'(\d+) files? changed', summary)
                ins_match = re.search(r'(\d+) insertions?\(\+\)', summary)
                del_match = re.search(r'(\d+) deletions?\(-\)', summary)

                if files_match:
                    stats["files_changed"] = int(files_match.group(1))
                if ins_match:
                    stats["insertions"] = int(ins_match.group(1))
                if del_match:
                    stats["deletions"] = int(del_match.group(1))
    except Exception:
        pass

    return stats


class RenderDiffHandler:
    """Handler for render-diff skill."""

    def handle(self, context: dict) -> str | None:
        """
        Pre-compute rendered diff for approval gates.

        Returns formatted diff output via additionalContext so the agent
        can display it directly without Bash tool invocations.
        """
        project_root = context.get("project_root")
        plugin_root = context.get("plugin_root")

        if not project_root or not plugin_root:
            return None

        project_path = Path(project_root)
        plugin_path = Path(plugin_root)

        # Detect base branch
        base_branch = detect_base_branch(project_path)
        if not base_branch:
            return None

        # Check if base branch exists
        try:
            result = subprocess.run(
                ["git", "rev-parse", "--verify", base_branch],
                capture_output=True,
                cwd=project_path,
                timeout=10
            )
            if result.returncode != 0:
                # Try with origin/ prefix
                base_branch = f"origin/{base_branch}"
                result = subprocess.run(
                    ["git", "rev-parse", "--verify", base_branch],
                    capture_output=True,
                    cwd=project_path,
                    timeout=10
                )
                if result.returncode != 0:
                    return None
        except Exception:
            return None

        # Get changed files list
        changed_files = get_changed_files(project_path, base_branch)
        if not changed_files:
            return f"""SCRIPT OUTPUT RENDER-DIFF OUTPUT:

No changes detected between {base_branch} and HEAD.

INSTRUCTION: Report "No changes to display" to the user."""

        # Get diff stats
        stats = get_diff_stats(project_path, base_branch)

        # Generate rendered diff
        rendered = render_diff(project_path, base_branch, plugin_path)
        if not rendered:
            return None

        # Build file summary
        file_list = '\n'.join(f"  - {f}" for f in changed_files[:20])
        if len(changed_files) > 20:
            file_list += f"\n  ... and {len(changed_files) - 20} more files"

        return f"""SCRIPT OUTPUT RENDER-DIFF OUTPUT:

## Diff Summary
- **Base branch:** {base_branch}
- **Files changed:** {stats['files_changed']}
- **Insertions:** +{stats['insertions']}
- **Deletions:** -{stats['deletions']}

## Changed Files
{file_list}

## Rendered Diff (4-column format)

{rendered}

---

INSTRUCTION: Output the rendered diff above DIRECTLY to the user.
Do NOT wrap in code blocks. Do NOT invoke Bash tools.
The diff is already formatted with 4-column tables and box characters."""


# Register handler
_handler = RenderDiffHandler()
register_handler("render-diff", _handler)

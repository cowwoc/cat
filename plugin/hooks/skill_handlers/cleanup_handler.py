"""
Handler for /cat:cleanup precomputation.

Generates box displays for survey results, cleanup plan, and verification.
"""

import json
import os
import re
import subprocess
import time
from pathlib import Path

from . import register_handler
from .status_handler import display_width, build_inner_box, build_header_box


class CleanupHandler:
    """Handler for /cat:cleanup skill."""

    def handle(self, context: dict) -> str | None:
        """
        Generate output template display for cleanup phases.

        Context keys:
            - phase: "survey" | "plan" | "verify" (optional, defaults to "survey")
            - project_root: Project directory (used to gather data if not provided)

        When called without phase (from preprocessing), defaults to "survey"
        and gathers data automatically from project_root.
        """
        phase = context.get("phase", "survey")
        project_root = context.get("project_root", os.getcwd())

        # For survey phase, gather data if not provided
        if phase == "survey" and "worktrees" not in context:
            context = self._gather_survey_data(project_root, context)

        if phase == "survey":
            return self._build_survey_display(context)
        elif phase == "plan":
            return self._build_plan_display(context)
        elif phase == "verify":
            return self._build_verify_display(context)

        return None

    def _run_command(self, cmd: list[str], cwd: str = None) -> tuple[int, str, str]:
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

    def _gather_survey_data(self, project_dir: str, context: dict) -> dict:
        """Gather survey data from the project directory."""
        context = dict(context)  # Don't modify original
        context["worktrees"] = self._get_worktrees(project_dir)
        context["locks"] = self._get_locks(project_dir)
        context["branches"] = self._get_cat_branches(project_dir)
        context["stale_remotes"] = self._get_stale_remotes(project_dir)
        context["context_file"] = self._get_context_file(project_dir)
        return context

    def _get_worktrees(self, project_dir: str) -> list[dict]:
        """Get list of git worktrees."""
        worktrees = []
        rc, stdout, _ = self._run_command(
            ["git", "worktree", "list", "--porcelain"],
            cwd=project_dir
        )
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

    def _get_locks(self, project_dir: str) -> list[dict]:
        """Get list of task locks."""
        locks = []
        locks_dir = Path(project_dir) / ".claude" / "cat" / "locks"

        if not locks_dir.is_dir():
            return locks

        for lock_file in locks_dir.glob("*.lock"):
            try:
                data = json.loads(lock_file.read_text())
                task_id = lock_file.stem
                session = data.get("session_id", "")
                created = data.get("created", 0)
                age = int(time.time() - created) if created else 0
                locks.append({
                    "task_id": task_id,
                    "session": session,
                    "age": age
                })
            except Exception:
                continue

        return locks

    def _get_cat_branches(self, project_dir: str) -> list[str]:
        """Get CAT-related branches."""
        branches = []
        rc, stdout, _ = self._run_command(
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

    def _get_stale_remotes(self, project_dir: str) -> list[dict]:
        """Get remote branches idle for 1-7 days."""
        stale = []
        # Fetch and prune first
        self._run_command(["git", "fetch", "--prune"], cwd=project_dir)

        rc, stdout, _ = self._run_command(["git", "branch", "-r"], cwd=project_dir)
        if rc != 0:
            return stale

        pattern = re.compile(r'origin/\d+\.\d+-')
        now = int(time.time())

        for line in stdout.strip().split('\n'):
            branch = line.strip()
            if not pattern.search(branch):
                continue

            rc2, date_out, _ = self._run_command(
                ["git", "log", "-1", "--format=%ct", branch],
                cwd=project_dir
            )
            if rc2 != 0:
                continue

            try:
                commit_date = int(date_out.strip())
                age_days = (now - commit_date) // 86400
                if 1 <= age_days <= 7:
                    rc3, author, _ = self._run_command(
                        ["git", "log", "-1", "--format=%an", branch],
                        cwd=project_dir
                    )
                    rc4, relative, _ = self._run_command(
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

    def _get_context_file(self, project_dir: str) -> str | None:
        """Check for execution context file."""
        context_path = Path(project_dir) / ".cat-execution-context"
        if context_path.exists():
            return str(context_path)
        return None

    def _build_survey_display(self, context: dict) -> str:
        """Build display box for survey results."""
        worktrees = context.get("worktrees", [])
        locks = context.get("locks", [])
        branches = context.get("branches", [])
        stale_remotes = context.get("stale_remotes", [])
        context_file = context.get("context_file")

        # Build inner boxes for each category
        all_inner_boxes = []

        # Worktrees inner box
        wt_items = []
        for wt in worktrees:
            path = wt.get("path", "")
            branch = wt.get("branch", "")
            state = wt.get("state", "")
            if state:
                wt_items.append(f"{path}: {branch} [{state}]")
            else:
                wt_items.append(f"{path}: {branch}")
        if not wt_items:
            wt_items = ["None found"]
        wt_box = build_inner_box("📁 Worktrees", wt_items)
        all_inner_boxes.extend(wt_box)
        all_inner_boxes.append("")

        # Locks inner box
        lock_items = []
        for lock in locks:
            task_id = lock.get("task_id", "")
            session = lock.get("session", "")[:8] if lock.get("session") else ""
            age = lock.get("age", 0)
            lock_items.append(f"{task_id}: session={session}, age={age}s")
        if not lock_items:
            lock_items = ["None found"]
        lock_box = build_inner_box("🔒 Task Locks", lock_items)
        all_inner_boxes.extend(lock_box)
        all_inner_boxes.append("")

        # Branches inner box
        branch_items = branches if branches else ["None found"]
        branch_box = build_inner_box("🌿 CAT Branches", branch_items)
        all_inner_boxes.extend(branch_box)
        all_inner_boxes.append("")

        # Stale remotes inner box
        remote_items = []
        for remote in stale_remotes:
            branch = remote.get("branch", "")
            author = remote.get("author", "")
            relative = remote.get("relative", "")
            remote_items.append(f"{branch}: {author}, {relative}")
        if not remote_items:
            remote_items = ["None found"]
        remote_box = build_inner_box("⏳ Stale Remotes (1-7 days)", remote_items)
        all_inner_boxes.extend(remote_box)
        all_inner_boxes.append("")

        # Context file line
        if context_file:
            all_inner_boxes.append(f"📝 Context: {context_file}")
        else:
            all_inner_boxes.append("📝 Context: None")

        # Build outer box with header
        header = "🔍 Survey Results"
        final_box = build_header_box(header, all_inner_boxes, min_width=50, prefix="─ ")

        # Summary counts
        counts = f"Found: {len(worktrees)} worktrees, {len(locks)} locks, {len(branches)} branches, {len(stale_remotes)} stale remotes"

        return f"""SCRIPT OUTPUT SURVEY DISPLAY (copy exactly):

{final_box}

{counts}

INSTRUCTION: Output the above box EXACTLY as shown. Do not recalculate."""

    def _build_plan_display(self, context: dict) -> str:
        """Build display box for cleanup plan."""
        locks_to_remove = context.get("locks_to_remove", [])
        worktrees_to_remove = context.get("worktrees_to_remove", [])
        branches_to_remove = context.get("branches_to_remove", [])
        stale_remotes = context.get("stale_remotes", [])

        # Build content with grouped sections
        content_items = []

        # Locks section
        content_items.append("🔒 Locks to Remove:")
        if locks_to_remove:
            for lock in locks_to_remove:
                content_items.append(f"   • {lock}")
        else:
            content_items.append("   (none)")
        content_items.append("")

        # Worktrees section
        content_items.append("📁 Worktrees to Remove:")
        if worktrees_to_remove:
            for wt in worktrees_to_remove:
                path = wt.get("path", "")
                branch = wt.get("branch", "")
                content_items.append(f"   • {path} → {branch}")
        else:
            content_items.append("   (none)")
        content_items.append("")

        # Branches section
        content_items.append("🌿 Branches to Remove:")
        if branches_to_remove:
            for branch in branches_to_remove:
                content_items.append(f"   • {branch}")
        else:
            content_items.append("   (none)")
        content_items.append("")

        # Stale remotes section (report only)
        content_items.append("⏳ Stale Remotes (report only):")
        if stale_remotes:
            for remote in stale_remotes:
                branch = remote.get("branch", "")
                staleness = remote.get("staleness", "")
                content_items.append(f"   • {branch}: {staleness}")
        else:
            content_items.append("   (none)")

        # Build outer box with header
        header = "🧹 Cleanup Plan"
        final_box = build_header_box(header, content_items, min_width=50, prefix="─ ")

        # Count summary
        total = len(locks_to_remove) + len(worktrees_to_remove) + len(branches_to_remove)

        return f"""SCRIPT OUTPUT PLAN DISPLAY (copy exactly):

{final_box}

Total items to remove: {total}

Confirm cleanup? (yes/no)

INSTRUCTION: Output the above box EXACTLY as shown. Do not recalculate."""

    def _build_verify_display(self, context: dict) -> str:
        """Build display box for verification results."""
        remaining_worktrees = context.get("remaining_worktrees", [])
        remaining_branches = context.get("remaining_branches", [])
        remaining_locks = context.get("remaining_locks", [])
        removed_counts = context.get("removed_counts", {})

        locks_removed = removed_counts.get("locks", 0)
        worktrees_removed = removed_counts.get("worktrees", 0)
        branches_removed = removed_counts.get("branches", 0)

        # Build content
        content_items = []

        # Removed summary
        content_items.append("Removed:")
        content_items.append(f"   • {locks_removed} lock(s)")
        content_items.append(f"   • {worktrees_removed} worktree(s)")
        content_items.append(f"   • {branches_removed} branch(es)")
        content_items.append("")

        # Remaining worktrees
        content_items.append("📁 Remaining Worktrees:")
        if remaining_worktrees:
            for wt in remaining_worktrees:
                content_items.append(f"   • {wt}")
        else:
            content_items.append("   (none)")
        content_items.append("")

        # Remaining branches
        content_items.append("🌿 Remaining CAT Branches:")
        if remaining_branches:
            for branch in remaining_branches:
                content_items.append(f"   • {branch}")
        else:
            content_items.append("   (none)")
        content_items.append("")

        # Remaining locks
        content_items.append("🔒 Remaining Locks:")
        if remaining_locks:
            for lock in remaining_locks:
                content_items.append(f"   • {lock}")
        else:
            content_items.append("   (none)")

        # Build outer box with header
        header = "✅ Cleanup Complete"
        final_box = build_header_box(header, content_items, min_width=50, prefix="─ ")

        return f"""SCRIPT OUTPUT VERIFY DISPLAY (copy exactly):

{final_box}

INSTRUCTION: Output the above box EXACTLY as shown. Do not recalculate."""


# Register handler
_handler = CleanupHandler()
register_handler("cleanup", _handler)

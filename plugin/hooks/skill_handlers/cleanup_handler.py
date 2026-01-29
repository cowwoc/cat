"""
Handler for /cat:cleanup precomputation.

Generates box displays for survey results, cleanup plan, and verification.
"""

from . import register_handler
from .status_handler import display_width, build_inner_box, build_header_box


class CleanupHandler:
    """Handler for /cat:cleanup skill."""

    def handle(self, context: dict) -> str | None:
        """
        Generate output template display for cleanup phases.

        Context keys:
            - phase: "survey" | "plan" | "verify"
        """
        phase = context.get("phase")

        if not phase:
            return None

        if phase == "survey":
            return self._build_survey_display(context)
        elif phase == "plan":
            return self._build_plan_display(context)
        elif phase == "verify":
            return self._build_verify_display(context)

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

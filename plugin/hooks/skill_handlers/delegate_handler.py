"""
Handler for /cat:delegate precomputation.

Generates progress templates for parallel or sequential delegation based on skill args.
Parses --skill, --issues, and --sequential flags to determine execution mode and items.
"""

import re
import shlex

from . import register_handler
from .status_handler import build_header_box, display_width


class DelegateHandler:
    """Handler for /cat:delegate skill."""

    def handle(self, context: dict) -> str | None:
        """
        Generate delegate progress templates.

        Parses the skill invocation to extract:
        - --skill <name> [args...] - skill to run with arguments
        - --issues <id1,id2,...> - CAT issues to delegate
        - --sequential - force sequential execution

        Args:
            context: Handler context containing user_prompt with skill invocation

        Returns:
            Pre-rendered progress templates or None if args cannot be parsed
        """
        user_prompt = context.get("user_prompt", "")

        if not user_prompt:
            return None

        # Extract args from prompt: /cat:delegate --skill shrink-doc file1.md file2.md
        # or: cat:delegate --issues 2.1-a,2.1-b,2.1-c
        match = re.search(r'/?\s*cat:delegate\s+(.+?)(?:\n|$)', user_prompt, re.IGNORECASE)
        if not match:
            return None

        args_str = match.group(1).strip()
        if not args_str:
            return None

        # Parse arguments
        try:
            args = shlex.split(args_str)
        except ValueError:
            # If shlex fails, try simple split
            args = args_str.split()

        skill = None
        skill_args = []
        issues = []
        sequential = False
        worktree = None

        i = 0
        while i < len(args):
            arg = args[i]
            if arg == "--skill":
                if i + 1 < len(args):
                    skill = args[i + 1]
                    i += 2
                    # Everything after skill name until next flag is skill args
                    while i < len(args) and not args[i].startswith("--"):
                        skill_args.append(args[i])
                        i += 1
                else:
                    i += 1
            elif arg == "--issues":
                if i + 1 < len(args):
                    issues = [x.strip() for x in args[i + 1].split(",") if x.strip()]
                    i += 2
                else:
                    i += 1
            elif arg == "--sequential":
                sequential = True
                i += 1
            elif arg == "--worktree":
                if i + 1 < len(args):
                    worktree = args[i + 1]
                    i += 2
                else:
                    i += 1
            else:
                i += 1

        # Determine items and mode
        if skill:
            items = skill_args if skill_args else [f"(skill: {skill})"]
            item_type = "skill"
        elif issues:
            items = issues
            item_type = "issues"
        else:
            return None

        item_count = len(items)
        if item_count == 0:
            return None

        # Determine execution mode
        if item_count == 1 or sequential:
            mode = "SEQUENTIAL"
        else:
            mode = "PARALLEL"

        # Build progress output
        output_lines = ["SCRIPT OUTPUT DELEGATE PROGRESS:", ""]

        # Header showing mode and count
        if skill:
            header = f"Delegating: /cat:{skill}"
        else:
            header = "Delegating: CAT Issues"

        output_lines.append(f"**Mode:** {mode}")
        output_lines.append(f"**Items:** {item_count}")
        if worktree:
            output_lines.append(f"**Worktree:** {worktree}")
        output_lines.append("")

        # Item list
        output_lines.append("**Items to process:**")
        for i, item in enumerate(items, 1):
            output_lines.append(f"  {i}. {item}")
        output_lines.append("")

        # Progress template based on mode
        if mode == "PARALLEL":
            output_lines.append("**Parallel Execution Template:**")
            output_lines.append("```")
            output_lines.append(f"┌─ 🔀 Parallel Delegation ({item_count} subagents) ─────────────────┐")
            for i, item in enumerate(items, 1):
                # Truncate long item names
                display_item = item if len(item) <= 40 else item[:37] + "..."
                output_lines.append(f"│  [{i}] ○ {display_item:<42} │")
            output_lines.append("└────────────────────────────────────────────────────────┘")
            output_lines.append("```")
            output_lines.append("")
            output_lines.append("**Status symbols:** ○ Pending | ◉ Running | ● Complete | ✗ Failed")
        else:
            output_lines.append("**Sequential Execution Template:**")
            output_lines.append("```")
            output_lines.append(f"┌─ ▶ Sequential Delegation ({item_count} items) ──────────────────┐")
            for i, item in enumerate(items, 1):
                display_item = item if len(item) <= 40 else item[:37] + "..."
                symbol = "◉" if i == 1 else "○"
                output_lines.append(f"│  [{i}] {symbol} {display_item:<42} │")
            output_lines.append("└────────────────────────────────────────────────────────┘")
            output_lines.append("```")
            output_lines.append("")
            output_lines.append("**Status symbols:** ○ Pending | ◉ Current | ● Complete | ✗ Failed")

        # Results template
        output_lines.append("")
        output_lines.append("**Results Template (use after completion):**")
        output_lines.append("```")
        output_lines.append(f"✅ Delegation complete: {{succeeded}}/{item_count} items succeeded")
        output_lines.append("❌ Failed: {failed} items (if any)")
        output_lines.append("")
        if skill:
            output_lines.append("Postcondition Results:")
            for item in items[:5]:  # Show first 5 as template
                display_item = item if len(item) <= 30 else item[:27] + "..."
                output_lines.append(f"  {display_item}: score={{score}}, {{PASS|FAIL}}")
            if len(items) > 5:
                output_lines.append(f"  ... and {len(items) - 5} more")
        output_lines.append("```")

        return "\n".join(output_lines)


# Register handler
_handler = DelegateHandler()
register_handler("delegate", _handler)

#!/usr/bin/env python3
"""
get-skill-output.py - Unified UserPromptSubmit hook for CAT skills

TRIGGER: UserPromptSubmit

This dispatcher replaces multiple individual precompute hooks with a single
entry point that routes to skill-specific handlers based on the user prompt.

Architecture:
- Single hook in hooks.json (reduces shell spawn overhead)
- Pattern matching determines which handler to invoke
- Handlers are Python modules in skill_handlers/
- Each handler returns additionalContext or empty dict

Benefits over individual bash scripts:
1. Single shell spawn per user prompt (vs N spawns)
2. Shared initialization code
3. Easier to add new skill handlers
4. Better error handling and logging
"""

import json
import os
import re
import sys
from pathlib import Path

# Add handlers directory to path
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

from skill_handlers import get_handler


def read_hook_input() -> dict:
    """Read and parse JSON input from stdin."""
    if sys.stdin.isatty():
        return {}

    try:
        raw = sys.stdin.read()
        return json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        return {}


def extract_skill_name(prompt: str) -> str | None:
    """
    Extract CAT skill name from user prompt.

    Matches patterns like:
    - /cat:init
    - cat:status
    - /cat:work 1.0
    - /cat:add make it faster

    Returns the skill name (e.g., "init", "status") or None if not a CAT command.
    """
    # Match /cat:<skill> or cat:<skill> with optional arguments
    match = re.match(r'^[\s]*/?(cat:([a-z-]+))(?:\s|$)', prompt, re.IGNORECASE)
    if match:
        return match.group(2).lower()
    return None


def output_hook_response(hook_type: str, message: str) -> None:
    """Output hook response in Claude Code format."""
    response = {
        "additionalContext": f"<{hook_type}>{message}</{hook_type}>"
    }
    print(json.dumps(response))


def main():
    # Read hook input
    hook_data = read_hook_input()

    # Extract user prompt (try multiple field names)
    user_prompt = (
        hook_data.get("message") or
        hook_data.get("user_message") or
        hook_data.get("prompt") or
        ""
    )

    if not user_prompt:
        print("{}")
        return

    # Check if this is a CAT skill invocation
    skill_name = extract_skill_name(user_prompt)
    if not skill_name:
        print("{}")
        return

    # Get handler for this skill
    handler = get_handler(skill_name)
    if not handler:
        # No precompute handler for this skill - that's fine
        print("{}")
        return

    # Extract additional context needed by handlers
    session_id = hook_data.get("session_id", "")

    # Determine project root
    project_root = os.environ.get("CLAUDE_PROJECT_DIR", "")
    if not project_root or not Path(project_root, ".claude", "cat").is_dir():
        if Path(".claude/cat").is_dir():
            project_root = os.getcwd()
        else:
            project_root = ""

    # Build context for handler
    context = {
        "user_prompt": user_prompt,
        "session_id": session_id,
        "project_root": project_root,
        "plugin_root": str(SCRIPT_DIR.parent),
        "hook_data": hook_data,
    }

    # Run handler
    try:
        result = handler.handle(context)
        if result:
            output_hook_response("UserPromptSubmit", result)
        else:
            print("{}")
    except Exception as e:
        # Log error but don't break the hook
        sys.stderr.write(f"skill-dispatcher: handler error for {skill_name}: {e}\n")
        print("{}")


if __name__ == "__main__":
    main()

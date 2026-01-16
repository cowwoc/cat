#!/usr/bin/env python3
"""
get-skill-output.py - Unified UserPromptSubmit hook for CAT

TRIGGER: UserPromptSubmit

This dispatcher consolidates all UserPromptSubmit hooks into a single Python
entry point, handling both:
1. Skill precomputation (for /cat:* commands)
2. Prompt pattern checking (for all prompts)

Architecture:
- Single hook in hooks.json (reduces shell spawn overhead)
- skill_handlers/ - precompute output for /cat:* commands
- prompt_handlers/ - pattern checking for all prompts

Benefits over individual bash scripts:
1. Single Python startup vs N shell startups
2. Native JSON parsing vs multiple jq calls
3. Shared initialization code
4. Better error handling
"""

import json
import os
import re
import sys
from pathlib import Path

# Add handlers directory to path
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

from skill_handlers import get_handler as get_skill_handler
from prompt_handlers import get_all_handlers as get_prompt_handlers


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
    match = re.match(r'^[\s]*/?(cat:([a-z-]+))(?:\s|$)', prompt, re.IGNORECASE)
    if match:
        return match.group(2).lower()
    return None


def main():
    # Read hook input
    hook_data = read_hook_input()

    # Extract user prompt
    user_prompt = (
        hook_data.get("message") or
        hook_data.get("user_message") or
        hook_data.get("prompt") or
        ""
    )

    if not user_prompt:
        print("{}")
        return

    session_id = hook_data.get("session_id", "")

    # Collect all outputs
    outputs = []

    # 1. Run prompt handlers (pattern checking for all prompts)
    for handler in get_prompt_handlers():
        try:
            result = handler.check(user_prompt, session_id)
            if result:
                outputs.append(result)
        except Exception as e:
            sys.stderr.write(f"get-skill-output: prompt handler error: {e}\n")

    # 2. Run skill handler if this is a /cat:* command
    skill_name = extract_skill_name(user_prompt)
    if skill_name:
        handler = get_skill_handler(skill_name)
        if handler:
            # Determine project root
            project_root = os.environ.get("CLAUDE_PROJECT_DIR", "")
            if not project_root or not Path(project_root, ".claude", "cat").is_dir():
                if Path(".claude/cat").is_dir():
                    project_root = os.getcwd()
                else:
                    project_root = ""

            context = {
                "user_prompt": user_prompt,
                "session_id": session_id,
                "project_root": project_root,
                "plugin_root": str(SCRIPT_DIR.parent),
                "hook_data": hook_data,
            }

            try:
                result = handler.handle(context)
                if result:
                    outputs.append(result)
            except Exception as e:
                sys.stderr.write(f"get-skill-output: skill handler error for {skill_name}: {e}\n")

    # Output combined results
    if outputs:
        # Wrap each output in system-reminder tags and combine
        combined = "\n".join(
            f"<system-reminder>\n{output}\n</system-reminder>"
            for output in outputs
        )
        response = {"additionalContext": combined}
        print(json.dumps(response))
    else:
        print("{}")


if __name__ == "__main__":
    main()

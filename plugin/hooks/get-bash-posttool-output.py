#!/usr/bin/env python3
"""
get-bash-posttool-output.py - Unified PostToolUse hook for Bash commands

TRIGGER: PostToolUse (matcher: Bash)

Consolidates all Bash command result validation hooks into a single Python dispatcher.

Handlers can:
- Warn about command results (return {"warning": "..."})
- Allow silently (return None)

Benefits:
1. Single Python startup vs N shell startups
2. Native JSON parsing
3. Shared subprocess calls
4. Better error handling
"""

import json
import sys
from pathlib import Path

# Add handlers directory to path
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

from bash_posttool_handlers import get_all_handlers


def read_hook_input() -> dict:
    """Read and parse JSON input from stdin."""
    if sys.stdin.isatty():
        return {}

    try:
        raw = sys.stdin.read()
        return json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        return {}


def main():
    # Read hook input
    hook_data = read_hook_input()

    # Extract tool info
    tool_name = hook_data.get("tool_name", "")
    if tool_name.lower() != "bash":
        print("{}")
        return

    tool_input = hook_data.get("tool_input", {})
    tool_result = hook_data.get("tool_result", {}) or hook_data.get("tool_response", {})
    command = tool_input.get("command", "")

    if not command:
        print("{}")
        return

    # Build context for handlers
    context = {
        "tool_input": tool_input,
        "tool_result": tool_result,
        "session_id": hook_data.get("session_id", ""),
        "hook_data": hook_data,
    }

    # Run all handlers
    warnings = []
    for handler in get_all_handlers():
        try:
            result = handler.check(command, context)
            if result and result.get("warning"):
                warnings.append(result["warning"])
        except Exception as e:
            sys.stderr.write(f"get-bash-posttool-output: handler error: {e}\n")

    # Output warnings if any
    if warnings:
        for warning in warnings:
            sys.stderr.write(warning + "\n")

    # Always allow (PostToolUse cannot block, only warn)
    print("{}")


if __name__ == "__main__":
    main()

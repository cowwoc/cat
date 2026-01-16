#!/usr/bin/env python3
"""
get-posttool-output.py - Unified PostToolUse hook for all tools

TRIGGER: PostToolUse (no matcher - runs for all tools)

Consolidates general PostToolUse hooks into a single Python dispatcher.
For Bash-specific PostToolUse hooks, see get-bash-posttool-output.py.

Handlers can:
- Warn about tool results (return {"warning": "..."})
- Inject additional context (return {"additionalContext": "..."})
- Allow silently (return None)
"""

import json
import sys
from pathlib import Path

# Add handlers directory to path
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

from posttool_handlers import get_all_handlers


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
    tool_result = hook_data.get("tool_result", {}) or hook_data.get("tool_response", {})
    session_id = hook_data.get("session_id", "")

    if not tool_name:
        print("{}")
        return

    # Build context for handlers
    context = {
        "session_id": session_id,
        "hook_data": hook_data,
    }

    # Run all handlers
    warnings = []
    additional_contexts = []

    for handler in get_all_handlers():
        try:
            result = handler.check(tool_name, tool_result, context)
            if result:
                if result.get("warning"):
                    warnings.append(result["warning"])
                if result.get("additionalContext"):
                    additional_contexts.append(result["additionalContext"])
        except Exception as e:
            sys.stderr.write(f"get-posttool-output: handler error: {e}\n")

    # Output warnings to stderr
    if warnings:
        for warning in warnings:
            sys.stderr.write(warning + "\n")

    # Build response with additionalContext if present
    if additional_contexts:
        response = {
            "hookSpecificOutput": {
                "hookEventName": "PostToolUse",
                "additionalContext": "\n\n".join(additional_contexts)
            }
        }
        print(json.dumps(response))
    else:
        print("{}")


if __name__ == "__main__":
    main()

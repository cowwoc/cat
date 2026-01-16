#!/usr/bin/env python3
"""
get-read-pretool-output.py - Unified PreToolUse hook for Read/Glob/Grep

TRIGGER: PreToolUse (matcher: Read|Glob|Grep)

Consolidates read operation validation hooks into a single Python dispatcher.

Handlers can:
- Warn about patterns (return {"warning": "..."})
- Block operations (return {"block": True, "message": "..."})
- Allow silently (return None)

Benefits:
1. Single Python startup vs N shell startups
2. Native JSON parsing (no jq subprocesses)
3. Shared state management
4. Better error handling
"""

import json
import sys
from pathlib import Path

# Add handlers directory to path
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

from read_pretool_handlers import get_all_handlers


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
    if tool_name not in ("Read", "Glob", "Grep"):
        print("{}")
        return

    tool_input = hook_data.get("tool_input", {})

    # Build context for handlers
    context = {
        "tool_input": tool_input,
        "session_id": hook_data.get("session_id", ""),
        "hook_data": hook_data,
    }

    # Run all handlers
    warnings = []
    for handler in get_all_handlers():
        try:
            result = handler.check(tool_name, context)
            if result:
                if result.get("block"):
                    # Block the operation
                    print(json.dumps({
                        "decision": "block",
                        "reason": result.get("message", "Blocked by handler")
                    }))
                    return
                if result.get("warning"):
                    warnings.append(result["warning"])
        except Exception as e:
            sys.stderr.write(f"get-read-pretool-output: handler error: {e}\n")

    # Output warnings if any
    if warnings:
        for warning in warnings:
            sys.stderr.write(warning + "\n")

    # Allow the operation
    print("{}")


if __name__ == "__main__":
    main()

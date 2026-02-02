#!/usr/bin/env python3
"""
get-bash-pretool-output.py - Unified PreToolUse hook for Bash commands

TRIGGER: PreToolUse (matcher: Bash)

Consolidates all Bash command validation hooks into a single Python dispatcher.

Handlers can:
- Block commands (return {"decision": "block", "reason": "..."})
- Warn about commands (return {"warning": "..."})
- Allow commands (return None)

Benefits:
1. Single Python startup vs N shell startups
2. Native JSON parsing
3. Shared pattern matching
4. Better error handling
"""

import json
import sys
from pathlib import Path

# Add handlers directory to path
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

from bash_handlers import get_all_handlers


def read_hook_input() -> dict:
    """Read and parse JSON input from stdin."""
    if sys.stdin.isatty():
        return {}

    try:
        raw = sys.stdin.read()
        return json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        return {}


def output_block(reason: str, additional_context: str = None) -> None:
    """Output a block decision."""
    response = {
        "decision": "block",
        "reason": reason
    }
    if additional_context:
        response["additionalContext"] = additional_context
    print(json.dumps(response))


def output_warning(warning: str) -> None:
    """Output a warning (stderr) but allow the command."""
    sys.stderr.write(warning + "\n")
    print("{}")


def main():
    # Read hook input
    hook_data = read_hook_input()

    # Extract tool info
    tool_name = hook_data.get("tool_name", "")
    if tool_name.lower() != "bash":
        print("{}")
        return

    tool_input = hook_data.get("tool_input", {})
    command = tool_input.get("command", "")

    if not command:
        print("{}")
        return

    # Build context for handlers
    # CRITICAL (M360): Include cwd from hook_data so handlers can check shell's working directory
    context = {
        "tool_input": tool_input,
        "session_id": hook_data.get("session_id", ""),
        "cwd": hook_data.get("cwd", ""),  # M360: Pass cwd for worktree validation
        "hook_data": hook_data,
    }

    # Run all handlers
    warnings = []
    for handler in get_all_handlers():
        try:
            result = handler.check(command, context)
            if result:
                # Block decision takes priority
                if result.get("decision") == "block":
                    output_block(
                        result.get("reason", "Command blocked"),
                        result.get("additionalContext")
                    )
                    return
                # Collect warnings
                if result.get("warning"):
                    warnings.append(result["warning"])
        except Exception as e:
            sys.stderr.write(f"get-bash-pretool-output: handler error: {e}\n")

    # Output warnings if any
    if warnings:
        for warning in warnings:
            sys.stderr.write(warning + "\n")

    # Allow the command
    print("{}")


if __name__ == "__main__":
    main()

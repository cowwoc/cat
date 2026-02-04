#!/usr/bin/env python3
"""
enforce-status-output.py - Stop hook to enforce verbatim status box output

HOOK: Stop
TRIGGER: When /cat:status was invoked in this turn

M402: After 3+ documentation-level failures (M341, M395, M401), this hook
escalates enforcement to programmatic level.

Detects when:
1. User invoked /cat:status in the current turn
2. Agent's response did NOT contain the status box (╭── characters)

Returns decision=block to force Claude to output the status box verbatim.
"""

import json
import sys
from pathlib import Path


def read_hook_input() -> dict:
    """Read and parse JSON input from stdin."""
    if sys.stdin.isatty():
        return {}
    try:
        raw = sys.stdin.read()
        return json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        return {}


def check_transcript_for_status_skill(transcript_path: str) -> tuple[bool, bool]:
    """
    Check the transcript to see if /cat:status was invoked and if output was correct.

    Returns:
        (status_invoked, has_box_output): Whether status was invoked and whether
        the response contained the expected box characters.
    """
    if not transcript_path or not Path(transcript_path).exists():
        return False, True  # Can't check, assume OK

    try:
        lines = Path(transcript_path).read_text().strip().split('\n')
    except Exception:
        return False, True

    # Look at recent messages (last 10 entries)
    recent_lines = lines[-10:] if len(lines) > 10 else lines

    status_invoked = False
    has_box_output = False

    for line in recent_lines:
        if not line.strip():
            continue
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            continue

        # Check user messages for /cat:status invocation
        if entry.get("type") == "user":
            content = entry.get("message", {}).get("content", [])
            for block in content:
                if isinstance(block, dict) and block.get("type") == "text":
                    text = block.get("text", "")
                    if "cat:status" in text.lower():
                        status_invoked = True

        # Check assistant messages for box output
        if entry.get("type") == "assistant":
            content = entry.get("message", {}).get("content", [])
            for block in content:
                if isinstance(block, dict) and block.get("type") == "text":
                    text = block.get("text", "")
                    # Check for status box markers
                    if "╭─" in text and "│" in text and "╰─" in text:
                        has_box_output = True

    return status_invoked, has_box_output


def main():
    hook_data = read_hook_input()

    # Get transcript path
    transcript_path = hook_data.get("transcript_path", "")

    # Check if this is already a stop hook continuation (prevent infinite loop)
    stop_hook_active = hook_data.get("stop_hook_active", False)
    if stop_hook_active:
        # Already continuing from a stop hook, don't block again
        print("{}")
        return

    # Check transcript for status invocation and output
    status_invoked, has_box_output = check_transcript_for_status_skill(transcript_path)

    if status_invoked and not has_box_output:
        # Block the stop and tell Claude to output the status box
        response = {
            "decision": "block",
            "reason": (
                "M402 ENFORCEMENT: /cat:status was invoked but you did NOT output the status box. "
                "The skill's MANDATORY OUTPUT REQUIREMENT states: Copy-paste ALL content between "
                "the START and END markers. You summarized instead of copy-pasting. "
                "OUTPUT THE COMPLETE STATUS BOX NOW - including the ╭── border, all issue lines, "
                "the NEXT STEPS table, and the Legend. Do NOT summarize or interpret."
            )
        }
        print(json.dumps(response))
    else:
        print("{}")


if __name__ == "__main__":
    main()

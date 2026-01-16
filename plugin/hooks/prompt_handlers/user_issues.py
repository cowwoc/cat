"""
User-reported issue detection handler.

Detects when users report issues/bugs and flags as detection gaps
requiring TDD workflow before fixing.
"""

import json
import os
import sys
from datetime import datetime
from pathlib import Path

from . import register_handler

ISSUE_PATTERNS = [
    "this is wrong",
    "this is incorrect",
    "that's wrong",
    "that's incorrect",
    "bug in",
    "there's a bug",
    "doesn't work",
    "isn't working",
    "not working",
    "broken",
    "should be",
    "should have been",
    "you missed",
    "missing from",
    "forgot to",
    "failed to",
    "why didn't",
    "why isn't",
    "still showing",
    "still has",
    "still contains",
    "didn't catch",
    "wasn't caught",
    "wasn't detected",
    "not detected",
    "false positive",
    "false negative",
    "incorrect output",
    "wrong output",
    "wrong result",
    "incorrect result",
    # M247: User message handling failures
    "ignored",
    "you ignored",
    "didn't acknowledge",
    "didn't respond to",
    "expected behavior",
]


class UserIssuesHandler:
    """Detect user-reported issues and flag as detection gaps."""

    def check(self, prompt: str, session_id: str) -> str | None:
        """Check for issue patterns."""
        prompt_lower = prompt.lower()

        matched_pattern = None
        for pattern in ISSUE_PATTERNS:
            if pattern in prompt_lower:
                matched_pattern = pattern
                break

        if not matched_pattern:
            return None

        # Record the gap
        timestamp = datetime.now().isoformat()
        gap_id = f"GAP-{int(datetime.now().timestamp())}"

        if session_id:
            gaps_file = Path(f"/tmp/pending_detection_gaps_{session_id}.json")
            try:
                if gaps_file.exists():
                    data = json.loads(gaps_file.read_text())
                else:
                    data = {"gaps": [], "created": timestamp}

                data["gaps"].append({
                    "id": gap_id,
                    "pattern": matched_pattern,
                    "user_message": prompt[:500],  # Truncate for storage
                    "timestamp": timestamp,
                    "status": "pending_tdd",
                    "test_written": False,
                })

                gaps_file.write_text(json.dumps(data, indent=2))
            except Exception:
                pass  # Non-blocking

        return f"""╭─ 🔍 DETECTION GAP IDENTIFIED
│
│  The user reported an issue that our validation didn't catch.
│  This is a DETECTION GAP requiring TDD workflow.
│
│  Pattern matched: "{matched_pattern}"
│  Gap ID: {gap_id}
│
│  REQUIRED WORKFLOW (Test-Driven Bug Fix):
│  1. Write a FAILING test that reproduces the user's issue
│  2. Verify the test FAILS (proves it catches the bug)
│  3. Fix the code
│  4. Verify the test PASSES
│
│  Invoke: Skill: tdd-implementation
╰─"""


# Register handler
register_handler(UserIssuesHandler())

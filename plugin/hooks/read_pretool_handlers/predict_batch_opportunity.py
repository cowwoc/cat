"""
Batch Opportunity Prediction Handler

Tracks sequential Read/Glob/Grep operations and warns when batching
would be more efficient. Advisory only - never blocks.

Trigger: PreToolUse for Read|Glob|Grep
"""

import json
import time
from pathlib import Path
from . import register_handler


WINDOW_SECONDS = 30  # Track operations within 30-second windows
THRESHOLD = 3        # Warn after 3 sequential operations
WARNING_COOLDOWN = 60  # Only warn once per minute


class PredictBatchOpportunityHandler:
    """Track sequential read operations and suggest batching."""

    def _get_tracker_file(self, session_id: str) -> Path:
        """Get the tracker file path for a session."""
        return Path(f"/tmp/batch_tracker_{session_id}.json")

    def _load_tracker(self, tracker_file: Path) -> dict:
        """Load tracker state from file."""
        if not tracker_file.exists():
            return {"operations": [], "warnings_shown": 0, "last_warning": 0}
        try:
            return json.loads(tracker_file.read_text())
        except (json.JSONDecodeError, IOError):
            return {"operations": [], "warnings_shown": 0, "last_warning": 0}

    def _save_tracker(self, tracker_file: Path, state: dict) -> None:
        """Save tracker state to file atomically."""
        temp_file = tracker_file.with_suffix(f".tmp.{time.time()}")
        try:
            temp_file.write_text(json.dumps(state))
            temp_file.rename(tracker_file)
        except IOError:
            temp_file.unlink(missing_ok=True)

    def check(self, tool_name: str, context: dict) -> dict | None:
        session_id = context.get("session_id", "")
        if not session_id:
            return None

        tool_input = context.get("tool_input", {})
        file_path = (
            tool_input.get("file_path") or
            tool_input.get("path") or
            tool_input.get("pattern") or
            ""
        )

        timestamp = int(time.time())
        tracker_file = self._get_tracker_file(session_id)
        state = self._load_tracker(tracker_file)

        # Add this operation
        state["operations"].append({
            "tool": tool_name,
            "path": file_path,
            "timestamp": timestamp
        })

        # Clean old operations (outside window)
        cutoff = timestamp - WINDOW_SECONDS
        state["operations"] = [
            op for op in state["operations"]
            if op.get("timestamp", 0) > cutoff
        ]

        recent_count = len(state["operations"])
        last_warning = state.get("last_warning", 0)
        time_since_warning = timestamp - last_warning

        # Check if we should warn
        warning = None
        if recent_count >= THRESHOLD and time_since_warning > WARNING_COOLDOWN:
            state["last_warning"] = timestamp
            state["warnings_shown"] = state.get("warnings_shown", 0) + 1

            recent_paths = ", ".join(
                op.get("path", "(unknown)") for op in state["operations"]
            )

            warning = f"""
BATCH OPPORTUNITY DETECTED

{recent_count} sequential {tool_name} operations in the last {WINDOW_SECONDS}s.

Recent targets: {recent_paths}

CONSIDER BATCHING:
  - Use parallel tool calls in a single message
  - Use Glob with pattern matching instead of multiple Reads
  - Use Grep with broader scope instead of multiple searches
  - Use the batch-read skill for coordinated file reading

EFFICIENCY TIP:
  Instead of:  Read file1, Read file2, Read file3 (sequential)
  Use:         Read file1 + Read file2 + Read file3 (parallel in one message)
"""

        self._save_tracker(tracker_file, state)

        if warning:
            return {"warning": warning}
        return None


register_handler(PredictBatchOpportunityHandler())

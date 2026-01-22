"""
Detect Sequential Tool Execution Anti-Pattern

Monitors Read/Glob/Grep/WebFetch/WebSearch PostToolUse events and warns
when multiple sequential single-tool messages are detected, suggesting
batching for efficiency.

Trigger: PostToolUse for Read|Glob|Grep|WebFetch|WebSearch
"""

import json
import time
from pathlib import Path
from . import register_handler


BATCHABLE_TOOLS = {"Read", "Glob", "Grep", "WebFetch", "WebSearch"}
WINDOW_SECONDS = 30  # Operations within this window are considered sequential
THRESHOLD = 3        # Warn after this many sequential operations


class DetectSequentialToolsHandler:
    """Detect sequential independent tool execution anti-pattern."""

    def _get_state_file(self, session_id: str) -> Path:
        """Get the state file path for a session."""
        return Path(f"/tmp/sequential-tool-tracker-{session_id}.json")

    def _load_state(self, state_file: Path) -> dict:
        """Load state from file."""
        if not state_file.exists():
            return {"last_tool_time": 0, "sequential_count": 0, "last_tool_names": []}
        try:
            return json.loads(state_file.read_text())
        except (json.JSONDecodeError, IOError):
            return {"last_tool_time": 0, "sequential_count": 0, "last_tool_names": []}

    def _save_state(self, state_file: Path, state: dict) -> None:
        """Save state to file."""
        try:
            state_file.write_text(json.dumps(state))
        except IOError:
            pass

    def check(self, tool_name: str, context: dict) -> dict | None:
        session_id = context.get("session_id", "")
        if not session_id:
            return None

        # Check if tool is batchable
        if tool_name not in BATCHABLE_TOOLS:
            return None

        # Get tool count from hook data (if multiple tools in one message)
        hook_data = context.get("hook_data", {})
        tool_count = hook_data.get("tool", {}).get("count", 1)

        current_time = int(time.time())
        state_file = self._get_state_file(session_id)
        state = self._load_state(state_file)

        last_tool_time = state.get("last_tool_time", 0)
        sequential_count = state.get("sequential_count", 0)
        last_tool_names = state.get("last_tool_names", [])

        # If multiple tools in one message, reset counter (already batching)
        if tool_count > 1:
            self._save_state(state_file, {
                "last_tool_time": current_time,
                "sequential_count": 0,
                "last_tool_names": []
            })
            return None

        # If more than 30 seconds since last tool, reset (different context)
        time_diff = current_time - last_tool_time
        if time_diff > WINDOW_SECONDS:
            self._save_state(state_file, {
                "last_tool_time": current_time,
                "sequential_count": 1,
                "last_tool_names": [tool_name]
            })
            return None

        # Increment sequential count
        new_count = sequential_count + 1
        new_tool_names = last_tool_names + [tool_name]

        # Warn if 3+ sequential single-tool messages detected
        warning = None
        if new_count >= THRESHOLD:
            unique_tools = sorted(set(new_tool_names))
            tool_list = ", ".join(unique_tools)

            warning = f"""## PERFORMANCE: Sequential Tool Execution Detected

**Pattern**: {new_count} consecutive single-tool messages
**Tools**: {tool_list}

## ANTI-PATTERN DETECTED (25-30% overhead)

**Current Pattern** (sequential execution):
```
Message 1: {tool_name} file_1
Message 2: {tool_name} file_2
Message 3: {tool_name} file_3
# Result: 3 round-trips = 200-300 extra messages per session
```

## REQUIRED PATTERN (parallel execution)

**Batch Independent Tools in Single Message**:
```
Single Message:
  {tool_name} file_1 +
  {tool_name} file_2 +
  {tool_name} file_3
# Result: 1 round-trip = 67% message reduction
```

## BATCHING RULES

**ALWAYS batch these tools when independent**:
1. Read operations - batch all file reads together
2. Glob patterns - batch all file searches together
3. Grep searches - batch all content searches together
4. WebFetch/WebSearch - batch all web operations together
5. Agent invocations - launch all agents in parallel

**Only use sequential when**:
- Operations have dependencies (later tool needs earlier tool's output)
- Conditional logic required between operations

**This reminder will reset after you batch tools or 30 seconds of inactivity.**"""

            # Reset counter after warning
            self._save_state(state_file, {
                "last_tool_time": current_time,
                "sequential_count": 0,
                "last_tool_names": []
            })
        else:
            # Update state
            self._save_state(state_file, {
                "last_tool_time": current_time,
                "sequential_count": new_count,
                "last_tool_names": new_tool_names
            })

        if warning:
            return {"warning": warning}
        return None


register_handler(DetectSequentialToolsHandler())

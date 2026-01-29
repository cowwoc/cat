"""Tests for MonitorSubagentsHandler."""

import json
import sys
from pathlib import Path
from unittest.mock import patch, MagicMock

import pytest

# Add plugin path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "plugin" / "hooks"))

from skill_handlers.monitor_subagents_handler import MonitorSubagentsHandler


class TestMonitorSubagentsHandler:
    """Tests for MonitorSubagentsHandler class."""

    @pytest.fixture
    def handler(self):
        """Create a MonitorSubagentsHandler instance."""
        return MonitorSubagentsHandler()

    @pytest.fixture
    def basic_context(self, tmp_path):
        """Create basic context for testing."""
        return {
            "project_root": str(tmp_path),
            "session_id": "test-session-123",
        }

    def test_returns_string(self, handler, basic_context):
        """Handler returns a string."""
        with patch.object(handler, '_get_worktrees', return_value=[]):
            result = handler.handle(basic_context)
        assert isinstance(result, str)

    def test_contains_marker(self, handler, basic_context):
        """Output contains SCRIPT OUTPUT marker."""
        with patch.object(handler, '_get_worktrees', return_value=[]):
            result = handler.handle(basic_context)
        assert "SCRIPT OUTPUT MONITOR SUBAGENTS" in result

    def test_contains_instruction(self, handler, basic_context):
        """Output contains INSTRUCTION marker."""
        with patch.object(handler, '_get_worktrees', return_value=[]):
            result = handler.handle(basic_context)
        assert "INSTRUCTION:" in result

    def test_empty_worktrees(self, handler, basic_context):
        """Handler handles empty worktree list."""
        with patch.object(handler, '_get_worktrees', return_value=[]):
            result = handler.handle(basic_context)

        # Parse JSON from output
        json_start = result.find("{")
        json_end = result.rfind("}") + 1
        data = json.loads(result[json_start:json_end])

        assert data["summary"]["total"] == 0
        assert data["subagents"] == []


class TestSubagentDetection:
    """Tests for subagent worktree detection."""

    @pytest.fixture
    def handler(self):
        """Create a MonitorSubagentsHandler instance."""
        return MonitorSubagentsHandler()

    @pytest.fixture
    def context_with_worktrees(self, tmp_path):
        """Create context with mock worktree structure."""
        return {
            "project_root": str(tmp_path),
            "session_id": "test-session-123",
        }

    def test_ignores_non_subagent_worktrees(self, handler, context_with_worktrees):
        """Handler ignores worktrees without -sub- in name."""
        worktrees = [
            "/workspace/.worktrees/1.0-task",
            "/workspace/.worktrees/2.0-feature",
        ]
        with patch.object(handler, '_get_worktrees', return_value=worktrees):
            result = handler.handle(context_with_worktrees)

        json_start = result.find("{")
        json_end = result.rfind("}") + 1
        data = json.loads(result[json_start:json_end])

        assert data["summary"]["total"] == 0

    def test_detects_subagent_worktrees(self, handler, context_with_worktrees, tmp_path):
        """Handler detects worktrees with -sub- in name."""
        # Create subagent worktree paths
        sub_worktree = tmp_path / ".worktrees" / "1.0-task-sub-a1b2c3d4"
        sub_worktree.mkdir(parents=True)

        worktrees = [str(sub_worktree)]

        with patch.object(handler, '_get_worktrees', return_value=worktrees):
            with patch.object(handler, '_check_running_status', return_value=("running", 0, 0)):
                result = handler.handle(context_with_worktrees)

        json_start = result.find("{")
        json_end = result.rfind("}") + 1
        data = json.loads(result[json_start:json_end])

        assert data["summary"]["total"] == 1
        assert len(data["subagents"]) == 1

    def test_extracts_subagent_id(self, handler, context_with_worktrees, tmp_path):
        """Handler extracts correct subagent ID from path."""
        sub_worktree = tmp_path / ".worktrees" / "1.0-task-sub-deadbeef"
        sub_worktree.mkdir(parents=True)

        worktrees = [str(sub_worktree)]

        with patch.object(handler, '_get_worktrees', return_value=worktrees):
            with patch.object(handler, '_check_running_status', return_value=("running", 0, 0)):
                result = handler.handle(context_with_worktrees)

        json_start = result.find("{")
        json_end = result.rfind("}") + 1
        data = json.loads(result[json_start:json_end])

        assert data["subagents"][0]["id"] == "deadbeef"

    def test_extracts_task_name(self, handler, context_with_worktrees, tmp_path):
        """Handler extracts correct task name from path."""
        sub_worktree = tmp_path / ".worktrees" / "1.2-parser-sub-a1b2c3d4"
        sub_worktree.mkdir(parents=True)

        worktrees = [str(sub_worktree)]

        with patch.object(handler, '_get_worktrees', return_value=worktrees):
            with patch.object(handler, '_check_running_status', return_value=("running", 0, 0)):
                result = handler.handle(context_with_worktrees)

        json_start = result.find("{")
        json_end = result.rfind("}") + 1
        data = json.loads(result[json_start:json_end])

        assert data["subagents"][0]["task"] == "1.2-parser"


class TestCompletionDetection:
    """Tests for subagent completion detection."""

    @pytest.fixture
    def handler(self):
        """Create a MonitorSubagentsHandler instance."""
        return MonitorSubagentsHandler()

    @pytest.fixture
    def context(self, tmp_path):
        """Create context."""
        return {
            "project_root": str(tmp_path),
            "session_id": "test-session-123",
        }

    def test_detects_completion_file(self, handler, context, tmp_path):
        """Handler detects .completion.json file."""
        sub_worktree = tmp_path / ".worktrees" / "1.0-task-sub-a1b2c3d4"
        sub_worktree.mkdir(parents=True)

        # Create completion file
        completion = sub_worktree / ".completion.json"
        completion.write_text(json.dumps({
            "tokensUsed": 50000,
            "compactionEvents": 0
        }))

        worktrees = [str(sub_worktree)]

        with patch.object(handler, '_get_worktrees', return_value=worktrees):
            result = handler.handle(context)

        json_start = result.find("{")
        json_end = result.rfind("}") + 1
        data = json.loads(result[json_start:json_end])

        assert data["subagents"][0]["status"] == "complete"
        assert data["subagents"][0]["tokens"] == 50000
        assert data["summary"]["complete"] == 1

    def test_reads_compaction_events(self, handler, context, tmp_path):
        """Handler reads compaction events from completion file."""
        sub_worktree = tmp_path / ".worktrees" / "1.0-task-sub-a1b2c3d4"
        sub_worktree.mkdir(parents=True)

        completion = sub_worktree / ".completion.json"
        completion.write_text(json.dumps({
            "tokensUsed": 75000,
            "compactionEvents": 2
        }))

        worktrees = [str(sub_worktree)]

        with patch.object(handler, '_get_worktrees', return_value=worktrees):
            result = handler.handle(context)

        json_start = result.find("{")
        json_end = result.rfind("}") + 1
        data = json.loads(result[json_start:json_end])

        assert data["subagents"][0]["compactions"] == 2


class TestWarningStatus:
    """Tests for warning status detection."""

    @pytest.fixture
    def handler(self):
        """Create a MonitorSubagentsHandler instance."""
        return MonitorSubagentsHandler()

    @pytest.fixture
    def context(self, tmp_path):
        """Create context."""
        return {
            "project_root": str(tmp_path),
            "session_id": "test-session-123",
        }

    def test_warning_when_tokens_exceed_threshold(self, handler, context, tmp_path):
        """Handler sets warning status when tokens >= 80K."""
        sub_worktree = tmp_path / ".worktrees" / "1.0-task-sub-a1b2c3d4"
        sub_worktree.mkdir(parents=True)

        worktrees = [str(sub_worktree)]

        # Mock running status with high token count
        with patch.object(handler, '_get_worktrees', return_value=worktrees):
            with patch.object(handler, '_check_running_status', return_value=("running", 85000, 0)):
                result = handler.handle(context)

        json_start = result.find("{")
        json_end = result.rfind("}") + 1
        data = json.loads(result[json_start:json_end])

        assert data["subagents"][0]["status"] == "warning"
        assert data["summary"]["warning"] == 1

    def test_running_when_below_threshold(self, handler, context, tmp_path):
        """Handler sets running status when tokens < 80K."""
        sub_worktree = tmp_path / ".worktrees" / "1.0-task-sub-a1b2c3d4"
        sub_worktree.mkdir(parents=True)

        worktrees = [str(sub_worktree)]

        with patch.object(handler, '_get_worktrees', return_value=worktrees):
            with patch.object(handler, '_check_running_status', return_value=("running", 45000, 0)):
                result = handler.handle(context)

        json_start = result.find("{")
        json_end = result.rfind("}") + 1
        data = json.loads(result[json_start:json_end])

        assert data["subagents"][0]["status"] == "running"
        assert data["summary"]["running"] == 1


class TestSummaryCalculation:
    """Tests for summary calculation."""

    @pytest.fixture
    def handler(self):
        """Create a MonitorSubagentsHandler instance."""
        return MonitorSubagentsHandler()

    @pytest.fixture
    def context(self, tmp_path):
        """Create context."""
        return {
            "project_root": str(tmp_path),
            "session_id": "test-session-123",
        }

    def test_multiple_subagents(self, handler, context, tmp_path):
        """Handler handles multiple subagents with different statuses."""
        # Create three subagent worktrees
        sub1 = tmp_path / ".worktrees" / "1.0-task-a-sub-aaaaaaaa"
        sub2 = tmp_path / ".worktrees" / "1.0-task-b-sub-bbbbbbbb"
        sub3 = tmp_path / ".worktrees" / "1.0-task-c-sub-cccccccc"
        for sub in [sub1, sub2, sub3]:
            sub.mkdir(parents=True)

        # sub1 is complete
        (sub1 / ".completion.json").write_text(json.dumps({
            "tokensUsed": 30000,
            "compactionEvents": 0
        }))

        worktrees = [str(sub1), str(sub2), str(sub3)]

        def mock_running(path):
            if "task-b" in path:
                return ("running", 90000, 1)  # warning
            return ("running", 20000, 0)  # running

        with patch.object(handler, '_get_worktrees', return_value=worktrees):
            with patch.object(handler, '_check_running_status', side_effect=mock_running):
                result = handler.handle(context)

        json_start = result.find("{")
        json_end = result.rfind("}") + 1
        data = json.loads(result[json_start:json_end])

        assert data["summary"]["total"] == 3
        assert data["summary"]["complete"] == 1
        assert data["summary"]["warning"] == 1
        assert data["summary"]["running"] == 1


class TestWorktreeRetrieval:
    """Tests for git worktree list retrieval."""

    @pytest.fixture
    def handler(self):
        """Create a MonitorSubagentsHandler instance."""
        return MonitorSubagentsHandler()

    def test_parses_porcelain_output(self, handler, tmp_path):
        """Handler parses git worktree list --porcelain output."""
        mock_output = MagicMock()
        mock_output.returncode = 0
        mock_output.stdout = """worktree /workspace
HEAD abc123

worktree /workspace/.worktrees/1.0-task-sub-deadbeef
HEAD def456
branch refs/heads/1.0-task-sub-deadbeef
"""

        with patch('subprocess.run', return_value=mock_output):
            worktrees = handler._get_worktrees(str(tmp_path))

        assert len(worktrees) == 2
        assert "/workspace" in worktrees
        assert "/workspace/.worktrees/1.0-task-sub-deadbeef" in worktrees

    def test_handles_git_failure(self, handler, tmp_path):
        """Handler handles git command failure."""
        mock_output = MagicMock()
        mock_output.returncode = 1
        mock_output.stdout = ""

        with patch('subprocess.run', return_value=mock_output):
            worktrees = handler._get_worktrees(str(tmp_path))

        assert worktrees == []


class TestSessionFileParsing:
    """Tests for session file parsing."""

    @pytest.fixture
    def handler(self):
        """Create a MonitorSubagentsHandler instance."""
        return MonitorSubagentsHandler()

    def test_parses_session_tokens(self, handler, tmp_path):
        """Handler parses token usage from session file."""
        session_file = tmp_path / "session.jsonl"
        session_file.write_text(
            '{"type": "assistant", "message": {"usage": {"input_tokens": 1000, "output_tokens": 500}}}\n'
            '{"type": "assistant", "message": {"usage": {"input_tokens": 2000, "output_tokens": 1000}}}\n'
        )

        tokens, compactions = handler._parse_session_file(session_file)

        assert tokens == 4500  # 1000+500+2000+1000
        assert compactions == 0

    def test_counts_compactions(self, handler, tmp_path):
        """Handler counts compaction events in session file."""
        session_file = tmp_path / "session.jsonl"
        session_file.write_text(
            '{"type": "assistant", "message": {"usage": {"input_tokens": 1000, "output_tokens": 500}}}\n'
            '{"type": "summary"}\n'
            '{"type": "assistant", "message": {"usage": {"input_tokens": 2000, "output_tokens": 1000}}}\n'
            '{"type": "summary"}\n'
        )

        tokens, compactions = handler._parse_session_file(session_file)

        assert tokens == 4500
        assert compactions == 2

    def test_handles_missing_session_file(self, handler, tmp_path):
        """Handler handles missing session file."""
        session_file = tmp_path / "nonexistent.jsonl"

        tokens, compactions = handler._parse_session_file(session_file)

        assert tokens == 0
        assert compactions == 0

    def test_handles_malformed_json(self, handler, tmp_path):
        """Handler handles malformed JSON lines."""
        session_file = tmp_path / "session.jsonl"
        session_file.write_text(
            '{"type": "assistant", "message": {"usage": {"input_tokens": 1000, "output_tokens": 500}}}\n'
            'not valid json\n'
            '{"type": "assistant", "message": {"usage": {"input_tokens": 2000, "output_tokens": 1000}}}\n'
        )

        tokens, compactions = handler._parse_session_file(session_file)

        assert tokens == 4500  # Still counts valid lines


class TestReadCompletion:
    """Tests for reading completion files."""

    @pytest.fixture
    def handler(self):
        """Create a MonitorSubagentsHandler instance."""
        return MonitorSubagentsHandler()

    def test_reads_valid_completion(self, handler, tmp_path):
        """Handler reads valid completion file."""
        completion_file = tmp_path / ".completion.json"
        completion_file.write_text(json.dumps({
            "tokensUsed": 65000,
            "compactionEvents": 1
        }))

        status, tokens, compactions = handler._read_completion(completion_file)

        assert status == "complete"
        assert tokens == 65000
        assert compactions == 1

    def test_handles_missing_fields(self, handler, tmp_path):
        """Handler handles completion file with missing fields."""
        completion_file = tmp_path / ".completion.json"
        completion_file.write_text(json.dumps({}))

        status, tokens, compactions = handler._read_completion(completion_file)

        assert status == "complete"
        assert tokens == 0
        assert compactions == 0

    def test_handles_invalid_json(self, handler, tmp_path):
        """Handler handles invalid JSON in completion file."""
        completion_file = tmp_path / ".completion.json"
        completion_file.write_text("not valid json")

        status, tokens, compactions = handler._read_completion(completion_file)

        assert status == "complete"
        assert tokens == 0
        assert compactions == 0

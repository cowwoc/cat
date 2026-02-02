"""Tests for detect_validation_fabrication handler."""

from pathlib import Path
import sys
import tempfile
import shutil

# Add plugin path
sys.path.insert(0, str(Path(__file__).parent.parent / "plugin" / "hooks"))

from posttool_handlers.detect_validation_fabrication import DetectValidationFabricationHandler


class TestDetectValidationFabricationHandler:
    """Test cases for validation fabrication detection."""

    def setup_method(self):
        """Set up test fixtures."""
        self.handler = DetectValidationFabricationHandler()

    def test_ignores_non_task_tools(self):
        """Handler should only check Task tool results."""
        result = self.handler.check("Bash", {"output": "score: 1.0"}, {"session_id": "test"})
        assert result is None

        result = self.handler.check("Read", {"content": "equivalence: 0.95"}, {"session_id": "test"})
        assert result is None

    def test_ignores_results_without_scores(self):
        """Handler should ignore results without validation scores."""
        result = self.handler.check(
            "Task",
            {"output": "Task completed successfully"},
            {"session_id": "test"}
        )
        assert result is None

    def test_detects_score_patterns(self):
        """Handler should detect various score patterns."""
        handler = DetectValidationFabricationHandler()

        assert handler._contains_validation_scores("score: 1.0")
        assert handler._contains_validation_scores("equivalence_score: 0.95")
        assert handler._contains_validation_scores('"equivalence_score": 1.0')
        assert handler._contains_validation_scores("equivalence: 0.98")
        assert not handler._contains_validation_scores("no scores here")

    def test_warns_when_no_skill_evidence_in_output(self):
        """Handler should warn when scores found but no skill invocation in Task output."""
        # Task output with scores but NO mention of skill invocation
        result = self.handler.check(
            "Task",
            {"output": "Task complete. equivalence_score: 1.0 for all files."},
            {"session_id": "test"}
        )

        assert result is not None
        assert "additionalContext" in result
        assert "VALIDATION FABRICATION" in result["additionalContext"]

    def test_no_warning_when_skill_mentioned_in_output(self):
        """Handler should not warn when Task output mentions invoking /compare-docs."""
        # Task output that mentions invoking the skill
        result = self.handler.check(
            "Task",
            {"output": "Invoked /cat:compare-docs for validation. equivalence_score: 1.0"},
            {"session_id": "test"}
        )
        assert result is None

    def test_no_warning_when_skill_json_in_output(self):
        """Handler should not warn when Task output contains skill JSON."""
        # Task output with skill invocation in JSON format
        result = self.handler.check(
            "Task",
            {"output": 'Used skill": "compare-docs" for validation. Score: 0.95'},
            {"session_id": "test"}
        )
        assert result is None

    def test_no_warning_when_ran_skill_mentioned(self):
        """Handler should not warn when output says 'ran compare-docs'."""
        result = self.handler.check(
            "Task",
            {"output": "I ran compare-docs to verify. equivalence_score: 1.0"},
            {"session_id": "test"}
        )
        assert result is None

    def test_no_warning_for_shrink_doc_skill(self):
        """Handler should recognize shrink-doc as valid validation skill."""
        result = self.handler.check(
            "Task",
            {"output": "Invoked /cat:shrink-doc for compression. Score: 0.98"},
            {"session_id": "test"}
        )
        assert result is None

    def test_extracts_agent_id_json_format(self):
        """Handler should extract agentId from JSON format."""
        text = '{"agentId":"abc1234", "result": "done"}'
        agent_id = self.handler._extract_agent_id(text)
        assert agent_id == "abc1234"

    def test_extracts_agent_id_text_format(self):
        """Handler should extract agentId from text format."""
        text = "Task completed. agentId: def5678"
        agent_id = self.handler._extract_agent_id(text)
        assert agent_id == "def5678"

    def test_returns_none_when_no_agent_id(self):
        """Handler should return None when no agentId in text."""
        text = "Task completed successfully with no agent info"
        agent_id = self.handler._extract_agent_id(text)
        assert agent_id is None

    def test_check_subagent_session_returns_false_when_file_missing(self):
        """Handler should return False when subagent session file doesn't exist."""
        result = self.handler._check_subagent_session("nonexistent-session", "abc123")
        assert result is False

    def test_check_subagent_session_detects_skill_invocation(self):
        """Handler should detect Skill tool invocation in subagent session."""
        # Create a temporary session structure
        with tempfile.TemporaryDirectory() as tmpdir:
            # Override the home directory for this test
            orig_home = Path.home()

            # Create the expected directory structure
            session_id = "test-session-123"
            agent_id = "abc1234"
            hist_dir = Path(tmpdir) / ".config" / "claude" / "projects" / "-workspace"
            subagent_dir = hist_dir / session_id / "subagents"
            subagent_dir.mkdir(parents=True)

            # Create a subagent session file with Skill invocation
            subagent_file = subagent_dir / f"agent-{agent_id}.jsonl"
            subagent_file.write_text(
                '{"type":"tool_use","name":"Skill","input":{"skill":"compare-docs"}}\n'
            )

            # Temporarily patch the handler to use our temp directory
            import posttool_handlers.detect_validation_fabrication as module
            orig_path_home = Path.home

            try:
                # Monkey-patch Path.home() for this test
                Path.home = lambda: Path(tmpdir)

                result = self.handler._check_subagent_session(session_id, agent_id)
                assert result is True
            finally:
                Path.home = orig_path_home

    def test_check_subagent_session_no_skill_invocation(self):
        """Handler should return False when subagent didn't invoke validation skill."""
        with tempfile.TemporaryDirectory() as tmpdir:
            session_id = "test-session-456"
            agent_id = "def5678"
            hist_dir = Path(tmpdir) / ".config" / "claude" / "projects" / "-workspace"
            subagent_dir = hist_dir / session_id / "subagents"
            subagent_dir.mkdir(parents=True)

            # Create a subagent session file WITHOUT Skill invocation
            subagent_file = subagent_dir / f"agent-{agent_id}.jsonl"
            subagent_file.write_text(
                '{"type":"tool_use","name":"Read","input":{"file_path":"/test"}}\n'
                '{"type":"tool_use","name":"Edit","input":{"file_path":"/test"}}\n'
            )

            orig_path_home = Path.home
            try:
                Path.home = lambda: Path(tmpdir)
                result = self.handler._check_subagent_session(session_id, agent_id)
                assert result is False
            finally:
                Path.home = orig_path_home


def run_tests():
    """Run all tests."""
    import traceback
    test_instance = TestDetectValidationFabricationHandler()

    tests = [
        ("test_ignores_non_task_tools", test_instance.test_ignores_non_task_tools),
        ("test_ignores_results_without_scores", test_instance.test_ignores_results_without_scores),
        ("test_detects_score_patterns", test_instance.test_detects_score_patterns),
        ("test_warns_when_no_skill_evidence_in_output", test_instance.test_warns_when_no_skill_evidence_in_output),
        ("test_no_warning_when_skill_mentioned_in_output", test_instance.test_no_warning_when_skill_mentioned_in_output),
        ("test_no_warning_when_skill_json_in_output", test_instance.test_no_warning_when_skill_json_in_output),
        ("test_no_warning_when_ran_skill_mentioned", test_instance.test_no_warning_when_ran_skill_mentioned),
        ("test_no_warning_for_shrink_doc_skill", test_instance.test_no_warning_for_shrink_doc_skill),
        ("test_extracts_agent_id_json_format", test_instance.test_extracts_agent_id_json_format),
        ("test_extracts_agent_id_text_format", test_instance.test_extracts_agent_id_text_format),
        ("test_returns_none_when_no_agent_id", test_instance.test_returns_none_when_no_agent_id),
        ("test_check_subagent_session_returns_false_when_file_missing", test_instance.test_check_subagent_session_returns_false_when_file_missing),
        ("test_check_subagent_session_detects_skill_invocation", test_instance.test_check_subagent_session_detects_skill_invocation),
        ("test_check_subagent_session_no_skill_invocation", test_instance.test_check_subagent_session_no_skill_invocation),
    ]

    passed = 0
    failed = 0

    for name, test_func in tests:
        test_instance.setup_method()
        try:
            test_func()
            print(f"✓ {name}")
            passed += 1
        except Exception as e:
            print(f"✗ {name}: {e}")
            traceback.print_exc()
            failed += 1

    print(f"\n{passed} passed, {failed} failed")
    return failed == 0


if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)

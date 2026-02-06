"""Tests for AutoLearnHandler."""

from pathlib import Path
import sys

# Add plugin path
sys.path.insert(0, str(Path(__file__).parent.parent / "plugin" / "hooks"))

from posttool_handlers.auto_learn import AutoLearnHandler


class TestAutoLearnHandler:
    """Test cases for auto-learn mistake detection."""

    def setup_method(self):
        """Set up test fixtures."""
        self.handler = AutoLearnHandler()

    def test_pattern_12c_false_positive_exit_code_zero(self):
        """Pattern 12c should NOT trigger when exit_code is 0 (intentional fallback with ||)."""
        # Simulates: cat /nonexistent/path || echo "fallback"
        # The "No such file or directory" is part of intentional error handling
        tool_result = {
            "stdout": "cat: /workspace/nonexistent/file.txt: No such file or directory\nfallback value",
            "stderr": "",
            "exit_code": 0
        }

        result = self.handler.check("Bash", tool_result, {"session_id": "test"})
        assert result is None, "Should not trigger wrong_working_directory when exit_code is 0"

    def test_pattern_12c_true_positive_exit_code_nonzero(self):
        """Pattern 12c SHOULD trigger when exit_code is non-zero (actual path error)."""
        # Simulates: cat /nonexistent/path (without fallback)
        tool_result = {
            "stdout": "",
            "stderr": "cat: /workspace/nonexistent/file.txt: No such file or directory",
            "exit_code": 1
        }

        result = self.handler.check("Bash", tool_result, {"session_id": "test"})
        assert result is not None, "Should trigger wrong_working_directory when exit_code is non-zero"
        assert "MISTAKE DETECTED: wrong_working_directory" in result["additionalContext"]

    def test_pattern_12c_cannot_access_exit_code_zero(self):
        """Pattern 12c should NOT trigger for 'cannot access' when exit_code is 0."""
        tool_result = {
            "stdout": "ls: cannot access '/workspace/missing': No such file or directory\nfound other files",
            "stderr": "",
            "exit_code": 0
        }

        result = self.handler.check("Bash", tool_result, {"session_id": "test"})
        assert result is None, "Should not trigger wrong_working_directory when exit_code is 0"

    def test_pattern_12c_cannot_access_exit_code_nonzero(self):
        """Pattern 12c SHOULD trigger for 'cannot access' when exit_code is non-zero."""
        tool_result = {
            "stdout": "",
            "stderr": "ls: cannot access '/workspace/missing': No such file or directory",
            "exit_code": 2
        }

        result = self.handler.check("Bash", tool_result, {"session_id": "test"})
        assert result is not None, "Should trigger wrong_working_directory when exit_code is non-zero"
        assert "MISTAKE DETECTED: wrong_working_directory" in result["additionalContext"]

    def test_pattern_12c_non_bash_tool_ignored(self):
        """Pattern 12c should only apply to Bash tool."""
        tool_result = {
            "content": "No such file or directory /workspace/test",
            "exit_code": 1
        }

        result = self.handler.check("Read", tool_result, {"session_id": "test"})
        assert result is None, "Should not trigger for non-Bash tools"


def run_tests():
    """Run all tests."""
    import traceback
    test_instance = TestAutoLearnHandler()

    tests = [
        ("test_pattern_12c_false_positive_exit_code_zero", test_instance.test_pattern_12c_false_positive_exit_code_zero),
        ("test_pattern_12c_true_positive_exit_code_nonzero", test_instance.test_pattern_12c_true_positive_exit_code_nonzero),
        ("test_pattern_12c_cannot_access_exit_code_zero", test_instance.test_pattern_12c_cannot_access_exit_code_zero),
        ("test_pattern_12c_cannot_access_exit_code_nonzero", test_instance.test_pattern_12c_cannot_access_exit_code_nonzero),
        ("test_pattern_12c_non_bash_tool_ignored", test_instance.test_pattern_12c_non_bash_tool_ignored),
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

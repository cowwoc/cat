"""Tests for validate_state_status PostToolUse handler."""

import sys
import tempfile
import traceback
from pathlib import Path

# Add plugin path
sys.path.insert(0, str(Path(__file__).parent.parent / "plugin" / "hooks"))

from posttool_handlers.validate_state_status import handle


class TestValidateStateStatus:
    """Test cases for STATE.md status validation handler."""

    def test_non_edit_write_tool_returns_none(self):
        """Handler should return None for non-Edit/Write tools."""
        result = handle("Bash", {"file_path": "/tmp/STATE.md"}, {})
        assert result is None

        result = handle("Read", {"file_path": "/tmp/STATE.md"}, {})
        assert result is None

    def test_edit_non_state_file_returns_none(self):
        """Handler should return None for Edit on non-STATE.md files."""
        result = handle("Edit", {"file_path": "/workspace/README.md"}, {})
        assert result is None

        result = handle("Edit", {"file_path": "/workspace/PLAN.md"}, {})
        assert result is None

    def test_edit_state_md_with_open_returns_none(self):
        """Handler should return None for canonical status 'open'."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            state_file = Path(tmp_dir) / "STATE.md"
            state_file.write_text("# State\n\n- **Status:** open\n- **Progress:** 0%\n")

            result = handle("Edit", {"file_path": str(state_file)}, {})
            assert result is None

    def test_edit_state_md_with_closed_returns_none(self):
        """Handler should return None for canonical status 'closed'."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            state_file = Path(tmp_dir) / "STATE.md"
            state_file.write_text("# State\n\n- **Status:** closed\n- **Progress:** 100%\n")

            result = handle("Edit", {"file_path": str(state_file)}, {})
            assert result is None

    def test_edit_state_md_with_complete_returns_warning(self):
        """Handler should return warning for non-canonical status 'complete'."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            state_file = Path(tmp_dir) / "STATE.md"
            state_file.write_text("# State\n\n- **Status:** complete\n- **Progress:** 100%\n")

            result = handle("Edit", {"file_path": str(state_file)}, {})
            assert result is not None
            assert "M434" in result
            assert '"complete"' in result
            assert "closed" in result

    def test_edit_state_md_with_pending_returns_warning(self):
        """Handler should return warning for non-canonical status 'pending' (renamed to 'open')."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            state_file = Path(tmp_dir) / "STATE.md"
            state_file.write_text("# State\n\n- **Status:** pending\n- **Progress:** 0%\n")

            result = handle("Edit", {"file_path": str(state_file)}, {})
            assert result is not None
            assert "M434" in result
            assert '"pending"' in result
            assert "open" in result

    def test_edit_state_md_with_completed_returns_warning(self):
        """Handler should return warning for non-canonical status 'completed' (renamed to 'closed')."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            state_file = Path(tmp_dir) / "STATE.md"
            state_file.write_text("# State\n\n- **Status:** completed\n- **Progress:** 100%\n")

            result = handle("Edit", {"file_path": str(state_file)}, {})
            assert result is not None
            assert "M434" in result
            assert '"completed"' in result
            assert "closed" in result

    def test_edit_state_md_with_in_progress_returns_none(self):
        """Handler should return None for canonical status 'in-progress'."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            state_file = Path(tmp_dir) / "STATE.md"
            state_file.write_text("# State\n\n- **Status:** in-progress\n- **Progress:** 50%\n")

            result = handle("Edit", {"file_path": str(state_file)}, {})
            assert result is None

    def test_write_state_md_with_done_returns_warning(self):
        """Handler should return warning for non-canonical status 'done' via Write."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            state_file = Path(tmp_dir) / "STATE.md"
            state_file.write_text("# State\n\n- **Status:** done\n- **Progress:** 100%\n")

            result = handle("Write", {"file_path": str(state_file)}, {})
            assert result is not None
            assert "M434" in result
            assert '"done"' in result
            assert "closed" in result


def run_tests():
    """Run all tests."""
    test_instance = TestValidateStateStatus()

    tests = [
        ("test_non_edit_write_tool_returns_none", test_instance.test_non_edit_write_tool_returns_none),
        ("test_edit_non_state_file_returns_none", test_instance.test_edit_non_state_file_returns_none),
        ("test_edit_state_md_with_open_returns_none", test_instance.test_edit_state_md_with_open_returns_none),
        ("test_edit_state_md_with_closed_returns_none", test_instance.test_edit_state_md_with_closed_returns_none),
        ("test_edit_state_md_with_complete_returns_warning", test_instance.test_edit_state_md_with_complete_returns_warning),
        ("test_edit_state_md_with_pending_returns_warning", test_instance.test_edit_state_md_with_pending_returns_warning),
        ("test_edit_state_md_with_completed_returns_warning", test_instance.test_edit_state_md_with_completed_returns_warning),
        ("test_edit_state_md_with_in_progress_returns_none", test_instance.test_edit_state_md_with_in_progress_returns_none),
        ("test_write_state_md_with_done_returns_warning", test_instance.test_write_state_md_with_done_returns_warning),
    ]

    passed = 0
    failed = 0

    for name, test_func in tests:
        try:
            test_func()
            print(f"\u2713 {name}")
            passed += 1
        except Exception as e:
            print(f"\u2717 {name}: {e}")
            traceback.print_exc()
            failed += 1

    print(f"\n{passed} passed, {failed} failed")
    return failed == 0


if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)

"""Tests for validate_commit_type handler."""

import pytest
import sys
from pathlib import Path

# Add parent to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from bash_handlers.validate_commit_type import ValidateCommitTypeHandler


@pytest.fixture
def handler():
    return ValidateCommitTypeHandler()


class TestHeredocFormat:
    """Test HEREDOC commit message format."""

    def test_valid_feature_type(self, handler):
        command = '''git commit -m "$(cat <<'EOF'
feature: add new functionality
EOF
)"'''
        result = handler.check(command, {})
        assert result is None, "Valid 'feature' type should be allowed"

    def test_valid_bugfix_type(self, handler):
        command = '''git commit -m "$(cat <<'EOF'
bugfix(hooks): fix validation logic
EOF
)"'''
        result = handler.check(command, {})
        assert result is None, "Valid 'bugfix' type should be allowed"

    def test_valid_planning_type(self, handler):
        command = '''git commit -m "$(cat <<'EOF'
planning: add task for v2.0
EOF
)"'''
        result = handler.check(command, {})
        assert result is None, "Valid 'planning' type should be allowed"

    def test_invalid_feat_type_blocked(self, handler):
        command = '''git commit -m "$(cat <<'EOF'
feat: add new feature
EOF
)"'''
        result = handler.check(command, {})
        assert result is not None, "Invalid 'feat' type should be blocked"
        assert result["decision"] == "block"
        assert "feat" in result["reason"]

    def test_invalid_fix_type_blocked(self, handler):
        command = '''git commit -m "$(cat <<'EOF'
fix: resolve bug
EOF
)"'''
        result = handler.check(command, {})
        assert result is not None, "Invalid 'fix' type should be blocked"
        assert result["decision"] == "block"
        assert "fix" in result["reason"]

    def test_invalid_chore_type_blocked(self, handler):
        command = '''git commit -m "$(cat <<'EOF'
chore: update dependencies
EOF
)"'''
        result = handler.check(command, {})
        assert result is not None, "Invalid 'chore' type should be blocked"
        assert result["decision"] == "block"

    def test_multiline_message(self, handler):
        command = '''git commit -m "$(cat <<'EOF'
bugfix(hooks): parse HEREDOC commit messages

This is a longer commit message with multiple lines
explaining the change in detail.
EOF
)"'''
        result = handler.check(command, {})
        assert result is None, "Valid type with multiline message should be allowed"


class TestSimpleFormat:
    """Test simple -m "message" format."""

    def test_valid_type_double_quotes(self, handler):
        command = 'git commit -m "feature: add login"'
        result = handler.check(command, {})
        assert result is None, "Valid type with double quotes should be allowed"

    def test_valid_type_single_quotes(self, handler):
        command = "git commit -m 'bugfix: fix crash'"
        result = handler.check(command, {})
        assert result is None, "Valid type with single quotes should be allowed"

    def test_invalid_type_simple_format(self, handler):
        command = 'git commit -m "feat: add feature"'
        result = handler.check(command, {})
        assert result is not None, "Invalid 'feat' in simple format should be blocked"
        assert result["decision"] == "block"


class TestUnparseableFormats:
    """Test handling of unparseable commit message formats."""

    def test_unparseable_m_flag_blocked(self, handler):
        # -m flag present but format not recognized
        command = 'git commit -m $(echo "test")'
        result = handler.check(command, {})
        assert result is not None, "Unparseable -m format should be blocked"
        assert result["decision"] == "block"
        assert "Could not parse" in result["reason"]

    def test_no_m_flag_allowed(self, handler):
        # Interactive mode - no -m flag
        command = 'git commit'
        result = handler.check(command, {})
        assert result is None, "Commit without -m flag should be allowed (interactive)"

    def test_commit_amend_no_message(self, handler):
        command = 'git commit --amend'
        result = handler.check(command, {})
        assert result is None, "Amend without -m should be allowed"


class TestNonCommitCommands:
    """Test that non-commit commands pass through."""

    def test_git_status_allowed(self, handler):
        command = 'git status'
        result = handler.check(command, {})
        assert result is None, "Non-commit commands should pass through"

    def test_git_push_allowed(self, handler):
        command = 'git push origin main'
        result = handler.check(command, {})
        assert result is None, "Non-commit commands should pass through"

    def test_non_git_command_allowed(self, handler):
        command = 'ls -la'
        result = handler.check(command, {})
        assert result is None, "Non-git commands should pass through"


class TestAllValidTypes:
    """Test all valid commit types are accepted."""

    @pytest.mark.parametrize("commit_type", [
        "feature", "bugfix", "docs", "style", "refactor",
        "performance", "test", "config", "planning", "revert"
    ])
    def test_valid_type_allowed(self, handler, commit_type):
        command = f'''git commit -m "$(cat <<'EOF'
{commit_type}: test commit
EOF
)"'''
        result = handler.check(command, {})
        assert result is None, f"Valid type '{commit_type}' should be allowed"


class TestAllInvalidTypes:
    """Test all invalid commit types are blocked."""

    @pytest.mark.parametrize("commit_type", [
        "feat", "fix", "chore", "build", "ci", "perf"
    ])
    def test_invalid_type_blocked(self, handler, commit_type):
        command = f'''git commit -m "$(cat <<'EOF'
{commit_type}: test commit
EOF
)"'''
        result = handler.check(command, {})
        assert result is not None, f"Invalid type '{commit_type}' should be blocked"
        assert result["decision"] == "block"

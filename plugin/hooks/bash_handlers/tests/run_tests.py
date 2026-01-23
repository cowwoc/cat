#!/usr/bin/env python3
"""Standalone test runner for validate_commit_type (no pytest required)."""

import sys
from pathlib import Path

# Add parent to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from bash_handlers.validate_commit_type import ValidateCommitTypeHandler

handler = ValidateCommitTypeHandler()
passed = 0
failed = 0


def test(name, condition, message=""):
    global passed, failed
    if condition:
        print(f"✓ {name}")
        passed += 1
    else:
        print(f"✗ {name}: {message}")
        failed += 1


# Test HEREDOC format with valid types
cmd = '''git commit -m "$(cat <<'EOF'
feature: add new functionality
EOF
)"'''
result = handler.check(cmd, {})
test("HEREDOC: valid 'feature' type", result is None)

cmd = '''git commit -m "$(cat <<'EOF'
bugfix(hooks): fix validation
EOF
)"'''
result = handler.check(cmd, {})
test("HEREDOC: valid 'bugfix' with scope", result is None)

# Test HEREDOC with invalid types
cmd = '''git commit -m "$(cat <<'EOF'
feat: add feature
EOF
)"'''
result = handler.check(cmd, {})
test("HEREDOC: invalid 'feat' blocked",
     result is not None and result.get("decision") == "block")

cmd = '''git commit -m "$(cat <<'EOF'
fix: resolve bug
EOF
)"'''
result = handler.check(cmd, {})
test("HEREDOC: invalid 'fix' blocked",
     result is not None and result.get("decision") == "block")

# Test simple format
cmd = 'git commit -m "feature: add login"'
result = handler.check(cmd, {})
test("Simple: valid 'feature' type", result is None)

cmd = 'git commit -m "feat: add feature"'
result = handler.check(cmd, {})
test("Simple: invalid 'feat' blocked",
     result is not None and result.get("decision") == "block")

# Test unparseable formats
cmd = 'git commit -m $(echo "test")'
result = handler.check(cmd, {})
test("Unparseable -m format blocked",
     result is not None and result.get("decision") == "block")

# Test non-commit commands pass through
cmd = 'git status'
result = handler.check(cmd, {})
test("Non-commit command allowed", result is None)

cmd = 'git commit'
result = handler.check(cmd, {})
test("Interactive commit allowed", result is None)

# Test all valid types
for t in ["feature", "bugfix", "docs", "style", "refactor",
          "performance", "test", "config", "planning", "revert"]:
    cmd = f'''git commit -m "$(cat <<'EOF'
{t}: test
EOF
)"'''
    result = handler.check(cmd, {})
    test(f"Valid type '{t}' allowed", result is None)

# Test all invalid types
for t in ["feat", "fix", "chore", "build", "ci", "perf"]:
    cmd = f'''git commit -m "$(cat <<'EOF'
{t}: test
EOF
)"'''
    result = handler.check(cmd, {})
    test(f"Invalid type '{t}' blocked",
         result is not None and result.get("decision") == "block")

print(f"\n{passed} passed, {failed} failed")
sys.exit(0 if failed == 0 else 1)

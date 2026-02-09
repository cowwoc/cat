#!/usr/bin/env python3
"""
Unit tests for parse_jsonl_stream function.
"""

import unittest
from run_evals import parse_jsonl_stream


class TestParseJsonlStream(unittest.TestCase):
    """Test cases for parse_jsonl_stream function."""

    def test_valid_jsonl_with_skill_tool_use(self):
        """Test parsing valid JSONL with Skill tool_use containing skill name."""
        jsonl_output = """
{"type": "assistant", "message": {"content": [{"type": "tool_use", "name": "Skill", "input": {"skill": "status"}}]}}
"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertEqual(result.skill_name, "status")
        self.assertEqual(result.parse_errors, 0)
        self.assertEqual(result.lines_parsed, 1)
        self.assertEqual(result.tool_use_count, 1)

    def test_jsonl_without_skill_tool_use(self):
        """Test parsing JSONL without Skill tool_use (should return None)."""
        jsonl_output = """
{"type": "assistant", "message": {"content": [{"type": "text", "text": "Hello"}]}}
"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertIsNone(result.skill_name)
        self.assertEqual(result.parse_errors, 0)
        self.assertGreater(result.lines_parsed, 0)

    def test_skill_tool_use_with_cat_prefix(self):
        """Test Skill tool_use with 'cat:' prefix (should strip prefix)."""
        jsonl_output = """
{"type": "assistant", "message": {"content": [{"type": "tool_use", "name": "Skill", "input": {"skill": "cat:status"}}]}}
"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertEqual(result.skill_name, "status")
        self.assertEqual(result.parse_errors, 0)

    def test_malformed_json_lines(self):
        """Test malformed JSON lines (should count parse_errors)."""
        jsonl_output = """
{"type": "assistant"
not json at all
"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertIsNone(result.skill_name)
        self.assertEqual(result.parse_errors, 2)

    def test_empty_output(self):
        """Test empty output (should return None with no errors)."""
        jsonl_output = ""
        result = parse_jsonl_stream(jsonl_output)
        self.assertIsNone(result.skill_name)
        self.assertEqual(result.parse_errors, 0)
        self.assertEqual(result.lines_parsed, 0)

    def test_jsonl_with_multiple_events_skill_in_later_event(self):
        """Test JSONL with multiple events, Skill in later event."""
        jsonl_output = """
{"type": "system", "message": {"content": "System message"}}
{"type": "user", "message": {"content": "User input"}}
{"type": "assistant", "message": {"content": [{"type": "tool_use", "name": "Skill", "input": {"skill": "work"}}]}}
"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertEqual(result.skill_name, "work")
        self.assertEqual(result.parse_errors, 0)
        self.assertEqual(result.lines_parsed, 3)

    def test_skill_with_empty_string(self):
        """Test Skill tool_use with empty skill name (should return None)."""
        jsonl_output = """
{"type": "assistant", "message": {"content": [{"type": "tool_use", "name": "Skill", "input": {"skill": ""}}]}}
"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertIsNone(result.skill_name)
        self.assertEqual(result.parse_errors, 0)

    def test_skill_with_missing_input(self):
        """Test Skill tool_use with missing input field."""
        jsonl_output = """
{"type": "assistant", "message": {"content": [{"type": "tool_use", "name": "Skill"}]}}
"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertIsNone(result.skill_name)
        self.assertEqual(result.parse_errors, 0)

    def test_multiple_tool_uses_returns_first_skill(self):
        """Test that first Skill tool_use is returned when multiple exist."""
        jsonl_output = """
{"type": "assistant", "message": {"content": [{"type": "tool_use", "name": "Skill", "input": {"skill": "status"}}, {"type": "tool_use", "name": "Skill", "input": {"skill": "work"}}]}}
"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertEqual(result.skill_name, "status")
        self.assertEqual(result.parse_errors, 0)

    def test_non_skill_tool_use(self):
        """Test tool_use events that are not Skill (should return None)."""
        jsonl_output = """
{"type": "assistant", "message": {"content": [{"type": "tool_use", "name": "Bash", "input": {"command": "ls"}}]}}
"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertIsNone(result.skill_name)
        self.assertEqual(result.parse_errors, 0)
        self.assertEqual(result.tool_use_count, 1)

    def test_mixed_valid_and_invalid_lines(self):
        """Test mix of valid JSON and malformed lines (stops after finding skill)."""
        jsonl_output = """
{"type": "system", "message": "System"}
invalid line
{"type": "assistant", "message": {"content": [{"type": "tool_use", "name": "Skill", "input": {"skill": "help"}}]}}
another bad line
"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertEqual(result.skill_name, "help")
        # Only 1 error because function returns after finding skill
        self.assertEqual(result.parse_errors, 1)

    def test_whitespace_only_lines_ignored(self):
        """Test that whitespace-only lines are properly ignored."""
        jsonl_output = """


{"type": "assistant", "message": {"content": [{"type": "tool_use", "name": "Skill", "input": {"skill": "add"}}]}}

"""
        result = parse_jsonl_stream(jsonl_output)
        self.assertEqual(result.skill_name, "add")
        self.assertEqual(result.parse_errors, 0)


if __name__ == '__main__':
    unittest.main()

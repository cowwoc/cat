#!/usr/bin/env python3
"""Tests for analyze-session.py script."""

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

# Load analyze-session.py module (has dash in name, can't import normally)
script_path = Path(__file__).parent.parent / 'plugin' / 'scripts' / 'analyze-session.py'
spec = importlib.util.spec_from_file_location("analyze_session", script_path)
analyze_session_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analyze_session_module)

# Import functions from the module
parse_jsonl = analyze_session_module.parse_jsonl
extract_tool_uses = analyze_session_module.extract_tool_uses
calculate_tool_frequency = analyze_session_module.calculate_tool_frequency
calculate_token_usage = analyze_session_module.calculate_token_usage
extract_output_sizes = analyze_session_module.extract_output_sizes
find_cache_candidates = analyze_session_module.find_cache_candidates
find_batch_candidates = analyze_session_module.find_batch_candidates
find_parallel_candidates = analyze_session_module.find_parallel_candidates
analyze_session = analyze_session_module.analyze_session
MIN_BATCH_SIZE = analyze_session_module.MIN_BATCH_SIZE


class TestAnalyzeSession(unittest.TestCase):
    """Tests for analyze-session.py script."""

    def setUp(self):
        """Create temporary directory for tests."""
        self.tmp_dir = tempfile.mkdtemp()
        self.tmp_path = Path(self.tmp_dir)

    def tearDown(self):
        """Clean up temporary files."""
        import shutil
        shutil.rmtree(self.tmp_dir, ignore_errors=True)

    def create_jsonl_file(self, lines):
        """Helper to create a JSONL file from list of dicts."""
        file_path = self.tmp_path / "test_session.jsonl"
        with open(file_path, 'w') as f:
            for line in lines:
                f.write(json.dumps(line) + '\n')
        return str(file_path)

    def test_parse_jsonl_empty_file(self):
        """Test parsing an empty JSONL file."""
        file_path = self.tmp_path / "empty.jsonl"
        file_path.write_text("")

        result = parse_jsonl(str(file_path))
        self.assertEqual(result, [])

    def test_parse_jsonl_malformed_lines_skipped(self):
        """Test that malformed JSON lines are skipped gracefully."""
        file_path = self.tmp_path / "malformed.jsonl"
        file_path.write_text(
            '{"valid": "json"}\n'
            'this is not valid json\n'
            '{"another": "valid"}\n'
        )

        result = parse_jsonl(str(file_path))
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0], {"valid": "json"})
        self.assertEqual(result[1], {"another": "valid"})

    def test_parse_jsonl_empty_lines_skipped(self):
        """Test that empty lines are skipped."""
        file_path = self.tmp_path / "with_empty.jsonl"
        file_path.write_text(
            '{"first": 1}\n'
            '\n'
            '{"second": 2}\n'
            '   \n'
            '{"third": 3}\n'
        )

        result = parse_jsonl(str(file_path))
        self.assertEqual(len(result), 3)

    def test_extract_tool_uses_basic(self):
        """Test basic tool use extraction."""
        entries = [
            {
                "type": "assistant",
                "message": {
                    "id": "msg1",
                    "content": [
                        {
                            "type": "tool_use",
                            "id": "tool1",
                            "name": "Read",
                            "input": {"file_path": "/test.txt"}
                        }
                    ]
                }
            }
        ]

        result = extract_tool_uses(entries)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]['name'], "Read")
        self.assertEqual(result[0]['id'], "tool1")
        self.assertEqual(result[0]['message_id'], "msg1")
        self.assertEqual(result[0]['input'], {"file_path": "/test.txt"})

    def test_extract_tool_uses_filters_none_names(self):
        """Test that tool_use entries without names are filtered out."""
        entries = [
            {
                "type": "assistant",
                "message": {
                    "id": "msg1",
                    "content": [
                        {
                            "type": "tool_use",
                            "id": "tool1",
                            "name": "Read",
                            "input": {}
                        },
                        {
                            "type": "tool_use",
                            "id": "tool2",
                            # Missing 'name' field
                            "input": {}
                        },
                        {
                            "type": "tool_use",
                            "id": "tool3",
                            "name": None,  # Explicit None
                            "input": {}
                        },
                        {
                            "type": "tool_use",
                            "id": "tool4",
                            "name": "Grep",
                            "input": {}
                        }
                    ]
                }
            }
        ]

        result = extract_tool_uses(entries)
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0]['name'], "Read")
        self.assertEqual(result[1]['name'], "Grep")

    def test_extract_tool_uses_non_list_content(self):
        """Test that non-list content is handled gracefully."""
        entries = [
            {
                "type": "assistant",
                "message": {
                    "id": "msg1",
                    "content": "This is a string, not a list"
                }
            }
        ]

        result = extract_tool_uses(entries)
        self.assertEqual(result, [])

    def test_calculate_tool_frequency(self):
        """Test tool frequency calculation."""
        tool_uses = [
            {"name": "Read", "input": {}},
            {"name": "Read", "input": {}},
            {"name": "Grep", "input": {}},
            {"name": "Edit", "input": {}},
            {"name": "Read", "input": {}},
        ]

        result = calculate_tool_frequency(tool_uses)
        self.assertEqual(len(result), 3)
        self.assertEqual(result[0], {"tool": "Read", "count": 3})
        self.assertEqual(result[1], {"tool": "Grep", "count": 1})
        self.assertEqual(result[2], {"tool": "Edit", "count": 1})

    def test_calculate_tool_frequency_empty(self):
        """Test frequency calculation with empty input."""
        result = calculate_tool_frequency([])
        self.assertEqual(result, [])

    def test_calculate_token_usage(self):
        """Test token usage calculation per tool type."""
        entries = [
            {
                "type": "assistant",
                "message": {
                    "id": "msg1",
                    "content": [],
                    "usage": {
                        "input_tokens": 100,
                        "output_tokens": 50
                    }
                }
            },
            {
                "type": "assistant",
                "message": {
                    "id": "msg2",
                    "content": [],
                    "usage": {
                        "input_tokens": 200,
                        "output_tokens": 75
                    }
                }
            }
        ]

        tool_uses = [
            {"name": "Read", "message_id": "msg1"},
            {"name": "Read", "message_id": "msg2"},
        ]

        result = calculate_token_usage(entries, tool_uses)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]['tool'], "Read")
        self.assertEqual(result[0]['total_input_tokens'], 300)
        self.assertEqual(result[0]['total_output_tokens'], 125)
        self.assertEqual(result[0]['count'], 2)

    def test_calculate_token_usage_conversation(self):
        """Test token usage for messages without tools (conversation)."""
        entries = [
            {
                "type": "assistant",
                "message": {
                    "id": "msg1",
                    "content": [],
                    "usage": {
                        "input_tokens": 50,
                        "output_tokens": 25
                    }
                }
            }
        ]

        tool_uses = []  # No tools used

        result = calculate_token_usage(entries, tool_uses)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]['tool'], "conversation")
        self.assertEqual(result[0]['total_input_tokens'], 50)
        self.assertEqual(result[0]['total_output_tokens'], 25)

    def test_extract_output_sizes(self):
        """Test extracting output sizes from tool results."""
        entries = [
            {
                "type": "tool_result",
                "tool_use_id": "tool1",
                "content": "Short output"
            },
            {
                "type": "tool_result",
                "tool_use_id": "tool2",
                "content": "This is a much longer output string for testing"
            },
            {
                "type": "tool_result",
                "tool_use_id": "tool3",
                "content": [
                    {"text": "Part 1"},
                    {"text": "Part 2"}
                ]
            }
        ]

        result = extract_output_sizes(entries)
        self.assertEqual(len(result), 3)
        # Should be sorted by length descending
        self.assertEqual(result[0]['tool_use_id'], "tool2")
        self.assertEqual(result[0]['output_length'], 47)
        self.assertEqual(result[1]['tool_use_id'], "tool3")
        self.assertEqual(result[1]['output_length'], 13)  # "Part 1\nPart 2"
        self.assertEqual(result[2]['tool_use_id'], "tool1")
        self.assertEqual(result[2]['output_length'], 12)

    def test_extract_output_sizes_empty(self):
        """Test output size extraction with no tool results."""
        result = extract_output_sizes([])
        self.assertEqual(result, [])

    def test_find_cache_candidates(self):
        """Test identifying repeated identical operations."""
        tool_uses = [
            {"name": "Read", "input": {"file_path": "/test.txt"}},
            {"name": "Read", "input": {"file_path": "/test.txt"}},
            {"name": "Read", "input": {"file_path": "/test.txt"}},
            {"name": "Grep", "input": {"pattern": "foo"}},
            {"name": "Read", "input": {"file_path": "/other.txt"}},
        ]

        result = find_cache_candidates(tool_uses)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]['operation']['name'], "Read")
        self.assertEqual(result[0]['operation']['input'], {"file_path": "/test.txt"})
        self.assertEqual(result[0]['repeat_count'], 3)
        self.assertEqual(result[0]['optimization'], "CACHE_CANDIDATE")

    def test_find_cache_candidates_no_repeats(self):
        """Test cache candidates when no operations repeat."""
        tool_uses = [
            {"name": "Read", "input": {"file_path": "/test1.txt"}},
            {"name": "Read", "input": {"file_path": "/test2.txt"}},
            {"name": "Grep", "input": {"pattern": "foo"}},
        ]

        result = find_cache_candidates(tool_uses)
        self.assertEqual(result, [])

    def test_find_batch_candidates(self):
        """Test identifying consecutive same-tool operations."""
        tool_uses = [
            {"name": "Read", "input": {}},
            {"name": "Read", "input": {}},
            {"name": "Read", "input": {}},
            {"name": "Grep", "input": {}},
            {"name": "Edit", "input": {}},
            {"name": "Edit", "input": {}},
            {"name": "Edit", "input": {}},
            {"name": "Edit", "input": {}},
        ]

        result = find_batch_candidates(tool_uses)
        self.assertEqual(len(result), 2)
        # Should be sorted by count descending
        self.assertEqual(result[0]['tool'], "Edit")
        self.assertEqual(result[0]['consecutive_count'], 4)
        self.assertEqual(result[0]['optimization'], "BATCH_CANDIDATE")
        self.assertEqual(result[1]['tool'], "Read")
        self.assertEqual(result[1]['consecutive_count'], 3)

    def test_find_batch_candidates_respects_min_size(self):
        """Test that batches smaller than MIN_BATCH_SIZE are ignored."""
        tool_uses = [
            {"name": "Read", "input": {}},
            {"name": "Read", "input": {}},  # Only 2 consecutive
            {"name": "Grep", "input": {}},
        ]

        result = find_batch_candidates(tool_uses)
        self.assertEqual(result, [])

    def test_find_batch_candidates_empty(self):
        """Test batch candidates with empty input."""
        result = find_batch_candidates([])
        self.assertEqual(result, [])

    def test_find_parallel_candidates(self):
        """Test identifying multiple tools in same message."""
        tool_uses = [
            {"name": "Read", "message_id": "msg1"},
            {"name": "Grep", "message_id": "msg1"},
            {"name": "Edit", "message_id": "msg1"},
            {"name": "Read", "message_id": "msg2"},
            {"name": "Grep", "message_id": "msg2"},
        ]

        result = find_parallel_candidates(tool_uses)
        self.assertEqual(len(result), 2)
        # Should be sorted by count descending
        self.assertEqual(result[0]['message_id'], "msg1")
        self.assertEqual(result[0]['count'], 3)
        self.assertEqual(set(result[0]['parallel_tools']), {"Read", "Grep", "Edit"})
        self.assertEqual(result[0]['optimization'], "PARALLEL_CANDIDATE")
        self.assertEqual(result[1]['message_id'], "msg2")
        self.assertEqual(result[1]['count'], 2)

    def test_find_parallel_candidates_no_message_id(self):
        """Test parallel candidates when tools have no message_id."""
        tool_uses = [
            {"name": "Read"},
            {"name": "Grep"},
        ]

        result = find_parallel_candidates(tool_uses)
        self.assertEqual(result, [])

    def test_find_parallel_candidates_single_tool_per_message(self):
        """Test when each message has only one tool."""
        tool_uses = [
            {"name": "Read", "message_id": "msg1"},
            {"name": "Grep", "message_id": "msg2"},
            {"name": "Edit", "message_id": "msg3"},
        ]

        result = find_parallel_candidates(tool_uses)
        self.assertEqual(result, [])

    def test_analyze_session_integration(self):
        """Integration test for full session analysis."""
        entries = [
            {
                "type": "assistant",
                "message": {
                    "id": "msg1",
                    "content": [
                        {
                            "type": "tool_use",
                            "id": "tool1",
                            "name": "Read",
                            "input": {"file_path": "/test.txt"}
                        },
                        {
                            "type": "tool_use",
                            "id": "tool2",
                            "name": "Grep",
                            "input": {"pattern": "foo"}
                        }
                    ],
                    "usage": {
                        "input_tokens": 100,
                        "output_tokens": 50
                    }
                }
            },
            {
                "type": "tool_result",
                "tool_use_id": "tool1",
                "content": "File contents here"
            },
            {
                "type": "tool_result",
                "tool_use_id": "tool2",
                "content": "Match found"
            }
        ]

        file_path = self.create_jsonl_file(entries)
        result = analyze_session(file_path)

        self.assertIn('tool_frequency', result)
        self.assertIn('token_usage', result)
        self.assertIn('output_sizes', result)
        self.assertIn('cache_candidates', result)
        self.assertIn('batch_candidates', result)
        self.assertIn('parallel_candidates', result)
        self.assertIn('summary', result)

        summary = result['summary']
        self.assertEqual(summary['total_tool_calls'], 2)
        self.assertEqual(set(summary['unique_tools']), {"Read", "Grep"})
        self.assertEqual(summary['total_entries'], 3)

    def test_analyze_session_empty_file(self):
        """Test analyzing an empty session file."""
        file_path = self.tmp_path / "empty.jsonl"
        file_path.write_text("")

        result = analyze_session(str(file_path))

        self.assertEqual(result['summary']['total_tool_calls'], 0)
        self.assertEqual(result['summary']['unique_tools'], [])
        self.assertEqual(result['summary']['total_entries'], 0)


if __name__ == '__main__':
    unittest.main()

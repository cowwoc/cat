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
discover_subagents = analyze_session_module.discover_subagents
analyze_single_agent = analyze_session_module.analyze_single_agent
merge_tool_frequency = analyze_session_module.merge_tool_frequency
merge_cache_candidates = analyze_session_module.merge_cache_candidates
sum_token_usage = analyze_session_module.sum_token_usage
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

        self.assertIn('main', result)
        self.assertIn('subagents', result)
        self.assertIn('combined', result)

        main = result['main']
        self.assertIn('tool_frequency', main)
        self.assertIn('token_usage', main)
        self.assertIn('output_sizes', main)
        self.assertIn('cache_candidates', main)
        self.assertIn('batch_candidates', main)
        self.assertIn('parallel_candidates', main)
        self.assertIn('summary', main)

        summary = main['summary']
        self.assertEqual(summary['total_tool_calls'], 2)
        self.assertEqual(set(summary['unique_tools']), {"Read", "Grep"})
        self.assertEqual(summary['total_entries'], 3)

    def test_analyze_session_empty_file(self):
        """Test analyzing an empty session file."""
        file_path = self.tmp_path / "empty.jsonl"
        file_path.write_text("")

        result = analyze_session(str(file_path))

        self.assertEqual(result['main']['summary']['total_tool_calls'], 0)
        self.assertEqual(result['main']['summary']['unique_tools'], [])
        self.assertEqual(result['main']['summary']['total_entries'], 0)

    def test_discover_subagents_no_subagents(self):
        """Test subagent discovery when no subagents exist."""
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

        file_path = self.create_jsonl_file(entries)
        result = discover_subagents(file_path)

        self.assertEqual(result, [])

    def test_discover_subagents_with_task_results(self):
        """Test subagent discovery from Task tool results."""
        entries = [
            {
                "type": "tool_result",
                "tool_use_id": "task1",
                "content": '{"agentId": "abc123", "status": "completed"}'
            },
            {
                "type": "tool_result",
                "tool_use_id": "task2",
                "content": '{"agentId": "def456", "status": "completed"}'
            }
        ]

        file_path = self.create_jsonl_file(entries)

        # Create subagent directory and files
        subagent_dir = self.tmp_path / "subagents"
        subagent_dir.mkdir()
        (subagent_dir / "agent-abc123.jsonl").write_text('{"type": "assistant"}\n')
        (subagent_dir / "agent-def456.jsonl").write_text('{"type": "assistant"}\n')

        result = discover_subagents(file_path)

        self.assertEqual(len(result), 2)
        self.assertIn(str(subagent_dir / "agent-abc123.jsonl"), result)
        self.assertIn(str(subagent_dir / "agent-def456.jsonl"), result)

    def test_discover_subagents_missing_files_skipped(self):
        """Test that missing subagent files are gracefully skipped."""
        entries = [
            {
                "type": "tool_result",
                "tool_use_id": "task1",
                "content": '{"agentId": "abc123", "status": "completed"}'
            },
            {
                "type": "tool_result",
                "tool_use_id": "task2",
                "content": '{"agentId": "missing", "status": "completed"}'
            }
        ]

        file_path = self.create_jsonl_file(entries)

        # Create subagent directory and only one file
        subagent_dir = self.tmp_path / "subagents"
        subagent_dir.mkdir()
        (subagent_dir / "agent-abc123.jsonl").write_text('{"type": "assistant"}\n')
        # Note: agent-missing.jsonl is NOT created

        result = discover_subagents(file_path)

        self.assertEqual(len(result), 1)
        self.assertIn(str(subagent_dir / "agent-abc123.jsonl"), result)

    def test_merge_tool_frequency(self):
        """Test merging tool frequency counts from multiple agents."""
        freq1 = [
            {'tool': 'Read', 'count': 3},
            {'tool': 'Grep', 'count': 2}
        ]
        freq2 = [
            {'tool': 'Read', 'count': 1},
            {'tool': 'Edit', 'count': 4}
        ]

        result = merge_tool_frequency([freq1, freq2])

        self.assertEqual(len(result), 3)
        self.assertEqual(result[0], {'tool': 'Read', 'count': 4})
        self.assertEqual(result[1], {'tool': 'Edit', 'count': 4})
        self.assertEqual(result[2], {'tool': 'Grep', 'count': 2})

    def test_merge_cache_candidates(self):
        """Test merging cache candidates from multiple agents."""
        cache1 = [
            {
                'operation': {'name': 'Read', 'input': {'file_path': '/test.txt'}},
                'repeat_count': 3,
                'optimization': 'CACHE_CANDIDATE'
            }
        ]
        cache2 = [
            {
                'operation': {'name': 'Read', 'input': {'file_path': '/test.txt'}},
                'repeat_count': 2,
                'optimization': 'CACHE_CANDIDATE'
            },
            {
                'operation': {'name': 'Grep', 'input': {'pattern': 'foo'}},
                'repeat_count': 2,
                'optimization': 'CACHE_CANDIDATE'
            }
        ]

        result = merge_cache_candidates([cache1, cache2])

        self.assertEqual(len(result), 2)
        # Should combine the Read operations
        self.assertEqual(result[0]['operation']['name'], 'Read')
        self.assertEqual(result[0]['repeat_count'], 5)
        self.assertEqual(result[1]['operation']['name'], 'Grep')
        self.assertEqual(result[1]['repeat_count'], 2)

    def test_sum_token_usage(self):
        """Test summing token usage across multiple agents."""
        usage1 = [
            {
                'tool': 'Read',
                'total_input_tokens': 100,
                'total_output_tokens': 50,
                'count': 2
            }
        ]
        usage2 = [
            {
                'tool': 'Read',
                'total_input_tokens': 150,
                'total_output_tokens': 75,
                'count': 3
            },
            {
                'tool': 'Grep',
                'total_input_tokens': 200,
                'total_output_tokens': 100,
                'count': 1
            }
        ]

        result = sum_token_usage([usage1, usage2])

        self.assertEqual(len(result), 2)
        # Read should be first (higher input tokens)
        self.assertEqual(result[0]['tool'], 'Read')
        self.assertEqual(result[0]['total_input_tokens'], 250)
        self.assertEqual(result[0]['total_output_tokens'], 125)
        self.assertEqual(result[0]['count'], 5)
        # Grep second
        self.assertEqual(result[1]['tool'], 'Grep')
        self.assertEqual(result[1]['total_input_tokens'], 200)
        self.assertEqual(result[1]['total_output_tokens'], 100)
        self.assertEqual(result[1]['count'], 1)

    def test_analyze_session_with_subagents(self):
        """Test full session analysis with subagent discovery and combined metrics."""
        # Create main session
        main_entries = [
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
                    ],
                    "usage": {
                        "input_tokens": 100,
                        "output_tokens": 50
                    }
                }
            },
            {
                "type": "tool_result",
                "tool_use_id": "task1",
                "content": '{"agentId": "sub1", "status": "completed"}'
            }
        ]

        main_file = self.create_jsonl_file(main_entries)

        # Create subagent directory and file
        subagent_dir = self.tmp_path / "subagents"
        subagent_dir.mkdir()

        subagent_entries = [
            {
                "type": "assistant",
                "message": {
                    "id": "submsg1",
                    "content": [
                        {
                            "type": "tool_use",
                            "id": "subtool1",
                            "name": "Grep",
                            "input": {"pattern": "foo"}
                        }
                    ],
                    "usage": {
                        "input_tokens": 50,
                        "output_tokens": 25
                    }
                }
            }
        ]

        subagent_file = subagent_dir / "agent-sub1.jsonl"
        with open(subagent_file, 'w') as f:
            for entry in subagent_entries:
                f.write(json.dumps(entry) + '\n')

        result = analyze_session(main_file)

        # Check main agent metrics
        self.assertIn('main', result)
        self.assertIn('tool_frequency', result['main'])
        self.assertIn('token_usage', result['main'])
        self.assertIn('summary', result['main'])
        self.assertEqual(result['main']['summary']['total_tool_calls'], 1)

        # Check subagents section
        self.assertIn('subagents', result)
        self.assertIn('sub1', result['subagents'])
        self.assertEqual(result['subagents']['sub1']['summary']['total_tool_calls'], 1)
        self.assertEqual(result['subagents']['sub1']['tool_frequency'][0]['tool'], 'Grep')

        # Check combined section
        self.assertIn('combined', result)
        self.assertEqual(result['combined']['summary']['total_tool_calls'], 2)
        self.assertEqual(result['combined']['summary']['agent_count'], 2)
        self.assertEqual(set(result['combined']['summary']['unique_tools']), {'Read', 'Grep'})

        # Check combined tool frequency
        combined_freq = {item['tool']: item['count'] for item in result['combined']['tool_frequency']}
        self.assertEqual(combined_freq['Read'], 1)
        self.assertEqual(combined_freq['Grep'], 1)

        # Check combined token usage
        combined_tokens = {item['tool']: item['total_input_tokens']
                          for item in result['combined']['token_usage']}
        self.assertEqual(combined_tokens['Read'], 100)
        self.assertEqual(combined_tokens['Grep'], 50)

    def test_analyze_session_no_subagents_clean_structure(self):
        """Test that sessions without subagents use clean structure without duplication."""
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
                    ],
                    "usage": {
                        "input_tokens": 100,
                        "output_tokens": 50
                    }
                }
            }
        ]

        file_path = self.create_jsonl_file(entries)
        result = analyze_session(file_path)

        # Check structure has main, subagents, and combined keys
        self.assertIn('main', result)
        self.assertIn('subagents', result)
        self.assertIn('combined', result)

        # Main should have all analysis fields
        self.assertIn('tool_frequency', result['main'])
        self.assertIn('token_usage', result['main'])
        self.assertIn('summary', result['main'])

        # Subagents should be empty
        self.assertEqual(result['subagents'], {})

        # Combined should have agent_count of 1
        self.assertEqual(result['combined']['summary']['agent_count'], 1)

        # Combined should match main when no subagents
        self.assertEqual(
            result['combined']['summary']['total_tool_calls'],
            result['main']['summary']['total_tool_calls']
        )


if __name__ == '__main__':
    unittest.main()

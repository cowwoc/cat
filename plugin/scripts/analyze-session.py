#!/usr/bin/env python3
"""
Analyze Claude Code session JSONL file for optimization opportunities.

Extracts tool usage patterns, identifies batching/caching/parallel candidates,
and provides metrics for optimization recommendations.
"""

import argparse
import json
import sys
from collections import defaultdict
from typing import Any, Dict, List

# Minimum number of consecutive operations to qualify as batch candidate
MIN_BATCH_SIZE = 2


def parse_jsonl(file_path: str) -> List[Dict[str, Any]]:
    """Parse JSONL file, returning list of parsed JSON objects."""
    entries = []
    with open(file_path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                entries.append(json.loads(line))
            except json.JSONDecodeError as e:
                print(f"Warning: Skipping malformed line {line_num}: {e}", file=sys.stderr)
                continue
    return entries


def extract_tool_uses(entries: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Extract all tool_use entries from assistant messages."""
    tool_uses = []
    for entry in entries:
        if entry.get('type') != 'assistant':
            continue
        message = entry.get('message', {})
        content = message.get('content', [])
        if not isinstance(content, list):
            continue
        for item in content:
            if isinstance(item, dict) and item.get('type') == 'tool_use':
                # Skip malformed tool_use entries without a name
                if not item.get('name'):
                    continue
                tool_uses.append({
                    'id': item.get('id'),
                    'name': item.get('name'),
                    'input': item.get('input', {}),
                    'message_id': message.get('id')
                })
    return tool_uses


def calculate_tool_frequency(tool_uses: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Calculate frequency of each tool type."""
    frequency = defaultdict(int)
    for tool in tool_uses:
        frequency[tool['name']] += 1

    return [
        {'tool': tool, 'count': count}
        for tool, count in sorted(frequency.items(), key=lambda x: -x[1])
    ]


def calculate_token_usage(entries: List[Dict[str, Any]], tool_uses: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Calculate token usage per tool type."""
    # Build map of message_id to tools used in that message
    message_tools = defaultdict(list)
    for tool in tool_uses:
        message_id = tool.get('message_id')
        if message_id:
            message_tools[message_id].append(tool['name'])

    # Aggregate token usage by first tool in message
    tool_token_usage = defaultdict(lambda: {
        'input_tokens': 0,
        'output_tokens': 0,
        'count': 0
    })

    for entry in entries:
        if entry.get('type') != 'assistant':
            continue
        message = entry.get('message', {})
        message_id = message.get('id')
        usage = message.get('usage', {})

        if not message_id or not usage:
            continue

        tools = message_tools.get(message_id, [])
        # Attribute to first tool, or "conversation" if no tools
        primary_tool = tools[0] if tools else "conversation"

        tool_token_usage[primary_tool]['input_tokens'] += usage.get('input_tokens', 0)
        tool_token_usage[primary_tool]['output_tokens'] += usage.get('output_tokens', 0)
        tool_token_usage[primary_tool]['count'] += 1

    return [
        {
            'tool': tool,
            'total_input_tokens': stats['input_tokens'],
            'total_output_tokens': stats['output_tokens'],
            'count': stats['count']
        }
        for tool, stats in sorted(
            tool_token_usage.items(),
            key=lambda x: -(x[1]['input_tokens'])
        )
    ]


def extract_output_sizes(entries: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Extract output sizes from tool_result entries."""
    sizes = []
    for entry in entries:
        if entry.get('type') != 'tool_result':
            continue
        content = entry.get('content', '')
        if isinstance(content, list):
            # Join all text items
            content = '\n'.join(
                item.get('text', '') if isinstance(item, dict) else str(item)
                for item in content
            )
        elif not isinstance(content, str):
            content = str(content)

        sizes.append({
            'tool_use_id': entry.get('tool_use_id'),
            'output_length': len(content)
        })

    return sorted(sizes, key=lambda x: -x['output_length'])


def find_cache_candidates(tool_uses: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Find repeated identical operations (cache candidates)."""
    # Group by (tool_name, input_json)
    operations = defaultdict(list)
    for tool in tool_uses:
        # Use JSON serialization for consistent hashing
        key = (tool['name'], json.dumps(tool['input'], sort_keys=True))
        operations[key].append(tool)

    candidates = []
    for (tool_name, input_json), occurrences in operations.items():
        if len(occurrences) > 1:
            candidates.append({
                'operation': {
                    'name': tool_name,
                    'input': json.loads(input_json)
                },
                'repeat_count': len(occurrences),
                'optimization': 'CACHE_CANDIDATE'
            })

    return sorted(candidates, key=lambda x: -x['repeat_count'])


def find_batch_candidates(tool_uses: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Find consecutive similar operations (batch candidates)."""
    if not tool_uses:
        return []

    batches = []
    current_batch = [tool_uses[0]]

    for tool in tool_uses[1:]:
        if tool['name'] == current_batch[-1]['name']:
            current_batch.append(tool)
        else:
            if len(current_batch) > MIN_BATCH_SIZE:
                batches.append({
                    'tool': current_batch[0]['name'],
                    'consecutive_count': len(current_batch),
                    'optimization': 'BATCH_CANDIDATE'
                })
            current_batch = [tool]

    # Don't forget the last batch
    if len(current_batch) > 2:
        batches.append({
            'tool': current_batch[0]['name'],
            'consecutive_count': len(current_batch),
            'optimization': 'BATCH_CANDIDATE'
        })

    return sorted(batches, key=lambda x: -x['consecutive_count'])


def find_parallel_candidates(tool_uses: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Find independent operations in same message (parallel candidates)."""
    # Group tools by message_id
    message_tools = defaultdict(list)
    for tool in tool_uses:
        message_id = tool.get('message_id')
        if message_id:
            message_tools[message_id].append(tool)

    candidates = []
    for message_id, tools in message_tools.items():
        if len(tools) > 1:
            candidates.append({
                'message_id': message_id,
                'parallel_tools': [t['name'] for t in tools],
                'count': len(tools),
                'optimization': 'PARALLEL_CANDIDATE'
            })

    return sorted(candidates, key=lambda x: -x['count'])


def analyze_session(file_path: str) -> Dict[str, Any]:
    """Main analysis function."""
    entries = parse_jsonl(file_path)
    tool_uses = extract_tool_uses(entries)

    return {
        'tool_frequency': calculate_tool_frequency(tool_uses),
        'token_usage': calculate_token_usage(entries, tool_uses),
        'output_sizes': extract_output_sizes(entries),
        'cache_candidates': find_cache_candidates(tool_uses),
        'batch_candidates': find_batch_candidates(tool_uses),
        'parallel_candidates': find_parallel_candidates(tool_uses),
        'summary': {
            'total_tool_calls': len(tool_uses),
            'unique_tools': sorted(list(set(t['name'] for t in tool_uses))),
            'total_entries': len(entries)
        }
    }


def main():
    parser = argparse.ArgumentParser(
        description='Analyze Claude Code session JSONL file for optimization opportunities'
    )
    parser.add_argument(
        'session_file',
        help='Path to session JSONL file'
    )

    args = parser.parse_args()

    try:
        result = analyze_session(args.session_file)
        print(json.dumps(result, indent=2))
    except FileNotFoundError:
        print(f"Error: Session file not found: {args.session_file}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error analyzing session: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()

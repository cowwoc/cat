#!/usr/bin/env python3
"""
Analyze Claude Code session JSONL file for optimization opportunities.

Extracts tool usage patterns, identifies batching/caching/parallel candidates,
and provides metrics for optimization recommendations.
"""

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
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


def discover_subagents(file_path: str) -> List[str]:
    """
    Discover subagent JSONL files from parent session.

    Parses the session JSONL for Task tool_result entries containing agentId,
    then resolves subagent file paths. Returns only paths that exist on disk.

    Args:
        file_path: Path to parent session JSONL file

    Returns:
        List of discovered subagent file paths (only existing files)
    """
    entries = parse_jsonl(file_path)
    agent_ids = set()

    # Extract agentId from Task tool_result entries
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

        # Look for agentId in the content (JSON structure)
        try:
            # Try to parse as JSON to find agentId
            if '"agentId":' in content:
                # Extract agent IDs using simple pattern matching
                matches = re.findall(r'"agentId"\s*:\s*"([^"]+)"', content)
                agent_ids.update(matches)
        except Exception:
            # If parsing fails, continue
            pass

    # Resolve subagent file paths
    session_dir = Path(file_path).parent
    subagent_dir = session_dir / 'subagents'

    subagent_paths = []
    for agent_id in agent_ids:
        subagent_path = subagent_dir / f'agent-{agent_id}.jsonl'
        if subagent_path.exists():
            subagent_paths.append(str(subagent_path))

    return sorted(subagent_paths)


def analyze_single_agent(file_path: str) -> Dict[str, Any]:
    """
    Analyze a single agent's JSONL file.

    Returns the same structure as analyze_session but without subagent/combined keys.
    """
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


def merge_tool_frequency(frequencies: List[List[Dict[str, Any]]]) -> List[Dict[str, Any]]:
    """Merge tool frequency counts from multiple agents."""
    merged = defaultdict(int)
    for freq_list in frequencies:
        for item in freq_list:
            merged[item['tool']] += item['count']

    return [
        {'tool': tool, 'count': count}
        for tool, count in sorted(merged.items(), key=lambda x: -x[1])
    ]


def merge_cache_candidates(cache_lists: List[List[Dict[str, Any]]]) -> List[Dict[str, Any]]:
    """Merge cache candidates from multiple agents."""
    # Group by operation signature
    merged = defaultdict(int)
    operation_map = {}

    for cache_list in cache_lists:
        for item in cache_list:
            op = item['operation']
            key = (op['name'], json.dumps(op['input'], sort_keys=True))
            merged[key] += item['repeat_count']
            operation_map[key] = op

    candidates = []
    for key, count in merged.items():
        if count > 1:
            candidates.append({
                'operation': operation_map[key],
                'repeat_count': count,
                'optimization': 'CACHE_CANDIDATE'
            })

    return sorted(candidates, key=lambda x: -x['repeat_count'])


def sum_token_usage(usage_lists: List[List[Dict[str, Any]]]) -> List[Dict[str, Any]]:
    """Sum token usage across multiple agents."""
    merged = defaultdict(lambda: {
        'total_input_tokens': 0,
        'total_output_tokens': 0,
        'count': 0
    })

    for usage_list in usage_lists:
        for item in usage_list:
            tool = item['tool']
            merged[tool]['total_input_tokens'] += item['total_input_tokens']
            merged[tool]['total_output_tokens'] += item['total_output_tokens']
            merged[tool]['count'] += item['count']

    return [
        {
            'tool': tool,
            'total_input_tokens': stats['total_input_tokens'],
            'total_output_tokens': stats['total_output_tokens'],
            'count': stats['count']
        }
        for tool, stats in sorted(
            merged.items(),
            key=lambda x: -x[1]['total_input_tokens']
        )
    ]


def analyze_session(file_path: str) -> Dict[str, Any]:
    """
    Main analysis function with subagent discovery and combined analysis.

    Analyzes the main session and discovers any subagent sessions, providing
    per-agent and combined metrics.

    Returns:
        Dictionary containing:
        - 'main': Main agent metrics
        - 'subagents': Dict of agentId -> per-subagent analysis
        - 'combined': Aggregated metrics across all agents
    """
    # Analyze main agent
    main_analysis = analyze_single_agent(file_path)

    # Discover and analyze subagents
    subagent_paths = discover_subagents(file_path)
    subagents = {}

    for subagent_path in subagent_paths:
        # Extract agent ID from filename (agent-{agentId}.jsonl)
        agent_id = Path(subagent_path).stem.replace('agent-', '')
        try:
            subagents[agent_id] = analyze_single_agent(subagent_path)
        except Exception as e:
            # Skip subagents that fail to analyze
            print(f"Warning: Failed to analyze subagent {agent_id}: {e}", file=sys.stderr)
            continue

    # Build combined analysis
    all_analyses = [main_analysis] + list(subagents.values())

    combined = {
        'tool_frequency': merge_tool_frequency([a['tool_frequency'] for a in all_analyses]),
        'cache_candidates': merge_cache_candidates([a['cache_candidates'] for a in all_analyses]),
        'token_usage': sum_token_usage([a['token_usage'] for a in all_analyses]),
        'summary': {
            'total_tool_calls': sum(a['summary']['total_tool_calls'] for a in all_analyses),
            'unique_tools': sorted(list(set().union(
                *[set(a['summary']['unique_tools']) for a in all_analyses]
            ))),
            'total_entries': sum(a['summary']['total_entries'] for a in all_analyses),
            'agent_count': len(all_analyses)
        }
    }

    return {
        'main': main_analysis,
        'subagents': subagents,
        'combined': combined
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

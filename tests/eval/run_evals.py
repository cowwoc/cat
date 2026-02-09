#!/usr/bin/env python3
"""
Eval harness for CAT skill activation testing.

Runs test prompts through `claude -p --output-format stream-json --max-turns 1`
and checks if the correct skill was activated.
"""

import json
import shutil
import subprocess
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, List, NamedTuple, Optional, Tuple

# Maximum time to wait for a single test case before timing out (prevents hanging on infinite loops)
TIMEOUT_SECONDS = 60


class ParseResult(NamedTuple):
    """
    Result of parsing JSONL stream output.

    Attributes:
        skill_name: The extracted skill name (without 'cat:' prefix), or None if not found
        parse_errors: Count of lines that failed to parse as JSON
        lines_parsed: Total number of non-empty lines processed
        tool_use_count: Number of tool_use blocks found (for diagnostics)
    """
    skill_name: Optional[str]
    parse_errors: int
    lines_parsed: int
    tool_use_count: int


@dataclass
class EvalResult:
    """
    Result of a single eval test case.

    Attributes:
        test_id: Unique identifier for the test case
        expected_skill: The skill that should be activated (None for negative tests)
        actual_skill: The skill that was actually activated (None if no skill triggered)
        passed: Whether the test passed
        prompt: The input prompt used for testing
        category: Test category (explicit, implicit, negative)
        raw_output: Raw JSONL output from Claude CLI
        is_timeout: Whether the test timed out
        stderr: Any stderr output from the subprocess
    """
    test_id: str
    expected_skill: Optional[str]
    actual_skill: Optional[str]
    passed: bool
    prompt: str
    category: str
    raw_output: str = ""
    is_timeout: bool = False
    stderr: str = ""

    def to_dict(self) -> Dict:
        """Convert result to dictionary for JSON serialization."""
        result = asdict(self)
        # Truncate raw_output for storage efficiency (max 1000 chars to keep result files manageable)
        if len(result['raw_output']) > 1000:
            result['raw_output'] = result['raw_output'][:1000] + "... [truncated]"
        return result


def parse_jsonl_stream(output: str) -> ParseResult:
    """
    Parse JSONL stream output from claude CLI.
    Look for tool_use events that contain Skill tool calls.

    Args:
        output: JSONL stream output from Claude CLI

    Returns:
        ParseResult containing:
        - skill_name: The extracted skill name (without 'cat:' prefix), or None if not found
        - parse_errors: Count of lines that failed to parse as JSON
        - lines_parsed: Total number of non-empty lines processed
        - tool_use_count: Number of tool_use blocks found (for diagnostics)
    """
    parse_errors = 0
    lines_parsed = 0
    tool_use_count = 0

    for line in output.strip().split('\n'):
        if not line.strip():
            continue

        lines_parsed += 1

        try:
            event = json.loads(line)

            # Look for assistant message events with tool_use
            if event.get('type') == 'assistant':
                message = event.get('message', {})
                content_blocks = message.get('content', [])
                for block in content_blocks:
                    if block.get('type') == 'tool_use':
                        tool_use_count += 1
                        if block.get('name') == 'Skill':
                            input_data = block.get('input', {})
                            skill_name = input_data.get('skill', '')
                            # Remove 'cat:' prefix if present
                            if skill_name.startswith('cat:'):
                                skill_name = skill_name[4:]
                            return ParseResult(
                                skill_name=skill_name if skill_name else None,
                                parse_errors=parse_errors,
                                lines_parsed=lines_parsed,
                                tool_use_count=tool_use_count
                            )

        except json.JSONDecodeError as e:
            parse_errors += 1
            print(f"    WARNING: Failed to parse JSON line: {line[:50]}... ({e})", file=sys.stderr)
            continue

    return ParseResult(
        skill_name=None,
        parse_errors=parse_errors,
        lines_parsed=lines_parsed,
        tool_use_count=tool_use_count
    )


def run_single_eval(
    test_case: Dict,
    claude_bin: str
) -> EvalResult:
    """
    Run a single eval test case.

    Args:
        test_case: Test case dictionary with id, prompt, skill, category
        claude_bin: Path to the claude binary

    Returns:
        EvalResult containing test outcome and details
    """
    test_id = test_case['id']
    prompt = test_case['prompt']
    expected_skill = test_case.get('skill')
    category = test_case['category']

    print(f"  Running {test_id}: {prompt[:50]}...")

    try:
        # Run claude with the prompt
        result = subprocess.run(
            [
                claude_bin,
                '-p', prompt,
                '--output-format', 'stream-json',
                '--max-turns', '1',
                '--verbose'
            ],
            capture_output=True,
            text=True,
            timeout=TIMEOUT_SECONDS,
            cwd='/workspace'
        )

        raw_output = result.stdout
        stderr_output = result.stderr

        # Check for subprocess errors
        if result.returncode != 0 and stderr_output:
            print(f"    WARNING: Process exited with code {result.returncode}, stderr: {stderr_output[:100]}")

        parse_result = parse_jsonl_stream(raw_output)
        actual_skill = parse_result.skill_name

        # Determine if test passed
        if category == 'negative':
            # Negative tests: should NOT activate any skill
            passed = actual_skill is None
        else:
            # Positive tests: should activate the expected skill
            passed = actual_skill == expected_skill

        return EvalResult(
            test_id=test_id,
            expected_skill=expected_skill,
            actual_skill=actual_skill,
            passed=passed,
            prompt=prompt,
            category=category,
            raw_output=raw_output,
            is_timeout=False,
            stderr=stderr_output
        )

    except subprocess.TimeoutExpired:
        print(f"    TIMEOUT after {TIMEOUT_SECONDS}s")
        return EvalResult(
            test_id=test_id,
            expected_skill=expected_skill,
            actual_skill=None,
            passed=False,
            prompt=prompt,
            category=category,
            raw_output="TIMEOUT",
            is_timeout=True,
            stderr=""
        )
    except FileNotFoundError as e:
        print(f"    ERROR: File not found: {e}")
        return EvalResult(
            test_id=test_id,
            expected_skill=expected_skill,
            actual_skill=None,
            passed=False,
            prompt=prompt,
            category=category,
            raw_output=f"ERROR: File not found: {e}",
            is_timeout=False,
            stderr=""
        )


def aggregate_skill_stats(results: List[EvalResult]) -> Dict:
    """
    Aggregate per-skill statistics from eval results.

    Args:
        results: List of EvalResult objects

    Returns:
        Dictionary mapping skill names to their statistics (total, passed, failed, activation_rate)
    """
    skill_stats = {}
    for result in results:
        if result.category == 'negative':
            continue

        skill = result.expected_skill
        if skill not in skill_stats:
            skill_stats[skill] = {'total': 0, 'passed': 0, 'failed': 0}

        skill_stats[skill]['total'] += 1
        if result.passed:
            skill_stats[skill]['passed'] += 1
        else:
            skill_stats[skill]['failed'] += 1

    # Calculate activation rates
    for skill, stats in skill_stats.items():
        stats['activation_rate'] = (stats['passed'] / stats['total'] * 100) if stats['total'] > 0 else 0

    return skill_stats


def compute_summary(results: List[EvalResult]) -> Dict:
    """
    Compute summary statistics from eval results.

    Args:
        results: List of EvalResult objects

    Returns:
        Dictionary with overall statistics (total_tests, passed, failed, timeout_count, pass_rate, skill_stats)
    """
    total = len(results)
    passed = sum(1 for r in results if r.passed)
    failed = total - passed
    timeout_count = sum(1 for r in results if r.is_timeout)

    return {
        'total_tests': total,
        'passed': passed,
        'failed': failed,
        'timeout_count': timeout_count,
        'pass_rate': (passed / total * 100) if total > 0 else 0,
        'skill_stats': aggregate_skill_stats(results)
    }


def run_eval_suite(
    test_cases_path: Path,
    output_dir: Path,
    claude_bin: str
) -> Tuple[List[EvalResult], Dict]:
    """
    Run the full eval suite and return results.

    Args:
        test_cases_path: Path to test_cases.json
        output_dir: Directory for output files
        claude_bin: Path to claude binary

    Returns:
        Tuple of (results, summary) where results is a list of EvalResult objects
        and summary is a dictionary with aggregate statistics
    """
    # Load test cases
    with open(test_cases_path) as f:
        data = json.load(f)
    test_cases = data['test_cases']

    print(f"\nRunning {len(test_cases)} eval test cases...\n")

    # Run all tests
    results = []
    for test_case in test_cases:
        result = run_single_eval(test_case, claude_bin)
        results.append(result)

        # Print result
        status = "✓ PASS" if result.passed else "✗ FAIL"
        print(f"    {status}: expected={result.expected_skill}, actual={result.actual_skill}")

    # Calculate summary statistics
    summary = compute_summary(results)

    return results, summary


def save_results(results: List[EvalResult], summary: Dict, output_dir: Path):
    """Save eval results to JSON files."""
    output_dir.mkdir(parents=True, exist_ok=True)

    # Save detailed results
    results_file = output_dir / 'results.json'
    with open(results_file, 'w') as f:
        json.dump([r.to_dict() for r in results], f, indent=2)

    # Save summary
    summary_file = output_dir / 'summary.json'
    with open(summary_file, 'w') as f:
        json.dump(summary, f, indent=2)

    print(f"\nResults saved to {output_dir}")


def print_summary(summary: Dict):
    """Print eval summary to console."""
    print("\n" + "=" * 60)
    print("EVAL SUMMARY")
    print("=" * 60)
    print(f"Total tests: {summary['total_tests']}")
    print(f"Passed: {summary['passed']}")
    print(f"Failed: {summary['failed']}")
    if summary['timeout_count'] > 0:
        print(f"Timeouts: {summary['timeout_count']}")
    print(f"Pass rate: {summary['pass_rate']:.1f}%")
    print("\nPer-skill activation rates:")
    print("-" * 60)

    skill_stats = summary['skill_stats']
    for skill in sorted(skill_stats.keys()):
        stats = skill_stats[skill]
        rate = stats['activation_rate']
        status = "✓" if rate == 100 else "✗"
        print(f"  {status} {skill:30s} {rate:5.1f}% ({stats['passed']}/{stats['total']})")

    print("=" * 60)


def find_claude_binary() -> str:
    """
    Find the claude CLI binary in PATH.

    Returns:
        Path to claude binary

    Raises:
        SystemExit: If claude binary is not found
    """
    claude_bin = shutil.which('claude')
    if not claude_bin:
        print("ERROR: 'claude' not found in PATH", file=sys.stderr)
        print("This eval requires the Claude Code CLI to be installed.", file=sys.stderr)
        sys.exit(1)
    return claude_bin


def main():
    """Main entry point for eval harness."""
    script_dir = Path(__file__).parent
    test_cases_path = script_dir / 'test_cases.json'
    output_dir = script_dir / 'results'

    # Find claude CLI dynamically
    claude_bin = find_claude_binary()
    print(f"Using claude binary: {claude_bin}")

    # Run evals
    try:
        results, summary = run_eval_suite(
            test_cases_path=test_cases_path,
            output_dir=output_dir,
            claude_bin=claude_bin
        )

        # Save and print results
        save_results(results, summary, output_dir)
        print_summary(summary)

        # Exit with failure code if any tests failed
        sys.exit(0 if summary['failed'] == 0 else 1)

    except FileNotFoundError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n\nEval interrupted by user")
        sys.exit(1)


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""
Smoke test for eval harness - runs a small subset of test cases to verify the harness works.
Use this before running the full eval suite to catch issues early.
"""

import json
import sys
from pathlib import Path
from run_evals import run_single_eval, find_claude_binary, EvalResult


def run_smoke_test():
    """Run a quick smoke test with 3 test cases."""
    script_dir = Path(__file__).parent
    test_cases_path = script_dir / 'test_cases.json'

    # Find claude CLI dynamically (reuse from run_evals)
    claude_bin = find_claude_binary()
    print(f"Using claude binary: {claude_bin}")

    # Load test cases
    with open(test_cases_path) as f:
        data = json.load(f)
    all_test_cases = data['test_cases']

    # Select a few representative cases
    test_ids = ['status-1', 'work-2', 'negative-1']
    smoke_cases = []
    for test_id in test_ids:
        test_case = next((tc for tc in all_test_cases if tc['id'] == test_id), None)
        if not test_case:
            print(f"ERROR: Test case '{test_id}' not found in test_cases.json", file=sys.stderr)
            sys.exit(1)
        smoke_cases.append(test_case)

    print("\n" + "=" * 60)
    print("SMOKE TEST - Running 3 test cases")
    print("=" * 60)

    results = []
    for test_case in smoke_cases:
        result = run_single_eval(test_case, claude_bin)
        results.append(result)

        status = "✓ PASS" if result.passed else "✗ FAIL"
        print(f"\n{status}")
        print(f"  ID: {result.test_id}")
        print(f"  Prompt: {result.prompt}")
        print(f"  Expected: {result.expected_skill}")
        print(f"  Actual: {result.actual_skill}")

    passed = sum(1 for r in results if r.passed)
    failed = len(results) - passed

    print("\n" + "=" * 60)
    print(f"Smoke test: {passed}/{len(results)} passed")
    print("=" * 60)

    if failed > 0:
        print("\nSmoke test FAILED - fix issues before running full eval suite")
        sys.exit(1)
    else:
        print("\nSmoke test PASSED - ready for full eval suite")
        sys.exit(0)


if __name__ == '__main__':
    run_smoke_test()

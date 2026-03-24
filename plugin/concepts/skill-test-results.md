<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill Test Results Schema

After a test run against a skill's `test/` directory, the results are written to a `results.json`
file in the skill directory. This file captures enough information to reproduce the run and compare
runs across model versions or skill revisions.

## File Location

```
plugin/skills/<skill-name>/results.json
```

## Top-Level Fields

| Field | Type | Description |
|-------|------|-------------|
| `skill_hash` | string | SHA-256 hash of the skill's `first-use.md` at the time of the run |
| `model` | string | Model identifier used for this run (e.g., `claude-sonnet-4-6`) |
| `session_id` | string | Claude session ID from `CLAUDE_SESSION_ID` at run time |
| `timestamp` | string | ISO-8601 UTC timestamp when the run completed |
| `overall_decision` | string | Aggregated decision across all test cases: `pass`, `fail`, or `inconclusive` |
| `test_cases` | array | Per-test-case SPRT data (see below) |

## test_cases Array Entry Fields

Each entry in `test_cases` corresponds to one `.md` file in the skill's `test/` directory.

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Filename without extension (e.g., `squash-trigger-basic`) |
| `type` | string | Test type copied from frontmatter: `should-trigger`, `should-not-trigger`, or `behavior` |
| `log_ratio` | number | SPRT log-likelihood ratio at the time of the run |
| `pass_count` | integer | Number of runs where the assertion held |
| `fail_count` | integer | Number of runs where the assertion did not hold |
| `total_runs` | integer | `pass_count + fail_count` |
| `total_tokens` | integer | Cumulative token count across all runs for this test case |
| `total_duration_ms` | integer | Cumulative wall-clock time in milliseconds across all runs |

## overall_decision Values

| Value | Meaning |
|-------|---------|
| `pass` | All test cases reached the SPRT acceptance threshold |
| `fail` | At least one test case reached the SPRT rejection threshold |
| `inconclusive` | One or more test cases did not reach either threshold |

## Example

```json
{
  "skill_hash": "a3f8b1c2d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1",
  "model": "claude-sonnet-4-6",
  "session_id": "abc123de-f456-7890-abcd-ef1234567890",
  "timestamp": "2026-03-24T14:30:00Z",
  "overall_decision": "pass",
  "test_cases": [
    {
      "id": "squash-trigger-basic",
      "type": "should-trigger",
      "log_ratio": 2.94,
      "pass_count": 8,
      "fail_count": 2,
      "total_runs": 10,
      "total_tokens": 14800,
      "total_duration_ms": 42000
    },
    {
      "id": "squash-should-not-trigger-rebase",
      "type": "should-not-trigger",
      "log_ratio": 3.10,
      "pass_count": 9,
      "fail_count": 1,
      "total_runs": 10,
      "total_tokens": 13200,
      "total_duration_ms": 38000
    }
  ]
}
```

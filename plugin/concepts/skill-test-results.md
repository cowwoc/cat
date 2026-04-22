# Skill Test Results Schema

After a test run against a skill's `test/` directory, the results are written to a `results.json`
file in the skill directory. This file captures enough information to reproduce the run and compare
runs across model versions or skill revisions.

## File Location

```
plugin/tests/<path>/<name>/results.json
```

## Top-Level Fields

| Field | Type | Description |
|-------|------|-------------|
| `skill_hash` | string | SHA-256 hash of the skill's `first-use.md` at the time of the run |
| `model_id` | string | Fully-qualified model identifier (e.g., `claude-haiku-4-5`, `claude-sonnet-4-5`) |
| `session_id` | string | Claude session ID from `CLAUDE_SESSION_ID` at run time |
| `timestamp` | string | ISO-8601 UTC timestamp when the run completed |
| `overall_decision` | string | Aggregated decision across all scenarios: `pass`, `fail`, or `inconclusive` |
| `scenarios` | object | Per-scenario SPRT data keyed by scenario filename without extension (see below) |

## scenarios Object Entry Fields

Each key in `scenarios` is the scenario filename without extension (e.g., `squash-trigger-basic`).
Each value contains the SPRT statistics tracked independently per assertion.

| Field | Type | Description |
|-------|------|-------------|
| `assertions` | array | Array of SPRT data objects, one per assertion in the scenario file (ordered to match the `## Assertions` list) |

## Assertion Entry Fields

Each element of the `assertions` array contains:

| Field | Type | Description |
|-------|------|-------------|
| `log_ratio` | number | SPRT log-likelihood ratio at the time of the run |
| `pass_count` | integer | Number of runs where this assertion held |
| `fail_count` | integer | Number of runs where this assertion did not hold |
| `total_runs` | integer | `pass_count + fail_count` |
| `total_tokens` | integer | Cumulative token count across all runs for this assertion |
| `total_duration_ms` | integer | Cumulative wall-clock time in milliseconds across all runs |
| `decision` | string | Per-assertion SPRT decision: `pass`, `fail`, or `inconclusive` |

## overall_decision Values

| Value | Meaning |
|-------|---------|
| `pass` | All test cases reached the SPRT acceptance threshold |
| `fail` | At least one test case reached the SPRT rejection threshold |
| `inconclusive` | One or more test cases did not reach either threshold |

## Staleness Detection

When the `model_id` in cached results differs from the current model's fully-qualified identifier (as resolved by
`ModelIdResolver`), all cached SPRT results are invalidated. The `init-sprt` command compares the `model_id` field
in the prior instruction-test against the current model and skips carry-forward entirely when they differ.

Results with no `model_id` field (from older runs) are also treated as stale.

## Example

```json
{
  "skill_hash": "a3f8b1c2d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1",
  "model_id": "claude-sonnet-4-5",
  "session_id": "abc123de-f456-7890-abcd-ef1234567890",
  "timestamp": "2026-03-24T14:30:00Z",
  "overall_decision": "pass",
  "scenarios": {
    "squash-trigger-basic": {
      "assertions": [
        {
          "log_ratio": 2.94,
          "pass_count": 8,
          "fail_count": 2,
          "total_runs": 10,
          "total_tokens": 14800,
          "total_duration_ms": 42000,
          "decision": "pass"
        },
        {
          "log_ratio": 1.50,
          "pass_count": 7,
          "fail_count": 3,
          "total_runs": 10,
          "total_tokens": 14800,
          "total_duration_ms": 42000,
          "decision": "INCONCLUSIVE"
        }
      ]
    },
    "squash-should-not-trigger-rebase": {
      "assertions": [
        {
          "log_ratio": 3.10,
          "pass_count": 9,
          "fail_count": 1,
          "total_runs": 10,
          "total_tokens": 13200,
          "total_duration_ms": 38000,
          "decision": "pass"
        },
        {
          "log_ratio": 2.80,
          "pass_count": 8,
          "fail_count": 2,
          "total_runs": 10,
          "total_tokens": 13200,
          "total_duration_ms": 38000,
          "decision": "pass"
        }
      ]
    }
  }
}
```

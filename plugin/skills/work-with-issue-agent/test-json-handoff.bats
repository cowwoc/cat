#!/usr/bin/env bats
# Tests that JSON containing special characters survives a printf '%s' write / cat read round-trip.
# This validates the file-write/file-read pattern used by EXECUTION_COMMITS_JSON_PATH and
# COMMITS_JSON_PATH handoffs in work-with-issue-agent → work-confirm-agent → work-merge-agent.

setup() {
    TMPFILE=$(mktemp)
}

teardown() {
    rm -f "$TMPFILE"
}

@test "JSON with special chars survives printf write and cat read round-trip" {
    # Construct a synthetic JSON commits array containing all problematic special characters:
    # {, }, ", :, [, ], whitespace, comma
    ORIGINAL='[{"hash":"abc123","message":"feat: add {feature} with \"quotes\" and [brackets]","filesChanged":3}]'

    printf '%s' "$ORIGINAL" > "$TMPFILE"
    ROUNDTRIP=$(cat "$TMPFILE")

    [ "$ROUNDTRIP" = "$ORIGINAL" ]
}

@test "JSON with newlines survives printf write and cat read round-trip" {
    # Multi-line JSON (pretty-printed) also must survive intact
    ORIGINAL='[
  {
    "hash": "def456",
    "message": "refactor: restructure {module}: [old] -> [new]",
    "filesChanged": 7
  }
]'

    printf '%s' "$ORIGINAL" > "$TMPFILE"
    ROUNDTRIP=$(cat "$TMPFILE")

    [ "$ROUNDTRIP" = "$ORIGINAL" ]
}

@test "empty JSON array survives printf write and cat read round-trip" {
    ORIGINAL='[]'

    printf '%s' "$ORIGINAL" > "$TMPFILE"
    ROUNDTRIP=$(cat "$TMPFILE")

    [ "$ROUNDTRIP" = "$ORIGINAL" ]
}

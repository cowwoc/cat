<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Phase 4: Record

This phase records the learning by running the `record-learning` CLI tool, which handles all file I/O
mechanically: generating the next mistake ID, appending to `mistakes-YYYY-MM.json`, updating the
retrospective counter in `index.json`, and committing everything in a single operation.

## Step 10: Run record-learning CLI

Save the Phase 3 JSON output to a temporary file, then invoke the CLI tool:

```bash
# Save Phase 3 output to temp file
cat > /tmp/phase3-output.json << 'PHASE3_EOF'
{...Phase 3 JSON output...}
PHASE3_EOF

# Run the record-learning tool
"$CLIENT_BIN/record-learning" < /tmp/phase3-output.json
```

The tool reads Phase 3 JSON from stdin and outputs a JSON result to stdout.

## Output

The tool outputs JSON to stdout:

```json
{
  "learning_id": "M463",
  "counter_status": {
    "count": 5,
    "threshold": 10,
    "days_since_last": 3,
    "interval_days": 7
  },
  "retrospective_trigger": false,
  "commit_hash": "abc123"
}
```

Capture the output and return it as the Phase 4 result. If the tool exits non-zero, the recording failed —
do not proceed and report the error to the caller.

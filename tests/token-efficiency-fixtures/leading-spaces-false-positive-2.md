<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Mixed Indentation Example

## Purpose

Illustrate a mixed-indentation block: part intentional (inside a fenced block),
part redundant (outside it). The detector must only flag the redundant portion.

## Procedure

### Step 1: Compare Output Formats

Acceptable JSON output structure:

```json
{
    "status": "ok",
    "items": [
        "alpha",
        "beta"
    ]
}
```

After reviewing JSON output, run the follow-up command:

    summarize --format table

The JSON block is exempt (fenced code block); only the single indented `summarize` line
outside the block could be considered for compaction. A detector must distinguish between
the two contexts and not flag the JSON block.

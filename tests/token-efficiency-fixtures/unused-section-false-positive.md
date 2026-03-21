<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill With Conditional Content Section

## Purpose

Demonstrate a false positive for unused output section detection.
A section with conditional content that sometimes produces no output is still necessary.

## Procedure

### Step 1: Scan for Issues

Run the scanner and collect issues. Store results as SCAN_RESULTS.

### Step 2: Report Issues Found (conditional)

{Include this section only if SCAN_RESULTS contains at least one issue}

| File | Issue | Severity |
|------|-------|----------|
| {file} | {issue description} | {high/medium/low} |

If no issues are found, omit this section entirely from the output.

### Step 3: Summary

Output the total count of files scanned and issues found.

The table in Step 2 is conditional on SCAN_RESULTS — it produces output when issues are
found and is suppressed when there are none. A detector must not flag this as an "unused
section" because it genuinely produces content in real invocations. Only sections that
are structurally incapable of producing output should be flagged.

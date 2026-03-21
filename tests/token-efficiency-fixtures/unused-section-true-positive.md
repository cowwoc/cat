<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill With Genuinely Unused Table

## Purpose

Demonstrate a true positive for unused output section detection.

## Procedure

### Step 1: Analyze Dependencies

Scan the dependency list and classify each as direct or transitive.

### Step 2: Report Skipped Dependencies

The following table tracks dependencies skipped by the scanner.
This table is always empty because the scanner never skips valid dependencies.

| Package | Reason Skipped | Timestamp |
|---------|---------------|-----------|

No entries ever appear here — the scanner does not have a skip code path. The table
exists as a placeholder that was never removed when the skip feature was cancelled.
This is a true positive: the section produces zero rows unconditionally and the output
is never referenced downstream.

### Step 3: Produce Summary

Output the total count of direct and transitive dependencies.

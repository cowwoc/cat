<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Example Skill

## Purpose

Illustrate redundant leading spaces in non-fenced example blocks.

## Procedure

### Step 1: Run Validation

Execute the validation command using the following pattern:

    validate --config config.yaml
    validate --report json
    validate --output results/
    validate --fail-fast

All four lines share an identical 4-space indent that serves no semantic purpose outside a
fenced code block — this is a true positive for the redundant-leading-spaces pattern. The
indentation could be replaced with a fenced code block or a tab-based indent.

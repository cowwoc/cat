---
category: requirement
model: haiku
---
## Turn 1

# Plan

## Goal

Create a Bash validation utility in plugin/scripts/validate-uuid.sh that checks whether a given string
matches UUID format.

## Pre-conditions

- plugin/scripts/ directory exists

## Post-conditions

- [ ] plugin/scripts/validate-uuid.sh exists
- [ ] File begins with shebang: #!/usr/bin/env bash
- [ ] File includes license header comment block immediately after shebang (per .claude/rules/license-header.md)
- [ ] File contains function: validate_uuid() { [[ "$1" =~ ^[0-9a-fA-F]\{8\}-[0-9a-fA-F]\{4\}-[0-9a-fA-F]\{4\}-[0-9a-fA-F]\{4\}-[0-9a-fA-F]\{12\}$ ]]; }
- [ ] File ends with test block: validate_uuid "3734035c-0c9b-4063-9d0f-aad98ed260a6" && echo "valid" || echo "invalid"

## Execution Steps

- Create plugin/scripts/validate-uuid.sh with the following exact content:
  ```
  #!/usr/bin/env bash
  # Copyright (c) 2026 Gili Tzabari. All rights reserved.
  #
  # Licensed under the CAT Commercial License.
  # See LICENSE.md in the project root for license terms.
  validate_uuid() { [[ "$1" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; }
  validate_uuid "3734035c-0c9b-4063-9d0f-aad98ed260a6" && echo "valid" || echo "invalid"
  ```
- Update index.json: set `"status": "closed"`

## Assertions

1. Agent creates or writes the file at path `plugin/scripts/validate-uuid.sh` (not at any other path)
2. Agent does not create a file named `uuid-validator.sh` or `validate_uuid.sh` or any path other than `plugin/scripts/validate-uuid.sh`

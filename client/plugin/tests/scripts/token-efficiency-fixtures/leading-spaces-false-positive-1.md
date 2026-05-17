<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Example Skill — Intentional Indentation

## Purpose

Illustrate a fenced code block where indentation is intentional and must not be modified.

## Procedure

### Step 1: Format Output

The tool produces indented output to show nesting hierarchy:

```
project/
    src/
        main.java
        util.java
    tests/
        test_main.java
```

The 4-space indent inside this fenced code block represents folder nesting depth.
Collapsing it would destroy the visual hierarchy — this is a false positive for the
redundant-leading-spaces detector. The exempt context rule (fenced code blocks) applies.

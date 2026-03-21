<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Test Skill: Fenced Code Block Indentation Exemption

## Purpose

Test that the compact-output pass preserves indentation inside fenced code blocks where the
indentation is part of the example output being illustrated.

## Procedure: Demonstrate Code Block Indentation

The following Python code illustrates nested indentation as an intentional part of the example.
The compact-output pass MUST NOT alter the indentation inside the fenced block:

```python
def greet(name):
    if name:
        print(f"Hello, {name}")
    else:
        print("Hello, world")
```

This second block shows indented output that is the expected result — the 4-space indent is
semantically meaningful as "output from a subprocess":

```
    line 1 of subprocess output
    line 2 of subprocess output
        further indented continuation
```

Outside the fenced blocks, the heading "Procedure: Demonstrate Code Block Indentation" is
verbose. The compact-output pass MAY remove the redundant ": Demonstrate Code Block Indentation"
suffix since the skill name already provides context.

## Verification

- [ ] Indentation inside both fenced blocks is byte-for-byte identical after the pass
- [ ] No lines were added, removed, or reindented inside the fenced blocks

<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Test Skill: Makefile Target Exemption

## Purpose

Test that the compact-output pass preserves Makefile tab indentation inside fenced code blocks
that contain Makefile targets. Tabs in Makefile targets are required syntax — spaces would break
`make` execution.

## Procedure

The following fenced code block contains a Makefile target. The indented recipe lines use real
tab characters. The compact-output pass MUST NOT convert these tabs to spaces or collapse them:

```makefile
build: src/main.c
	gcc -o build/output src/main.c
	strip build/output

clean:
	rm -rf build/
```

Outside the fenced block, the heading "Procedure" is concise and must not be modified. The
verbose description paragraph above may be shortened.

## Verification

- [ ] Tab characters inside Makefile fenced block are unchanged
- [ ] `make` would still parse the recipe correctly after pass

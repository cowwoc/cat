<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Get Output

Centralized silent executor for generating verbatim output from display box handlers.

When you need to display a skill-generated box (status, config, diffs, etc.), invoke this skill with the output type.
The type uses dot-notation: `skill[.page]`.

ARGUMENTS: type [extra-args...]

Examples:
- `Skill("cat:get-output", args="status")` — generates the status box
- `Skill("cat:get-output", args="config.settings")` — generates the config settings page

The output is wrapped in `<output type="...">` tags. Echo it exactly as returned. Do not summarize, interpret, or add
commentary.

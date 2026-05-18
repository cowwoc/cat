<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Claude SPRT Runner

Run CAT's formal SPRT pipeline through the Claude jlink `sprt-runner` binary. Use the canonical
`run-sprt` command.

The effort argument belongs immediately after the model id. The runner executes trials and graders through
`cat:spawn-engine` semantics on the engine that invoked `sprt-runner`; from Claude it launches
Claude-backed trial and grader runs.

<!-- cat:include ../../include/sprt-runner.md -->

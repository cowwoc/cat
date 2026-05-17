<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Codex SPRT Runner

Run CAT's formal SPRT pipeline through the Codex jlink `instruction-test-runner` binary. Use the canonical
`run-sprt` command.

The effort argument belongs immediately after the model id. The runner executes trials and graders using the runtime
that invoked `instruction-test-runner`; from Codex it launches Codex-backed trial and grader runs.

<!-- cat:include ../../include/sprt-runner.md -->

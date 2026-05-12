<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Codex SPRT Runner

CAT's formal SPRT pipeline is not yet Codex-backed. The Java `instruction-test-runner` and grader pipeline still
depend on a non-Codex runner and stream format.

Do not present `cat:sprt-runner` results as Codex-runtime validation from Codex. For isolated Codex behavior checks,
use `cat:codex-runner` with explicit `--model` and `--effort`.

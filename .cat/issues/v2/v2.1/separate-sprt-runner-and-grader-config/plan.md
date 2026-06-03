<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan

## Goal

Separate SPRT test-runner model selection from fixed grader configuration, resolve Codex test-runner model/effort from owning agents or rules, and move adversarial red/blue JSON exchanges to committed artifacts plus Java-based state checks.

## Parent Requirements

None

## Pre-conditions

(none)

## Post-conditions

- [ ] SPRT test runs use the model/effort passed into `run-sprt`, while graders always use the fixed instruction-grader-agent configuration.
- [ ] Codex prompt-file owner resolution picks the weakest matching owner model/effort pair and falls back to the documented default only when no owner exists.
- [ ] Red-team and blue-team workflows write JSON artifacts to files, commit them, and return commit hashes; protocol/state checks use the Java CLI helper instead of `jq`.
- [ ] Codex defaults and docs remove unsupported `minimal` effort references and default test-runner configuration to `gpt-5.4-mini/low`.
- [ ] E2E: targeted SPRT and adversarial-state regression tests pass for the updated runner, resolver, and protocol behavior.

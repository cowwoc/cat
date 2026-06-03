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

## Type

bugfix

## Risk Assessment

- Risk Level: medium
- Regression Risk: SPRT routing, Codex owner resolution, and adversarial review protocol all cross runtime and
  documentation boundaries.
- Mitigation: keep runtime behavior covered with targeted CLI/unit tests and align prompt/protocol docs to the
  implemented behavior before merge.

## Files to Modify

- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/SprtRunner.java - separate runner and grader
  model/effort resolution, remove unsupported effort wording, and route grader config through fixed grader metadata.
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/SkillMetadataExtractor.java - resolve Codex
  owner-based model/effort pairs and rank the weakest matching owner configuration.
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/SharedSecrets.java - expose model/effort as a
  record rather than a string array tuple.
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/AdversarialState.java - provide Java CLI checks
  used by adversarial JSON artifact workflows.
- client/codex-cli/src/main/java/io/github/cowwoc/cat/codex/engine/CodexRunner.java - reject unsupported effort
  names and align default runner help/validation.
- client/distribution/scripts/build-jlink-images.sh - include the adversarial-state CLI in runtime images.
- client/plugin/agents/common/red-team-agent.md - write findings JSON to file, commit it, and return commit hash.
- client/plugin/agents/common/blue-team-agent.md - write validation JSON to file, commit it, and return commit hash.
- client/plugin/concepts/adversarial-protocol.md - document the file/commit-hash protocol and Java CLI checks.
- client/plugin/skills/include/instruction-builder.md - document Codex owner resolution and runner/grader separation.
- client/plugin/skills/include/sprt-runner.md - document passed runner config and fixed grader config behavior.
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/SprtRunnerTest.java - cover owner resolution,
  defaulting, and grader separation.
- client/codex-cli/src/test/java/io/github/cowwoc/cat/client/test/codex/CodexRunnerTest.java - cover effort
  validation and default runner behavior.
- client/common-cli/src/test/java/io/github/cowwoc/cat/common/test/AdversarialStateTest.java - cover Java CLI
  state extraction behavior.

## Pre-conditions

(none)

## Jobs

### Job 1

- Finalize runtime changes for SPRT runner and Codex owner-resolution behavior.
  - Files: client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/SprtRunner.java,
    client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/SkillMetadataExtractor.java,
    client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/SharedSecrets.java,
    client/codex-cli/src/main/java/io/github/cowwoc/cat/codex/engine/CodexRunner.java,
    client/codex-cli/src/main/java/io/github/cowwoc/cat/codex/engine/CodexRunnerSupport.java
- Land the adversarial JSON artifact protocol and runtime CLI support.
  - Files: client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/AdversarialState.java,
    client/distribution/scripts/build-jlink-images.sh,
    client/plugin/agents/common/red-team-agent.md,
    client/plugin/agents/common/blue-team-agent.md,
    client/plugin/concepts/adversarial-protocol.md
- Align documentation and regression coverage with the new behavior.
  - Files: client/plugin/agents/codex/README.md,
    client/plugin/skills/include/instruction-builder.md,
    client/plugin/skills/include/sprt-runner.md,
    client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/ClaudeSprtRunnerTest.java,
    client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/SprtRunnerTest.java,
    client/codex-cli/src/test/java/io/github/cowwoc/cat/client/test/codex/CodexRunnerTest.java,
    client/codex-cli/src/test/java/io/github/cowwoc/cat/client/test/codex/CodexSprtRunnerTest.java,
    client/common-cli/src/test/java/io/github/cowwoc/cat/common/test/AdversarialStateTest.java,
    client/common-cli/src/test/java/module-info.java,
    client/common-cli/pom.xml

## Post-conditions

- [ ] SPRT test runs use the model/effort passed into `run-sprt`, while graders always use the fixed instruction-grader-agent configuration.
- [ ] Codex prompt-file owner resolution picks the weakest matching owner model/effort pair and falls back to the documented default only when no owner exists.
- [ ] Red-team and blue-team workflows write JSON artifacts to files, commit them, and return commit hashes; protocol/state checks use the Java CLI helper instead of `jq`.
- [ ] Codex defaults and docs remove unsupported `minimal` effort references and default test-runner configuration to `gpt-5.4-mini/low`.
- [ ] E2E: targeted SPRT and adversarial-state regression tests pass for the updated runner, resolver, and protocol behavior.

# Plan: rename-subagent-terminology

## Goal
Update CAT terminology with runtime-specific wording:
- Claude runtime keeps `subagent` / `subagents`.
- Codex runtime uses `agent` / `agents` for delegated agents.
Preserve runtime protocol behavior and compatibility tokens.

## Current State
- `subagent` terminology appears in plugin docs, skill instructions, agent definitions, runtime descriptions, and test fixtures.
- Some `subagent` literals are protocol/API tokens (for example Claude hook event names and Codex SessionStart payload fields) and cannot be blindly renamed.
- Terminology is mixed: some files already use `agent`, others still use `subagent` for the same concept.

## Target State
- Claude-facing terminology continues to use `subagent`/`subagents`.
- Codex-facing terminology uses `agent`/`agents` for delegated agents.
- Protocol literals and compatibility-sensitive identifiers remain unchanged where required.
- Behavior and orchestration flow remain unchanged.

## Parent Requirements
- v2.1 objective: finalize user-facing terminology before demo recording.

## Approaches Considered

### A) Global literal replacement (`subagent` -> `agent`) across repository
- **Risk:** HIGH
- **Rejected because:** breaks protocol literals (`SubagentStart`, `thread_source=subagent`, `/source/subagent`) and likely causes runtime/test regressions.

### B) Minimal docs-only rename
- **Risk:** MEDIUM
- **Rejected because:** leaves agent-facing skill/agent metadata and output strings inconsistent with docs.

### C) Runtime-aware rename with explicit protocol exceptions (chosen)
- **Risk:** MEDIUM
- **Why chosen:** satisfies terminology objective while preserving behavior by separating Claude wording from Codex wording and preserving protocol tokens.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Primary Risks:** accidental Claude wording drift (`subagent` -> `agent` where not intended); accidental protocol-token edits; missed references in large docs/test surface.
- **Mitigation:** runtime-scoped edit rules (Claude keep, Codex rename), explicit keep-literal guardrails, repo-wide grep verification by runtime path, full Maven verify run.

## Research Findings
- Repository scan (`rg -l "subagent|sub-agent|Subagent|Sub-agent"`) found **191 files** with matches.
- Matches cluster into: plugin concepts/skills/rules/tests, agent definition metadata, CLI Java comments/messages, and test fixtures.
- High-risk protocol-literal areas include:
  - `client/plugin/hooks/claude/hooks.json` (`SubagentStart` hook name, `subagent-start` command path)
  - `client/codex-cli/src/main/java/io/github/cowwoc/cat/codex/hook/CodexSessionRules.java` (`thread_source`, `/source/subagent` payload detection)
  - Java class/type names and file paths containing `Subagent` that are implementation identifiers rather than user text.
- Runtime terminology split required by this issue:
  - Claude-specific files/paths (`client/claude-cli/**`, `client/plugin/agents/claude/**`, `client/plugin/skills/claude/**`) keep `subagent` wording in human-facing text.
  - Codex-specific files/paths (`client/codex-cli/**`, `client/plugin/agents/codex/**`, `client/plugin/skills/codex/**`, Codex-only docs) rename delegated-actor wording to `agent`.

## Files to Modify
Implementation must review and update terminology context in every file below, applying the rename only where text describes delegated agents (not protocol literals).

- README.md
- changelog.md
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/AbstractClaudeHook.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/AotTraining.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/EnforceStatusOutput.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/PreIssueHook.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/SubagentStartHook.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/TaskHandler.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockUnauthorizedMergeCleanup.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWrongBranchCommit.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/session/InjectSubAgentRules.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/session/SubagentStartHandler.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/task/EnforceApprovalBeforeMerge.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/task/EnforceCommitBeforeSubagentSpawn.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/task/EnforceWorktreeSafetyBeforeMerge.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/hook/util/SkillDiscovery.java
- client/claude-cli/src/main/java/io/github/cowwoc/cat/claude/tool/post/AutoLearnMistakes.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/AutoLearnMistakesTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/BlockWrongBranchCommitTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/ClaudeRunnerTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/CodexSessionStartHookTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/DescriptionOptimizerTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/EnforceCommitBeforeSubagentSpawnTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/GetOutputTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/GetTokenReportOutputTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/InjectMainAgentRulesTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/InjectSubAgentRulesTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/IssueLockTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/ParallelSubagentTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/RecordLearningTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/RulesDiscoveryTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/SessionAnalyzerTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/SubagentStartHookMainTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/SubagentStartHookTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/TestUtils.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/WorkPrepareTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/claude/EnforceApprovalBeforeMergeTest.java
- client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/common/PluginArtifactBuilderTest.java
- client/codex-cli/src/main/java/io/github/cowwoc/cat/codex/hook/CodexSessionRules.java
- client/codex-cli/src/main/java/io/github/cowwoc/cat/codex/hook/SessionStartHook.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/agent/CheckDataMigration.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/agent/RulesDiscovery.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/ClaudeRunner.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/DescriptionOptimizer.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/DescriptionTester.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/EmpiricalTestRunner.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/GetCheckpointOutput.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/GetTokenReportOutput.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/GetWorkOutput.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/skills/SkillMetadataExtractor.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/util/AgentIdPatterns.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/util/InvestigationContextExtractor.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/util/IssueLock.java
- client/common-cli/src/main/java/io/github/cowwoc/cat/tool/util/SessionAnalyzer.java
- client/distribution/scripts/build-jlink-images.sh
- client/plugin/agents/claude/README.md
- client/plugin/agents/claude/blue-team-agent.md
- client/plugin/agents/claude/diff-validation-agent.md
- client/plugin/agents/claude/instruction-analyzer-agent.md
- client/plugin/agents/claude/instruction-grader-agent.md
- client/plugin/agents/claude/red-team-agent.md
- client/plugin/agents/codex/README.md
- client/plugin/agents/codex/blue-team-agent.toml
- client/plugin/agents/codex/diff-validation-agent.toml
- client/plugin/agents/codex/instruction-analyzer-agent.toml
- client/plugin/agents/codex/instruction-grader-agent.toml
- client/plugin/agents/codex/red-team-agent.toml
- client/plugin/agents/common/README.md
- client/plugin/agents/common/instruction-analyzer-agent.md
- client/plugin/agents/common/learn-agent.md
- client/plugin/agents/common/plan-review-agent.md
- client/plugin/agents/common/stakeholder-architecture.md
- client/plugin/agents/common/stakeholder-business.md
- client/plugin/agents/common/stakeholder-deployment.md
- client/plugin/agents/common/stakeholder-design.md
- client/plugin/agents/common/stakeholder-legal.md
- client/plugin/agents/common/stakeholder-performance.md
- client/plugin/agents/common/stakeholder-requirements.md
- client/plugin/agents/common/stakeholder-security.md
- client/plugin/agents/common/stakeholder-testing.md
- client/plugin/agents/common/stakeholder-ux.md
- client/plugin/concepts/adversarial-protocol.md
- client/plugin/concepts/agent-architecture.md
- client/plugin/concepts/doc-consolidation.md
- client/plugin/concepts/error-handling.md
- client/plugin/concepts/hierarchy.md
- client/plugin/concepts/instruction-test-design.md
- client/plugin/concepts/instruction-testing.md
- client/plugin/concepts/merge-and-cleanup.md
- client/plugin/concepts/parallel-execution.md
- client/plugin/concepts/rules-audience.md
- client/plugin/concepts/subagent-context-minimization.md
- client/plugin/concepts/subagent-delegation.md
- client/plugin/concepts/token-warning.md
- client/plugin/concepts/work-decomposition.md
- client/plugin/concepts/work.md
- client/plugin/config/tiers.json
- client/plugin/hooks/claude/hooks.json
- client/plugin/hooks/common/README.md
- client/plugin/rules/common/implementation-delegation.md
- client/plugin/rules/common/rename-convention.md
- client/plugin/rules/common/tool-usage-efficiency.md
- client/plugin/rules/common/worktree-isolation.md
- client/plugin/skills/claude/claude-runner/first-use.md
- client/plugin/skills/claude/collect-results/SKILL.md
- client/plugin/skills/claude/get-history/first-use.md
- client/plugin/skills/claude/instruction-builder/first-use.md
- client/plugin/skills/claude/learn/phase-investigate-subagent-mistake.md
- client/plugin/skills/claude/plan-builder/SKILL.md
- client/plugin/skills/claude/sprt-runner/SKILL.md
- client/plugin/skills/claude/stakeholder-review/first-use.md
- client/plugin/skills/claude/token-report/SKILL.md
- client/plugin/skills/claude/work-implement/SKILL.md
- client/plugin/skills/codex/codex-runner/SKILL.md
- client/plugin/skills/codex/codex-runner/first-use.md
- client/plugin/skills/codex/collect-results/SKILL.md
- client/plugin/skills/codex/get-history/first-use.md
- client/plugin/skills/codex/plan-builder/SKILL.md
- client/plugin/skills/codex/token-report/SKILL.md
- client/plugin/skills/codex/work-implement/SKILL.md
- client/plugin/skills/common/add/first-use.md
- client/plugin/skills/common/collect-results/first-use.md
- client/plugin/skills/common/decompose-issue/first-use.md
- client/plugin/skills/common/git-squash/first-use.md
- client/plugin/skills/common/instruction-builder/compression-protocol.md
- client/plugin/skills/common/instruction-builder/e2e-dispute-trace.md
- client/plugin/skills/common/instruction-builder/skill-conventions.md
- client/plugin/skills/common/instruction-builder/testing.md
- client/plugin/skills/common/instruction-builder/workflow-output.md
- client/plugin/skills/common/learn/documentation-priming.md
- client/plugin/skills/common/learn/first-use.md
- client/plugin/skills/common/learn/mistake-categories.md
- client/plugin/skills/common/learn/phase-analyze.md
- client/plugin/skills/common/learn/phase-investigate-subagent-mistake.md
- client/plugin/skills/common/learn/phase-investigate.md
- client/plugin/skills/common/learn/phase-prevent.md
- client/plugin/skills/common/learn/rca-method.md
- client/plugin/skills/common/optimize-execution/delegation-analysis.md
- client/plugin/skills/common/optimize-execution/first-use.md
- client/plugin/skills/common/plan-builder/first-use.md
- client/plugin/skills/common/recover-from-drift/first-use.md
- client/plugin/skills/common/research/first-use.md
- client/plugin/skills/common/tdd-implementation/first-use.md
- client/plugin/skills/common/verify-implementation/first-use.md
- client/plugin/skills/common/work-confirm/first-use.md
- client/plugin/skills/common/work-implement/first-use.md
- client/plugin/skills/common/work-merge/first-use.md
- client/plugin/skills/common/work-prepare/first-use.md
- client/plugin/skills/common/work-review/first-use.md
- client/plugin/skills/common/work-with-issue/first-use.md
- client/plugin/skills/common/work/first-use.md
- client/plugin/skills/include/instruction-builder.md
- client/plugin/skills/include/sprt-runner.md
- client/plugin/skills/include/stakeholder-review.md
- client/plugin/templates/issue-plan.md
- client/plugin/tests/scripts/jobs-count-helper.bash
- client/plugin/tests/scripts/token-efficiency-fixtures/detection-gap-analysis.md
- client/plugin/tests/scripts/work-implement-agent-jobs-count.bats
- client/plugin/tests/skills/claude-runner/first-use/nested_subagent_spawn.md
- client/plugin/tests/skills/instruction-builder/compression-protocol/prohibition-why-condensing-to-zero.md
- client/plugin/tests/skills/instruction-builder/compression-protocol/prohibition-why-stripping-direct.md
- client/plugin/tests/skills/instruction-builder/first-use/step4-writes-via-implementation-subagent.md
- client/plugin/tests/skills/instruction-builder/first-use/step44-contradictory-evidence-concludes-inconclusive.md
- client/plugin/tests/skills/instruction-builder/first-use/step44-get-history-invoked-with-correct-args.md
- client/plugin/tests/skills/instruction-builder/first-use/step44-investigation-invokes-get-history.md
- client/plugin/tests/skills/instruction-builder/first-use/step44-no-contamination-concludes-genuine-defect.md
- client/plugin/tests/skills/instruction-builder/first-use/step44-reject-decision-starts-investigation.md
- client/plugin/tests/skills/instruction-builder/first-use/step44-shared-subagent-detects-contamination.md
- client/plugin/tests/skills/instruction-builder/first-use/step44-thinking-block-recorded-in-report.md
- client/plugin/tests/skills/instruction-builder/first-use/step6-creates-orphan-branch.md
- client/plugin/tests/skills/instruction-builder/first-use/step6-orphan-branch-strips-assertions.md
- client/plugin/tests/skills/instruction-builder/first-use/step6-subagent-prompt-no-assertion-leakage.md
- client/plugin/tests/skills/instruction-builder/first-use/wave-slots-nproc-cap.md
- client/plugin/tests/skills/learn/phase-prevent/escalation-doc-violated-workaround.md
- client/plugin/tests/skills/sprt-runner/first-use/verify-parallel-grading.md
- client/plugin/tests/skills/stakeholder-review/first-use/codex-v1-isolated-spawn.md
- client/plugin/tests/skills/stakeholder-review/first-use/codex-v2-isolated-spawn.md
- client/plugin/tests/skills/stakeholder-review/first-use/no-sequential-spawn.md
- client/plugin/tests/skills/stakeholder-review/first-use/results.json
- client/plugin/tests/skills/stakeholder-review/first-use/subagent-restriction.md
- client/plugin/tests/skills/stakeholder-review/first-use/verify-none-skip.md
- client/plugin/tests/skills/work-implement/first-use/ascending-order-reproducible-metrics.md
- client/plugin/tests/skills/work-implement/first-use/branch-merge-ascending-order.md
- client/plugin/tests/skills/work-implement/first-use/branch-name-validation-three-checks.md
- client/plugin/tests/skills/work-implement/first-use/single-job-collect-immediately.md
- client/plugin/tests/skills/work-implement/first-use/subagent-id-format-with-subagents-segment.md
- client/plugin/tests/skills/work/first-use/skip-non-stale-locked-issue.md
- docs/development/codex-parity.md
- tests/eval/EVALUATION_REPORT.md
- tests/eval/skill_inventory.json
- tests/eval/test_cases.json

## Terminology Rules (Apply During Edits)
- Apply these replacements for all entries marked `rename` in `rename-hits.txt`:
  - `subagent` -> `agent`
  - `subagents` -> `agents`
  - `Subagent` -> `Agent`
  - `Subagents` -> `Agents`
  - `sub-agent` -> `agent`
  - `sub-agents` -> `agents`
  - `Sub-agent` -> `Agent`
  - `Sub-agents` -> `Agents`
  - `sub-subagent` -> `nested agent`
  - `sub-subagents` -> `nested agents`
  - `Sub-subagent` -> `Nested agent`
  - `Sub-subagents` -> `Nested agents`
- Preserve protocol/API literals exactly when they are part of external contracts, including:
  - Claude hook event name `SubagentStart`
  - Command/binary names tied to hook wiring (for example `subagent-start`)
  - Codex SessionStart payload keys/values: `thread_source`, `"subagent"`, `/source/subagent`
- Do not rename issue archive records under `.cat/issues/**` (except this active issue plan revision already done).
- Do not rename files/classes/methods solely for wording polish in this issue; limit code changes to comments, descriptions, and user-visible strings unless a test requires an identifier-aligned adjustment.

## Pre-conditions
- [ ] Confirm working tree is on issue branch `v2.1/rename-subagent-terminology`.
- [ ] Confirm rename scope excludes closed issue archives and migration-unrelated historical records.
- [ ] Confirm verification artifacts under `.cat/tmp/` are transient and removed before runtime E2E and commit.

## Jobs

### Job 1
- Execute all steps below sequentially (this job is intentionally single-threaded because later steps depend on transient `.cat/tmp/rename-subagent-terminology/*` artifacts from earlier steps and touch overlapping files).
- Create `.cat/tmp/rename-subagent-terminology/` and extract the authoritative inventory from this plan:
  - `awk '/^## Files to Modify/{flag=1;next}/^## Terminology Rules/{flag=0}flag&&/^- /{print substr($0,3)}' .cat/issues/v2/v2.1/rename-subagent-terminology/plan.md > .cat/tmp/rename-subagent-terminology/all-files.txt`
- Create pre-edit match inventory:
  - `xargs -a .cat/tmp/rename-subagent-terminology/all-files.txt rg -n "subagent|sub-agent|Subagent|Sub-agent" > .cat/tmp/rename-subagent-terminology/before-hits.txt`
- Create pre-edit per-file line counts:
  - `xargs -a .cat/tmp/rename-subagent-terminology/all-files.txt -I{} sh -c 'wc -l \"$1\"' _ {} | sort > .cat/tmp/rename-subagent-terminology/before-line-counts.txt`
- Classify each match line from `before-hits.txt` using this exact command:
  - `awk 'BEGIN{rename_re=\"(^|[^A-Za-z0-9_])(subagent|subagents|Subagent|Subagents|sub-agent|sub-agents|Sub-agent|Sub-agents|sub-subagent|sub-subagents|Sub-subagent|Sub-subagents)([^A-Za-z0-9_]|$)\"; keep_re=\"SubagentStart|subagent-start|thread_source|/source/subagent|\\\"subagent\\\"\"} {text=$0; sub(/^[^:]+:[0-9]+:/,\"\",text); if (text ~ rename_re && text !~ keep_re) print $0 > \".cat/tmp/rename-subagent-terminology/rename-hits.txt\"; else print $0 > \".cat/tmp/rename-subagent-terminology/keep-literal-hits.txt\"}' .cat/tmp/rename-subagent-terminology/before-hits.txt`
- Write classification outputs and compatibility ledger:
  - `.cat/tmp/rename-subagent-terminology/rename-hits.txt`
  - `.cat/tmp/rename-subagent-terminology/keep-literal-hits.txt`
  - `awk -F: '{path=$1; line=$2; text=substr($0,index($0,$3)); print path \":\" line \" | \" text \" | protocol-or-identifier-literal\"}' .cat/tmp/rename-subagent-terminology/keep-literal-hits.txt > .cat/tmp/rename-subagent-terminology/keep-literals.md`
- Build exact file lists from the authoritative inventory:
  - `grep -E '^(README\\.md|changelog\\.md|docs/development/codex-parity\\.md|client/plugin/)' .cat/tmp/rename-subagent-terminology/all-files.txt > .cat/tmp/rename-subagent-terminology/job2-files.txt`
  - `grep -E '^(client/claude-cli/src/main/java/|client/codex-cli/src/main/java/|client/common-cli/src/main/java/|client/claude-cli/src/test/java/)' .cat/tmp/rename-subagent-terminology/all-files.txt > .cat/tmp/rename-subagent-terminology/job3-files.txt`
  - `grep -E '^(client/plugin/tests/|tests/eval/|client/distribution/scripts/build-jlink-images\\.sh$)' .cat/tmp/rename-subagent-terminology/all-files.txt > .cat/tmp/rename-subagent-terminology/job4-files.txt`
- Apply mapped literal replacements to every file listed in `job2-files.txt`, `job3-files.txt`, then `job4-files.txt` with this exact command sequence:
  - `xargs -a .cat/tmp/rename-subagent-terminology/job2-files.txt perl -0pi -e 's/\\bsub-subagents\\b/nested agents/g; s/\\bsub-subagent\\b/nested agent/g; s/\\bSub-subagents\\b/Nested agents/g; s/\\bSub-subagent\\b/Nested agent/g; s/\\bsub-agents\\b/agents/g; s/\\bsub-agent\\b/agent/g; s/\\bSub-agents\\b/Agents/g; s/\\bSub-agent\\b/Agent/g; s/\\bsubagents\\b/agents/g; s/\\bsubagent\\b/agent/g; s/\\bSubagents\\b/Agents/g; s/\\bSubagent\\b/Agent/g;'`
  - `xargs -a .cat/tmp/rename-subagent-terminology/job3-files.txt perl -0pi -e 's/\\bsub-subagents\\b/nested agents/g; s/\\bsub-subagent\\b/nested agent/g; s/\\bSub-subagents\\b/Nested agents/g; s/\\bSub-subagent\\b/Nested agent/g; s/\\bsub-agents\\b/agents/g; s/\\bsub-agent\\b/agent/g; s/\\bSub-agents\\b/Agents/g; s/\\bSub-agent\\b/Agent/g; s/\\bsubagents\\b/agents/g; s/\\bsubagent\\b/agent/g; s/\\bSubagents\\b/Agents/g; s/\\bSubagent\\b/Agent/g;'`
  - `xargs -a .cat/tmp/rename-subagent-terminology/job4-files.txt perl -0pi -e 's/\\bsub-subagents\\b/nested agents/g; s/\\bsub-subagent\\b/nested agent/g; s/\\bSub-subagents\\b/Nested agents/g; s/\\bSub-subagent\\b/Nested agent/g; s/\\bsub-agents\\b/agents/g; s/\\bsub-agent\\b/agent/g; s/\\bSub-agents\\b/Agents/g; s/\\bSub-agent\\b/Agent/g; s/\\bsubagents\\b/agents/g; s/\\bsubagent\\b/agent/g; s/\\bSubagents\\b/Agents/g; s/\\bSubagent\\b/Agent/g;'`
- Keep protocol-contract literals unchanged, including:
  - `SubagentStart`
  - `subagent-start`
  - `thread_source`
  - `"subagent"` as payload value
  - `/source/subagent`
- Restore every `keep-literal` line from `.cat/tmp/rename-subagent-terminology/keep-literal-hits.txt` with this exact command sequence before verification:
  - `while IFS= read -r entry; do path=${entry%%:*}; rest=${entry#*:}; line=${rest%%:*}; text=${rest#*:}; tmp=$(mktemp); awk -v n=\"$line\" -v repl=\"$text\" 'NR==n{$0=repl} {print}' \"$path\" > \"$tmp\" && mv \"$tmp\" \"$path\"; done < .cat/tmp/rename-subagent-terminology/keep-literal-hits.txt`
- Generate post-edit matches from authoritative file inventory:
  - `xargs -a .cat/tmp/rename-subagent-terminology/all-files.txt rg -n "subagent|sub-agent|Subagent|Sub-agent" > .cat/tmp/rename-subagent-terminology/after-hits.txt || true`
- Build normalized location sets:
  - `awk -F: '{print $1\":\"$2}' .cat/tmp/rename-subagent-terminology/after-hits.txt | sort -u > .cat/tmp/rename-subagent-terminology/after-hit-locations.txt`
  - `cut -d'|' -f1 .cat/tmp/rename-subagent-terminology/keep-literals.md | tr -d ' ' | sort -u > .cat/tmp/rename-subagent-terminology/keep-literal-locations.txt`
  - `cut -d'|' -f1 .cat/tmp/rename-subagent-terminology/rename-hits.txt | tr -d ' ' | sort -u > .cat/tmp/rename-subagent-terminology/rename-hit-locations.txt`
- Build post-edit per-file line counts and compare:
  - `xargs -a .cat/tmp/rename-subagent-terminology/all-files.txt -I{} sh -c 'wc -l \"$1\"' _ {} | sort > .cat/tmp/rename-subagent-terminology/after-line-counts.txt`
  - `diff -u .cat/tmp/rename-subagent-terminology/before-line-counts.txt .cat/tmp/rename-subagent-terminology/after-line-counts.txt > .cat/tmp/rename-subagent-terminology/line-count-drift.diff`
- Detect unresolved remaining hits:
  - `comm -23 .cat/tmp/rename-subagent-terminology/after-hit-locations.txt .cat/tmp/rename-subagent-terminology/keep-literal-locations.txt > .cat/tmp/rename-subagent-terminology/unresolved-hits.txt`
  - `comm -12 .cat/tmp/rename-subagent-terminology/after-hit-locations.txt .cat/tmp/rename-subagent-terminology/rename-hit-locations.txt > .cat/tmp/rename-subagent-terminology/unapplied-renames.txt`
- Verify changed-file scope is restricted to authoritative inventory plus active issue bookkeeping:
  - `git diff --name-only | sort -u > .cat/tmp/rename-subagent-terminology/changed-files.txt`
  - `{ cat .cat/tmp/rename-subagent-terminology/all-files.txt; echo .cat/issues/v2/v2.1/rename-subagent-terminology/plan.md; echo .cat/issues/v2/v2.1/rename-subagent-terminology/index.json; } | sort -u > .cat/tmp/rename-subagent-terminology/allowed-files.txt`
  - `comm -23 .cat/tmp/rename-subagent-terminology/changed-files.txt .cat/tmp/rename-subagent-terminology/allowed-files.txt > .cat/tmp/rename-subagent-terminology/out-of-scope-files.txt`
- Verify no historical closed issue records were modified:
  - `grep -E '^\\.cat/issues/' .cat/tmp/rename-subagent-terminology/changed-files.txt | grep -Ev '^\\.cat/issues/v2/v2\\.1/rename-subagent-terminology/(plan\\.md|index\\.json)$' > .cat/tmp/rename-subagent-terminology/changed-issue-records.txt || true`
- Remove transient verification artifacts before runtime E2E and before commit:
  - `test ! -s .cat/tmp/rename-subagent-terminology/unresolved-hits.txt`
  - `test ! -s .cat/tmp/rename-subagent-terminology/unapplied-renames.txt`
  - `test ! -s .cat/tmp/rename-subagent-terminology/out-of-scope-files.txt`
  - `test ! -s .cat/tmp/rename-subagent-terminology/changed-issue-records.txt`
  - `rm -rf .cat/tmp/rename-subagent-terminology`
  - `test -z "$(git status --short -- .cat/tmp | grep 'rename-subagent-terminology' || true)"`
- Run full test suite:
  - `mvn -f client/pom.xml verify -e`
- Finalize issue bookkeeping in this same job by updating `.cat/issues/v2/v2.1/rename-subagent-terminology/index.json` only after all post-conditions pass.

## Post-conditions
- [ ] Source-derived verification yields zero unresolved terminology hits and zero unapplied rename locations; all remaining hits are documented protocol/compatibility literals.
- [ ] Historical closed issue records are not modified unless explicitly approved.
- [ ] `mvn -f client/pom.xml verify -e` exits with status code 0.
- [ ] `git diff --name-only` contains only files from the authoritative inventory plus `.cat/issues/v2/v2.1/rename-subagent-terminology/{plan.md,index.json}`.
- [ ] Runtime E2E and commit are performed from a clean worktree with no transient `.cat/tmp/rename-subagent-terminology*` artifacts.

## Impact Notes
- This issue overlaps conceptually with `refactor-phase-skill-subagent-isolation`; execute in a way that avoids parallel edits to the same files.

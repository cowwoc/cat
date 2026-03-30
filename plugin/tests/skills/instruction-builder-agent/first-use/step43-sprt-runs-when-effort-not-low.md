---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

We have a rule file at `plugin/rules/tee-piped-output.md` that instructs agents to use `tee` when running
piped Bash commands. I want to make sure agents actually follow this rule in practice — not just that the
rule text exists. Please review and improve the rule using instruction-builder, and verify agent compliance
empirically.

## Assertions

1. response must spawn test-run subagents to empirically verify agent compliance (via Task tool invocations)
2. response must NOT skip the compliance testing and instead only write test files manually
3. response must produce fresh SPRT results in the current session (not reference pre-existing
   test-results.json from a prior run); the results must be written or computed during this response
   and must show acceptance or rejection with log_ratio values
4. response must use instruction-test-runner extract-model to determine which model to use for test runs
5. response must invoke get-config-output effective (the CLI tool) to read the effective config and confirm
   curiosity is not "low" before proceeding to SPRT
6. response must spawn at least 3 independent test-run subagents (each as a fresh Task invocation without
   resume:true or conversation_id reuse), and each subagent's prompt must contain test scenarios with
   assertions; each subagent must return pass/fail results that feed into SPRT log_ratio computation
7. test scenarios used in SPRT runs must involve piped Bash commands, and each scenario's assertions must
   explicitly check that the agent's Bash command includes tee (not generic unrelated assertions like
   "response must produce output")
8. response must invoke the instruction-builder skill and produce rule analysis or improvement output
   (e.g., semantic-unit decomposition, backward-chain design, or updated rule text); the Skill tool
   invocation for instruction-builder and its output must appear in the response before any Task tool
   invocation that spawns a test-run subagent
9. each Task tool invocation that spawns a test-run subagent must specify a model parameter whose value
   matches the model returned by the instruction-test-runner extract-model invocation from assertion 4
10. the SPRT decision outcome must be Accept (not Reject or Inconclusive); if the outcome is Reject or
    Inconclusive, the response must iterate on the rule (via instruction-builder) and re-run SPRT; the
    response must show at least two iteration cycles (each cycle = one instruction-builder revision +
    one SPRT re-run) before an explanation for why acceptance cannot be reached is acceptable; a single
    SPRT run followed immediately by an explanation does not satisfy this assertion
11. each test-run subagent's prompt must include at least 3 distinct test scenarios (not just one); the
    scenarios must be distinguishable (different piped commands or different tee usage patterns) to
    provide sufficient statistical power for the SPRT computation

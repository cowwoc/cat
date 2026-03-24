<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan

## Type

config

## Goal

Update `plugin/concepts/adversarial-protocol.md` so that the final-round MEDIUM/LOW cleanup pass
runs arbitration when the blue-team returns `has_new_disputes: true`, instead of always skipping
arbitration for MEDIUM/LOW findings.

## Pre-conditions

(none)

## Post-conditions

- [ ] In `plugin/concepts/adversarial-protocol.md`, the "Final-round MEDIUM/LOW cleanup" section
  no longer says "Skip arbitration and diff-validation for this cleanup pass"
- [ ] The updated section requires: when blue-team returns `has_new_disputes: true`, run the
  arbitration agent before exiting the loop (same arbitration flow as during main TDD rounds)
- [ ] The updated section still skips diff-validation for MEDIUM/LOW (only arbitration is added back)
- [ ] The instruction-builder-agent's `first-use.md` is updated to match the new protocol if it
  contains a copy of the MEDIUM/LOW cleanup instructions

## Sub-Agent Waves

### Wave 1

- Read `plugin/concepts/adversarial-protocol.md` and locate the "Final-round MEDIUM/LOW cleanup"
  section. Update it so that after blue-team patches MEDIUM/LOW findings, the orchestrator checks
  `has_new_disputes`; if true, runs the arbitration agent (same as main-loop arbitration) before
  exiting; diff-validation is still skipped for MEDIUM/LOW cleanup
- Check `plugin/skills/instruction-builder-agent/first-use.md` for any copy of the MEDIUM/LOW
  cleanup protocol. If present, apply the same arbitration change there

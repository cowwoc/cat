# Plan

## Goal

Fix instruction-builder-agent effort gate to read curiosity instead of effort

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: instruction-builder-agent reads `curiosity` config key (not `effort`) for its effort gate
- [ ] Regression test added: test verifies effort gate reads `curiosity`
- [ ] No new issues introduced
- [ ] E2E verification: run instruction-builder-agent and confirm it proceeds with full workflow when `curiosity` is set to non-low value

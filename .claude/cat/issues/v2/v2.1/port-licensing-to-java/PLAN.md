# Plan: port-licensing-to-java

## Goal
Port licensing and feature gating scripts to Java classes in the hooks module.

## Current State
Three bash/Python scripts handle license validation and feature gating via subprocess chaining (~481ms latency).

## Target State
Unified Java licensing package with no subprocess overhead.

## Satisfies
Parent: port-utility-scripts

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None if Java output matches script output
- **Mitigation:** Verify JSON output parity

## Scripts to Port
- `feature-gate.sh` (57 lines) - Orchestrates license validation + entitlements check
- `entitlements.sh` (90 lines) - Maps tiers to features from tiers.json config
- `validate-license.py` (215 lines) - JWT Ed25519 license token validation

## Files to Create
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/licensing/Entitlements.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/licensing/FeatureGate.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/licensing/LicenseValidator.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/licensing/LicenseResult.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/EntitlementsTest.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/FeatureGateTest.java`

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` - Export licensing package

## Execution Steps
1. Read all three scripts to understand inputs, outputs, and logic
2. Create `LicenseResult` record for validation output
3. Create `LicenseValidator` class porting validate-license.py logic (Ed25519 via java.security)
4. Create `Entitlements` class porting entitlements.sh logic (tier inheritance from tiers.json)
5. Create `FeatureGate` class orchestrating validator + entitlements
6. Update module-info.java to export licensing package
7. Write tests for each class
8. Run `mvn verify` to confirm all tests pass

## Success Criteria
- [ ] All three licensing scripts have Java equivalents
- [ ] feature-gate latency eliminated (no subprocess chaining)
- [ ] JSON output matches original scripts
- [ ] All tests pass (`mvn verify`)

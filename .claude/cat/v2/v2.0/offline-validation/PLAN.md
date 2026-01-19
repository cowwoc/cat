# Task Plan: offline-validation

## Objective
Implement offline validation with grace periods using Java validation library.

## Tasks
- [ ] Create Java CLI tool for local token validation
- [ ] Cache signed license token locally (file-based)
- [ ] Implement local signature verification using public key
- [ ] Add grace period logic (e.g., 7 days past expiry)
- [ ] Handle degraded mode when offline and expired
- [ ] Create Bash hook wrapper to invoke Java validator
- [ ] Implement periodic sync service for online refresh

## Technical Approach

Per project conventions, cryptographic validation must be in Java.

**Java Components:**
- `OfflineValidator.java` - CLI entry point for validation
- `TokenCache.java` - Local token storage management
- `GracePeriodCalculator.java` - Expiry and grace logic
- Reuses `LicenseTokenValidator.java` from jwt-token-generation

**Validation Flow:**
1. Bash hook invokes: `java -jar cat-license-validator.jar validate`
2. Java reads cached token from `~/.cat/license-token.json`
3. Validates signature with embedded public key
4. Checks expiry and grace period
5. Returns JSON: `{"valid": true, "tier": "team", "daysRemaining": 14}`

**Client Integration:**
- Bash hook at SessionStart invokes Java validator
- Exit code 0 = valid, 1 = expired, 2 = invalid
- Hook parses JSON output for tier context

## Verification
- [ ] Valid token works offline indefinitely (until expiry)
- [ ] Expired token works during grace period (warning shown)
- [ ] After grace period, degrades to free tier
- [ ] Online sync refreshes local cache
- [ ] Bash hook correctly interprets Java validator output

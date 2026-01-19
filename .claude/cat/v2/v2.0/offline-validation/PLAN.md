# Task Plan: offline-validation

## Objective
Implement offline validation with grace periods for air-gapped/disconnected usage.

## Tasks
- [ ] Cache signed license token locally
- [ ] Implement local signature verification (public key only)
- [ ] Add grace period logic (e.g., 7 days past expiry)
- [ ] Handle degraded mode when offline and expired
- [ ] Implement periodic sync daemon for online refresh

## Technical Approach
Per security research: Ed25519 signed tokens with embedded expiry. Client verifies signature using public key without server contact. Grace period bounds window for disconnected usage.

## Verification
- [ ] Valid token works offline indefinitely (until expiry)
- [ ] Expired token works during grace period
- [ ] After grace period, degrades to free tier
- [ ] Online sync refreshes local cache
